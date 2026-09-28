package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Widens the aquifer lattice origin at the +/-2^31 seam.
 *
 * <p>{@code Aquifer$NoiseBasedAquifer.<init>} derives its grid origin and
 * span from the int {@code ChunkPos.getMinBlockX()/getMaxBlockX()}, i.e.
 * {@code chunk.x << 4}. At block 2^31 the shift wraps to
 * {@code Integer.MIN_VALUE}, and the subsequent {@code -5} overflows again.
 * The min and max grid values then land in <em>different</em> int domains
 * (min wraps to a large positive lattice, max stays negative), producing
 * an inverted lattice. Three failures follow:</p>
 * <ol>
 *   <li>{@code gridSizeX*gridSizeY*gridSizeZ} overflows to a huge/negative
 *       value and {@code new FluidStatus[...]} OOMs;</li>
 *   <li>{@code maxPreliminarySurfaceLevel(fromGridX(minGridX,0), ...,
 *       fromGridX(maxGrid,9), ...)} is called with inverted corners;</li>
 *   <li>the cache cells are seeded on the wrong side of the seam (the
 *       "trench" / 沟).</li>
 * </ol>
 *
 * <p>Fix: derive {@code minGridX}/{@code minGridZ} and the max-grid locals
 * from {@code ChunkPos.xLong()/zLong()} in the long domain
 * ({@code ((xLong<<4)-5)>>4}), and route {@code computeSubstance}'s cache
 * grid coordinates through {@code FarAquiferHelper.aquiferGridX/Z}, which
 * recovers the wide block coordinate before the floor division. The stored
 * fields stay {@code int} (a grid index fits comfortably at 2^31); at normal
 * coordinates every expression collapses to the vanilla int arithmetic, so
 * generation is bit-identical.</p>
 */
public final class AquiferLatticePatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/levelgen/Aquifer$NoiseBasedAquifer";
    private static final String CHUNKPOS = "net/minecraft/world/level/ChunkPos";
    private static final String HELPER = "com/farlands/g2/runtime/FarAquiferHelper";
    private static final String CTOR_DESC =
        "(Lnet/minecraft/world/level/levelgen/NoiseChunk;Lnet/minecraft/world/level/ChunkPos;"
            + "Lnet/minecraft/world/level/levelgen/NoiseRouter;"
            + "Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;II"
            + "Lnet/minecraft/world/level/levelgen/Aquifer$FluidPicker;)V";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        MethodNode ctor = find(node, "<init>", CTOR_DESC);
        if (ctor == null) {
            return original;
        }
        // Idempotency: after patching the constructor stores minGridX twice
        // (vanilla then our wide recompute).
        if (countPutField(ctor, "minGridX") > 1) {
            return original;
        }

        boolean changed = false;
        // minGridX = ((xLong<<4) - 5) >> 4   (was gridX(getMinBlockX()-5), int)
        FieldInsnNode putMinX = findPutField(ctor, "minGridX");
        if (putMinX != null) {
            ctor.instructions.insert(putMinX, wideMinGrid("xLong", "minGridX"));
            changed = true;
        }
        // maxGridX local (slot 8) = (((xLong<<4)+15) - 5) >> 4 + 1
        VarInsnNode store8 = findIStore(ctor, 8);
        if (store8 != null) {
            ctor.instructions.insert(store8, wideMaxGrid("xLong", 8));
            changed = true;
        }
        FieldInsnNode putMinZ = findPutField(ctor, "minGridZ");
        if (putMinZ != null) {
            ctor.instructions.insert(putMinZ, wideMinGrid("zLong", "minGridZ"));
            changed = true;
        }
        VarInsnNode store11 = findIStore(ctor, 11);
        if (store11 != null) {
            ctor.instructions.insert(store11, wideMaxGrid("zLong", 11));
            changed = true;
        }

        // computeSubstance: route the cache grid coords through the wide
        // helpers instead of gridX/gridZ of the wrapped block coordinate.
        for (MethodNode m : node.methods) {
            if ("computeSubstance".equals(m.name)) {
                changed |= replaceGridCall(m, "gridX", "aquiferGridX");
                changed |= replaceGridCall(m, "gridZ", "aquiferGridZ");
            }
        }

        if (!changed) {
            return original;
        }

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static MethodNode find(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) {
            if (name.equals(m.name) && desc.equals(m.desc)) {
                return m;
            }
        }
        return null;
    }

    private static FieldInsnNode findPutField(MethodNode m, String field) {
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof FieldInsnNode fi && fi.getOpcode() == Opcodes.PUTFIELD
                && TARGET.equals(fi.owner) && field.equals(fi.name)) {
                return fi;
            }
        }
        return null;
    }

    private static int countPutField(MethodNode m, String field) {
        int n = 0;
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof FieldInsnNode fi && fi.getOpcode() == Opcodes.PUTFIELD
                && TARGET.equals(fi.owner) && field.equals(fi.name)) {
                n++;
            }
        }
        return n;
    }

    private static VarInsnNode findIStore(MethodNode m, int slot) {
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof VarInsnNode v && v.getOpcode() == Opcodes.ISTORE && v.var == slot) {
                return v;
            }
        }
        return null;
    }

    /** Leaves {@code ((xLong<<4) - 5) >> 4} on the stack and stores it in {@code field}. */
    private static InsnList wideMinGrid(String axisAccessor, String field) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(chunkLong(axisAccessor));
        il.add(new LdcInsnNode(4));
        il.add(new InsnNode(Opcodes.LSHL));
        il.add(new LdcInsnNode(5L));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new LdcInsnNode(4));
        il.add(new InsnNode(Opcodes.LSHR));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, field, "I"));
        return il;
    }

    /** Stores {@code (((xLong<<4)+15) - 5) >> 4 + 1} into local {@code slot}. */
    private static InsnList wideMaxGrid(String axisAccessor, int slot) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(chunkLong(axisAccessor));
        il.add(new LdcInsnNode(4));
        il.add(new InsnNode(Opcodes.LSHL));
        il.add(new LdcInsnNode(15L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new LdcInsnNode(5L));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new LdcInsnNode(4));
        il.add(new InsnNode(Opcodes.LSHR));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new VarInsnNode(Opcodes.ISTORE, slot));
        return il;
    }

    private static MethodInsnNode chunkLong(String accessor) {
        return new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNKPOS, accessor, "()J", false);
    }

    /**
     * Replaces {@code iload block; bipush -5; iadd; invokestatic gridX(int)}
     * with {@code iload block; invokestatic helper(int)} in the given method.
     */
    private static boolean replaceGridCall(MethodNode m, String gridName, String helperName) {
        for (AbstractInsnNode insn : m.instructions) {
            if (!(insn instanceof MethodInsnNode mi) || mi.getOpcode() != Opcodes.INVOKESTATIC
                || !TARGET.equals(mi.owner) || !gridName.equals(mi.name) || !"(I)I".equals(mi.desc)) {
                continue;
            }
            AbstractInsnNode iadd = insn.getPrevious();
            AbstractInsnNode bipush = iadd == null ? null : iadd.getPrevious();
            if (iadd == null || iadd.getOpcode() != Opcodes.IADD
                || bipush == null || bipush.getOpcode() != Opcodes.BIPUSH) {
                continue;
            }
            m.instructions.remove(iadd);
            m.instructions.remove(bipush);
            m.instructions.set(insn,
                new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, helperName, "(I)I", false));
            return true;
        }
        return false;
    }

    @Override
    public String describe(String internalName) {
        return "AquiferLatticePatch (wide grid origin + cache coords at 2^31)";
    }
}
