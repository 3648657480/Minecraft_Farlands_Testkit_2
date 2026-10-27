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
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Carries the wide chunk origin into {@code NoiseChunk} and keeps the noise
 * lattice in the long domain.
 *
 * <p>{@code forChunk} truncates the chunk position to int before
 * constructing, so the noise pipeline loses the true position beyond 2^31
 * (terrain repeats and tears along the wrap seam). This patch adds wide
 * origin fields and a constructor variant, and rewires {@code forChunk} to
 * pass {@code ChunkPos.xLong() &lt;&lt; 4}. The FarLands mod's mixin then
 * reconstructs continuous real coordinates in {@code getBlockXDouble()}.</p>
 *
 * <p>The cell start is kept as an authoritative long
 * ({@code farlands$cellStartBlockX/ZLong}) because the int field wraps for the
 * cell just across the 2^31 seam: the last cell of the chunk left of the seam
 * samples its right-hand corner at block 2^31, where
 * {@code cellX * cellWidth} overflows to {@code Integer.MIN_VALUE}. That
 * mirrored corner pulled the last two columns of every chunk before 2^31 up by
 * more than ten blocks. The long value is {@code origin + cellIndex *
 * cellWidth} for wide-constructed chunks and the plain int product for the
 * vanilla constructor (all normal-coordinate paths), so normal worlds stay
 * bit-identical.</p>
 */
public final class NoiseChunkPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/levelgen/NoiseChunk";
    private static final String VANILLA_CTOR_DESC =
        "(ILnet/minecraft/world/level/levelgen/RandomState;IILnet/minecraft/world/level/levelgen/NoiseSettings;"
            + "Lnet/minecraft/world/level/levelgen/DensityFunctions$BeardifierOrMarker;"
            + "Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;"
            + "Lnet/minecraft/world/level/levelgen/Aquifer$FluidPicker;"
            + "Lnet/minecraft/world/level/levelgen/blending/Blender;)V";
    private static final String WIDE_CTOR_DESC =
        "(ILnet/minecraft/world/level/levelgen/RandomState;IIJJLnet/minecraft/world/level/levelgen/NoiseSettings;"
            + "Lnet/minecraft/world/level/levelgen/DensityFunctions$BeardifierOrMarker;"
            + "Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;"
            + "Lnet/minecraft/world/level/levelgen/Aquifer$FluidPicker;"
            + "Lnet/minecraft/world/level/levelgen/blending/Blender;)V";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        if (node.fields.stream().anyMatch(f -> "farlands$originX".equals(f.name))) {
            return original;
        }

        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "farlands$originX", "J", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "farlands$originZ", "J", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "farlands$wideOrigin", "Z", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "farlands$firstNoiseXLong", "J", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "farlands$firstNoiseZLong", "J", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "farlands$cellStartBlockXLong", "J", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "farlands$cellStartBlockZLong", "J", null, null));
        node.methods.add(wideConstructor());
        node.methods.add(cellStartLong("farlands$cellStartX", "farlands$originX", "firstCellX",
            "farlands$cellStartBlockXLong", false));
        node.methods.add(cellStartLong("farlands$cellStartZ", "farlands$originZ", "firstCellZ",
            "farlands$cellStartBlockZLong", false));
        node.methods.add(cellStartLong("farlands$cellStartXIndex", "farlands$originX", "firstCellX",
            "farlands$cellStartBlockXLong", true));
        node.methods.add(cellStartLong("farlands$cellStartZIndex", "farlands$originZ", "firstCellZ",
            "farlands$cellStartBlockZLong", true));
        rewriteForChunk(node);
        installCellStartLong(node);
        installFirstNoiseLong(node);

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    /** (…, int minBlockX, int minBlockZ, long originX, long originZ, …rest) delegating to the vanilla ctor. */
    private static MethodNode wideConstructor() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", WIDE_CTOR_DESC, null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 9));
        il.add(new VarInsnNode(Opcodes.ALOAD, 10));
        il.add(new VarInsnNode(Opcodes.ALOAD, 11));
        il.add(new VarInsnNode(Opcodes.ALOAD, 12));
        il.add(new VarInsnNode(Opcodes.ALOAD, 13));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", VANILLA_CTOR_DESC, false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 5));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "farlands$originX", "J"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 7));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "farlands$originZ", "J"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "farlands$wideOrigin", "Z"));
        // farlands$firstNoise{X,Z}Long = QuartPos.fromBlock(origin) in the long domain
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/core/QuartPos", "fromBlock",
            "(J)J", false));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "farlands$firstNoiseXLong", "J"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/core/QuartPos", "fromBlock",
            "(J)J", false));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "farlands$firstNoiseZLong", "J"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "farlands$originX", "J"));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "farlands$cellStartBlockXLong", "J"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "farlands$originZ", "J"));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "farlands$cellStartBlockZLong", "J"));
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    /**
     * {@code void farlands$cellStartX(int cellIndex)}: publishes the absolute
     * (long) block coordinate of the cell start for the noise sampler.
     */
    private static MethodNode cellStartLong(String name, String origin, String firstCell,
            String target, boolean relativeIndex) {
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, name, "(I)V", null, null);
        InsnList il = m.instructions;
        org.objectweb.asm.tree.LabelNode useInt = new org.objectweb.asm.tree.LabelNode();
        org.objectweb.asm.tree.LabelNode done = new org.objectweb.asm.tree.LabelNode();

        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "farlands$wideOrigin", "Z"));
        il.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFEQ, useInt));
        // wide: origin + (cellIndex [- firstCell]) * cellWidth
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, origin, "J"));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        if (!relativeIndex) {
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, firstCell, "I"));
            il.add(new InsnNode(Opcodes.ISUB));
        }
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "cellWidth", "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, target, "J"));
        il.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.GOTO, done));

        // vanilla constructor: plain int arithmetic (never wraps at normal coords)
        il.add(useInt);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        if (relativeIndex) {
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, firstCell, "I"));
            il.add(new VarInsnNode(Opcodes.ILOAD, 1));
            il.add(new InsnNode(Opcodes.IADD));
        } else {
            il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        }
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "cellWidth", "I"));
        il.add(new InsnNode(Opcodes.IMUL));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, target, "J"));
        il.add(done);
        il.add(new InsnNode(Opcodes.RETURN));
        return m;
    }

    /** Sets the long cell start next to each int cell-start assignment. */
    private static void installCellStartLong(ClassNode node) {
        for (MethodNode m : node.methods) {
            if ("fillSlice".equals(m.name) && "(ZI)V".equals(m.desc)) {
                insertAfterPut(m, "cellStartBlockX", "farlands$cellStartX", 2);
                insertAfterPut(m, "cellStartBlockZ", "farlands$cellStartZ", 4);
            } else if ("advanceCellX".equals(m.name) && "(I)V".equals(m.desc)) {
                insertAfterPut(m, "cellStartBlockX", "farlands$cellStartXIndex", 1);
            } else if ("selectCellYZ".equals(m.name) && "(II)V".equals(m.desc)) {
                insertAfterPut(m, "cellStartBlockZ", "farlands$cellStartZIndex", 2);
            }
        }
    }

    private static void insertAfterPut(MethodNode m, String field, String helper, int varIndex) {        FieldInsnNode put = null;
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof FieldInsnNode fi && fi.getOpcode() == Opcodes.PUTFIELD
                && TARGET.equals(fi.owner) && field.equals(fi.name)) {
                put = fi;
            }
        }
        if (put == null || put.getNext() == null) {
            throw new IllegalStateException(TARGET + "#" + m.name + " PUTFIELD " + field + " not found");
        }
        InsnList call = new InsnList();
        call.add(new VarInsnNode(Opcodes.ALOAD, 0));
        call.add(new VarInsnNode(Opcodes.ILOAD, varIndex));
        call.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, helper, "(I)V", false));
        m.instructions.insert(put.getNext(), call);
    }

    /**
     * Mirrors the vanilla constructor's int {@code firstNoiseX/Z} into the long
     * fields; the wide constructor overwrites them from the true origin.
     */
    private static void installFirstNoiseLong(ClassNode node) {
        for (MethodNode m : node.methods) {
            if (!"<init>".equals(m.name) || !VANILLA_CTOR_DESC.equals(m.desc)) {
                continue;
            }
            mirrorNoiseLong(m, "firstNoiseX", "farlands$firstNoiseXLong");
            mirrorNoiseLong(m, "firstNoiseZ", "farlands$firstNoiseZLong");
            return;
        }
    }

    private static void mirrorNoiseLong(MethodNode m, String source, String target) {
        FieldInsnNode put = null;
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof FieldInsnNode fi && fi.getOpcode() == Opcodes.PUTFIELD
                && TARGET.equals(fi.owner) && source.equals(fi.name)) {
                put = fi;
            }
        }
        if (put == null || put.getNext() == null) {
            throw new IllegalStateException(TARGET + "#<init> PUTFIELD " + source + " not found");
        }
        InsnList call = new InsnList();
        call.add(new VarInsnNode(Opcodes.ALOAD, 0));
        call.add(new VarInsnNode(Opcodes.ALOAD, 0));
        call.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, source, "I"));
        call.add(new InsnNode(Opcodes.I2L));
        call.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, target, "J"));
        m.instructions.insert(put.getNext(), call);
    }

    /**
     * forChunk: additionally compute xLong()&lt;&lt;4 / zLong()&lt;&lt;4 and invoke the wide ctor.
     */
    private static void rewriteForChunk(ClassNode node) {
        MethodNode forChunk = null;
        for (MethodNode m : node.methods) {
            if ("forChunk".equals(m.name)) {
                forChunk = m;
                break;
            }
        }
        if (forChunk == null) {
            throw new IllegalStateException(TARGET + "#forChunk not found");
        }
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            "net/minecraft/world/level/levelgen/NoiseGeneratorSettings", "noiseSettings",
            "()Lnet/minecraft/world/level/levelgen/NoiseSettings;", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/levelgen/NoiseSettings",
            "clampToHeightAccessor",
            "(Lnet/minecraft/world/level/LevelHeightAccessor;)Lnet/minecraft/world/level/levelgen/NoiseSettings;",
            false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 6));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/chunk/ChunkAccess", "getPos",
            "()Lnet/minecraft/world/level/ChunkPos;", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 7));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 16));
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/levelgen/NoiseSettings",
            "getCellWidth", "()I", false));
        il.add(new InsnNode(Opcodes.IDIV));
        il.add(new VarInsnNode(Opcodes.ISTORE, 8));
        // wide origins: xLong() << 4, zLong() << 4
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/ChunkPos", "xLong", "()J", false));
        il.add(new InsnNode(Opcodes.ICONST_4));
        il.add(new InsnNode(Opcodes.LSHL));
        il.add(new VarInsnNode(Opcodes.LSTORE, 9));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/ChunkPos", "zLong", "()J", false));
        il.add(new InsnNode(Opcodes.ICONST_4));
        il.add(new InsnNode(Opcodes.LSHL));
        il.add(new VarInsnNode(Opcodes.LSTORE, 11));
        // publish the generation origin for origin-aware unwrap
        il.add(new VarInsnNode(Opcodes.LLOAD, 9));
        il.add(new VarInsnNode(Opcodes.LLOAD, 11));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/farlands/g2/util/FarProjection",
            "setGenerationOrigin", "(JJ)V", false));
        // publish the raw router so far-origin chunks can sample it directly
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/levelgen/RandomState",
            "router", "()Lnet/minecraft/world/level/levelgen/NoiseRouter;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/farlands/g2/util/FarProjection",
            "setRawRouter", "(Lnet/minecraft/world/level/levelgen/NoiseRouter;)V", false));
        // construct
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/ChunkPos", "getMinBlockX",
            "()I", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/ChunkPos", "getMinBlockZ",
            "()I", false));
        il.add(new VarInsnNode(Opcodes.LLOAD, 9));
        il.add(new VarInsnNode(Opcodes.LLOAD, 11));
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", WIDE_CTOR_DESC, false));
        il.add(new InsnNode(Opcodes.ARETURN));
        forChunk.instructions.clear();
        forChunk.localVariables = null;
        forChunk.tryCatchBlocks.clear();
        forChunk.instructions.add(il);
    }
}