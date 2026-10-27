package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Moves {@code NoiseChunk.FlatCache}'s sampling lattice into the long domain.
 *
 * <p>The overworld's {@code finalDensity} contains a {@code flatCache} node (the
 * jaggedness term). Vanilla builds it from {@code firstNoiseX + i} ints and
 * samples it through a {@code SinglePointContext} built from
 * {@code QuartPos.toBlock(int)}; at block 2^31 both wrap, so the right-hand
 * chunk caches terrain sampled on the <em>mirrored</em> (negative) side of the
 * seam and the whole jaggedness term jumps by several blocks. That is the
 * remaining 8-block solid-terrain step measured by {@code [SEAM]
 * maxFloorDelta}.</p>
 *
 * <p>Fix: the fill loop derives its quart base from the wide
 * {@code NoiseChunk.farlands$firstNoiseX/ZLong} cells (published by
 * {@link NoiseChunkPatch} from the true chunk origin) and samples through
 * {@code RealContext} with exact long block coordinates; {@code compute} maps
 * the caller's real block coordinate to the cache cell with long quart
 * arithmetic. At normal coordinates every value collapses to the vanilla int
 * result (origin is small, block coords identical), so generation is
 * bit-identical there.</p>
 *
 * <p>The fill is deferred to the first {@code compute}: the vanilla constructor
 * runs before the wide origin is assigned (the wide constructor's field stores
 * happen after the delegating {@code this(...)} call), so filling in the
 * constructor would read origin 0.</p>
 */
public final class NoiseChunkFlatCacheLongPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/levelgen/NoiseChunk$FlatCache";
    private static final String OUTER = "net/minecraft/world/level/levelgen/NoiseChunk";
    private static final String CTOR_DESC =
        "(Lnet/minecraft/world/level/levelgen/NoiseChunk;Lnet/minecraft/world/level/levelgen/DensityFunction;Z)V";
    private static final String COMPUTE_DESC =
        "(Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;)D";
    private static final String CONTEXT =
        "net/minecraft/world/level/levelgen/DensityFunction$FunctionContext";
    private static final String REAL_CONTEXT = "com/farlands/g2/runtime/RealContext";
    private static final String DIAG = "com/farlands/g2/runtime/FarFlatCacheDiag";
    private static final String PENDING = "farlands$pendingFill";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        if (has(node, "farlands$fillNow", "()V")) {
            return original;
        }
        for (MethodNode m : node.methods) {
            if (!"<init>".equals(m.name) || !CTOR_DESC.equals(m.desc)) {
                continue;
            }
            if (!deferFill(m)) {
                return original;
            }
        }

        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, PENDING, "Z", null, null));
        node.methods.add(fillNow());
        if (!rewriteCompute(node)) {
            throw new IllegalStateException(TARGET + "#compute not found");
        }

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    /** Replaces the constructor's eager int fill with {@code pendingFill = fill}. */
    private static boolean deferFill(MethodNode ctor) {
        AbstractInsnNode putValues = null;
        for (AbstractInsnNode insn : ctor.instructions) {
            if (insn instanceof FieldInsnNode fi && fi.getOpcode() == Opcodes.PUTFIELD
                && "values".equals(fi.name)) {
                putValues = insn;
            }
        }
        if (putValues == null) {
            return false;
        }
        AbstractInsnNode next = putValues.getNext();
        while (next != null) {
            AbstractInsnNode after = next.getNext();
            ctor.instructions.remove(next);
            next = after;
        }
        InsnList tail = new InsnList();
        tail.add(new VarInsnNode(Opcodes.ALOAD, 0));
        tail.add(new VarInsnNode(Opcodes.ILOAD, 3));
        tail.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, PENDING, "Z"));
        tail.add(new InsnNode(Opcodes.RETURN));
        ctor.instructions.add(tail);
        ctor.localVariables = null;
        ctor.tryCatchBlocks.clear();
        return true;
    }

    /**
     * private void farlands$fillNow(): sample the filler on the true block grid.
     *
     * <pre>
     * if (!pendingFill) return;
     * pendingFill = false;
     * long baseQuartX = QuartPos.fromBlock(originX);
     * long baseQuartZ = QuartPos.fromBlock(originZ);
     * for (int x = 0; x &lt;= noiseSizeXZ; x++) {
     *    long blockX = QuartPos.toBlock(baseQuartX + x);
     *    for (int z = 0; z &lt;= noiseSizeXZ; z++) {
     *       long blockZ = QuartPos.toBlock(baseQuartZ + z);
     *       values[x + z * sizeXZ] = noiseFiller.compute(new RealContext(blockX, 0L, blockZ));
     *    }
     * }
     * </pre>
     */
    private static MethodNode fillNow() {
        MethodNode m = new MethodNode(Opcodes.ACC_PRIVATE, "farlands$fillNow", "()V", null, null);
        InsnList il = m.instructions;
        LabelNode done = new LabelNode();
        LabelNode loopZ = new LabelNode();
        LabelNode nextX = new LabelNode();
        LabelNode loopX = new LabelNode();
        LabelNode endX = new LabelNode();

        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, PENDING, "Z"));
        il.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFEQ, done));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, PENDING, "Z"));

        // long baseQuartX (slot 1), long baseQuartZ (slot 3)
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "this$0", "L" + OUTER + ";"));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, OUTER, "farlands$firstNoiseXLong", "J"));
        il.add(new VarInsnNode(Opcodes.LSTORE, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "this$0", "L" + OUTER + ";"));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, OUTER, "farlands$firstNoiseZLong", "J"));
        il.add(new VarInsnNode(Opcodes.LSTORE, 3));

        // diagnostic: one-shot print of the wide quart base
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "this$0", "L" + OUTER + ";"));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, OUTER, "farlands$originX", "J"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "this$0", "L" + OUTER + ";"));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, OUTER, "farlands$originZ", "J"));
        il.add(new VarInsnNode(Opcodes.LLOAD, 1));
        il.add(new VarInsnNode(Opcodes.LLOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, DIAG, "log", "(JJJJ)V", false));

        // int x = 0 (slot 5)
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 5));
        il.add(loopX);
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "this$0", "L" + OUTER + ";"));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, OUTER, "noiseSizeXZ", "I"));
        il.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IF_ICMPGT, endX));

        // long blockX (slot 6) = QuartPos.toBlock(baseQuartX + x)
        il.add(new VarInsnNode(Opcodes.LLOAD, 1));
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/core/QuartPos", "toBlock",
            "(J)J", false));
        il.add(new VarInsnNode(Opcodes.LSTORE, 6));

        // int z = 0 (slot 8)
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 8));
        il.add(loopZ);
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "this$0", "L" + OUTER + ";"));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, OUTER, "noiseSizeXZ", "I"));
        il.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IF_ICMPGT, nextX));

        // long blockZ (slot 9) = QuartPos.toBlock(baseQuartZ + z)
        il.add(new VarInsnNode(Opcodes.LLOAD, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/core/QuartPos", "toBlock",
            "(J)J", false));
        il.add(new VarInsnNode(Opcodes.LSTORE, 9));

        // values[x + z * sizeXZ] = filler.compute(new RealContext(blockX, 0L, blockZ))
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "values", "[D"));
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "sizeXZ", "I"));
        il.add(new InsnNode(Opcodes.IMUL));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "noiseFiller",
            "Lnet/minecraft/world/level/levelgen/DensityFunction;"));
        il.add(new TypeInsnNode(Opcodes.NEW, REAL_CONTEXT));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.LLOAD, 6));
        il.add(new InsnNode(Opcodes.LCONST_0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 9));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, REAL_CONTEXT, "<init>", "(JJJ)V", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
            "net/minecraft/world/level/levelgen/DensityFunction", "compute",
            "(L" + CONTEXT + ";)D", true));
        il.add(new InsnNode(Opcodes.DASTORE));

        il.add(new org.objectweb.asm.tree.IincInsnNode(8, 1));
        il.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.GOTO, loopZ));
        il.add(nextX);
        il.add(new org.objectweb.asm.tree.IincInsnNode(5, 1));
        il.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.GOTO, loopX));
        il.add(endX);
        il.add(done);
        il.add(new InsnNode(Opcodes.RETURN));
        return m;
    }

    /**
     * compute(): wide cache lookup.
     *
     * <pre>
     * fillNow();
     * long qx = QuartPos.fromBlock((long) context.getBlockXDouble());
     * long qz = QuartPos.fromBlock((long) context.getBlockZDouble());
     * long dx = qx - QuartPos.fromBlock(originX);
     * long dz = qz - QuartPos.fromBlock(originZ);
     * if (dx &gt;= 0 &amp;&amp; dz &gt;= 0 &amp;&amp; dx &lt; sizeXZ &amp;&amp; dz &lt; sizeXZ)
     *    return values[(int) (dx + dz * sizeXZ)];
     * return noiseFiller.compute(context);
     * </pre>
     */
    private static boolean rewriteCompute(ClassNode node) {
        for (MethodNode m : node.methods) {
            if (!"compute".equals(m.name) || !COMPUTE_DESC.equals(m.desc)) {
                continue;
            }
            m.instructions.clear();
            m.tryCatchBlocks.clear();
            m.localVariables = null;
            InsnList il = m.instructions;
            LabelNode fallback = new LabelNode();

            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$fillNow", "()V", false));

            // long qx (slot 2)
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, CONTEXT, "getBlockXDouble", "()D", true));
            il.add(new InsnNode(Opcodes.D2L));
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/core/QuartPos", "fromBlock",
                "(J)J", false));
            il.add(new VarInsnNode(Opcodes.LSTORE, 2));
            // long qz (slot 4)
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, CONTEXT, "getBlockZDouble", "()D", true));
            il.add(new InsnNode(Opcodes.D2L));
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/core/QuartPos", "fromBlock",
                "(J)J", false));
            il.add(new VarInsnNode(Opcodes.LSTORE, 4));
            // long dx (slot 6), long dz (slot 8)
            il.add(new VarInsnNode(Opcodes.LLOAD, 2));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "this$0", "L" + OUTER + ";"));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, OUTER, "farlands$firstNoiseXLong", "J"));
            il.add(new InsnNode(Opcodes.LSUB));
            il.add(new VarInsnNode(Opcodes.LSTORE, 6));
            il.add(new VarInsnNode(Opcodes.LLOAD, 4));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "this$0", "L" + OUTER + ";"));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, OUTER, "farlands$firstNoiseZLong", "J"));
            il.add(new InsnNode(Opcodes.LSUB));
            il.add(new VarInsnNode(Opcodes.LSTORE, 8));

            il.add(new VarInsnNode(Opcodes.LLOAD, 6));
            il.add(new InsnNode(Opcodes.LCONST_0));
            il.add(new InsnNode(Opcodes.LCMP));
            il.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFLT, fallback));
            il.add(new VarInsnNode(Opcodes.LLOAD, 8));
            il.add(new InsnNode(Opcodes.LCONST_0));
            il.add(new InsnNode(Opcodes.LCMP));
            il.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFLT, fallback));
            il.add(new VarInsnNode(Opcodes.LLOAD, 6));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "sizeXZ", "I"));
            il.add(new InsnNode(Opcodes.I2L));
            il.add(new InsnNode(Opcodes.LCMP));
            il.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFGE, fallback));
            il.add(new VarInsnNode(Opcodes.LLOAD, 8));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "sizeXZ", "I"));
            il.add(new InsnNode(Opcodes.I2L));
            il.add(new InsnNode(Opcodes.LCMP));
            il.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFGE, fallback));

            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "values", "[D"));
            il.add(new VarInsnNode(Opcodes.LLOAD, 6));
            il.add(new VarInsnNode(Opcodes.LLOAD, 8));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "sizeXZ", "I"));
            il.add(new InsnNode(Opcodes.I2L));
            il.add(new InsnNode(Opcodes.LMUL));
            il.add(new InsnNode(Opcodes.LADD));
            il.add(new InsnNode(Opcodes.L2I));
            il.add(new InsnNode(Opcodes.DALOAD));
            il.add(new InsnNode(Opcodes.DRETURN));

            il.add(fallback);
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "noiseFiller",
                "Lnet/minecraft/world/level/levelgen/DensityFunction;"));
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "net/minecraft/world/level/levelgen/DensityFunction", "compute",
                "(L" + CONTEXT + ";)D", true));
            il.add(new InsnNode(Opcodes.DRETURN));
            return true;
        }
        return false;
    }

    private static boolean has(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) {
            if (name.equals(m.name) && desc.equals(m.desc)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String describe(String internalName) {
        return "NoiseChunkFlatCacheLongPatch (long quart lattice for FlatCache)";
    }
}
