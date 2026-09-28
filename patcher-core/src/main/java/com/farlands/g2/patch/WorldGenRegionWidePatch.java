package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * W5 gatekeeper: makes {@code WorldGenRegion} resolve chunks from the wide
 * block coordinates during generation.
 *
 * <p>All generation reads/writes funnel through this class. Its
 * {@code getBlockState/getFluidState/isWithinWriteZone(BlockPos)} and the
 * inherited {@code LevelReader.getChunk(BlockPos)} derive the chunk with
 * {@code SectionPos.blockToSectionCoord(pos.getX())}. At block 2^31 the int
 * view wraps, the +1 neighbour chunk (section 2^27, exactly the boundary) is
 * misclassified as out of the write zone, and every boundary feature write is
 * dropped with the "Detected setBlock in a far chunk" error. A single ore/disk
 * column then emits an unbounded log flood that trips the 60 s server
 * watchdog.</p>
 *
 * <p>Here every one of those entry points computes the chunk from
 * {@code getLongX/getLongZ}; the boundary chunk is then correctly inside the
 * write zone and the write lands in the real neighbour chunk. The
 * {@code LevelReader.getChunk(BlockPos)} default is overridden locally (a new
 * public method on WorldGenRegion) so the three inheriting callers are fixed
 * too. At normal coordinates the long equals the int it replaced.</p>
 *
 * <p>The same class also has an independent out-of-bounds throw:
 * {@code getCurrentDifficultyAt(BlockPos)} (vanilla offset 21-31) computes
 * {@code hasChunk(blockToSectionCoord(pos.getX()), blockToSectionCoord(pos.getZ()))}
 * from the <em>int-wrapped</em> block coordinates and throws
 * {@code "We are asking a region for a chunk out of bound"} when false.
 * {@code NaturalSpawner.spawnMobsForChunkGeneration} starts at
 * {@code ChunkPos.getMinBlockX()} and drifts the candidate x/z by
 * {@code rand.nextInt(5)-rand.nextInt(5)} per attempt; at the 2^31 seam the
 * start already wraps for chunk {@code 134217728} and the drift pushes past
 * block {@code 2147483647} for chunk {@code 134217727}, so the {@code BlockPos}
 * handed to this method carries a wrapped (negative) coordinate, the section
 * query lands at {@code -134217728} while the region center is
 * {@code 134217728}, and {@code generateSpawn} dies. Unlike the {@code getChunk}
 * path this throw is not on a chunk-resolution path - the returned
 * {@code DifficultyInstance} only reads level difficulty, clock time and moon
 * brightness - so it is degraded (return the difficulty) rather than
 * redirected, matching {@link WgrPatch}'s "no hard fail past 2^31" policy.
 * At normal coordinates the guard never fired, so this is a no-op there.</p>
 */
public final class WorldGenRegionWidePatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/server/level/WorldGenRegion";
    private static final String BLOCKPOS = "net/minecraft/core/BlockPos";
    private static final String CHUNKACCESS = "net/minecraft/world/level/chunk/ChunkAccess";
    private static final String BLOCKSTATE = "net/minecraft/world/level/block/state/BlockState";
    private static final String FLUIDSTATE = "net/minecraft/world/level/material/FluidState";
    private static final String SERVERLEVEL = "net/minecraft/server/level/ServerLevel";
    private static final String DIFFICULTY = "net/minecraft/world/DifficultyInstance";
    private static final String DIFFICULTY_DESC =
        "(Lnet/minecraft/world/Difficulty;JJF)V";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        if (find(node, "getChunk", "(L" + BLOCKPOS + ";)L" + CHUNKACCESS + ";") != null) {
            return original; // already patched
        }

        rewrite(node, "getBlockState", "(L" + BLOCKPOS + ";)L" + BLOCKSTATE + ";", readBody("getBlockState"));
        rewrite(node, "getFluidState", "(L" + BLOCKPOS + ";)L" + FLUIDSTATE + ";", readBody("getFluidState"));
        rewrite(node, "isWithinWriteZone", "(L" + BLOCKPOS + ";)Z", isWithinWriteZoneBody());
        rewrite(node, "getCurrentDifficultyAt",
            "(L" + BLOCKPOS + ";)L" + DIFFICULTY + ";", difficultyBody());
        node.methods.add(getChunkBlockPos());

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    /**
     * {@code int cx = (int)(pos.getLongX()>>4); int cz = (int)(pos.getLongZ()>>4);
     *  warnIfReadOutsideWriteZone(cx, cz); return getChunk(cx, cz).read(pos);}
     */
    private static InsnList readBody(String read) {
        String readDesc = read.equals("getBlockState")
            ? "(L" + BLOCKPOS + ";)L" + BLOCKSTATE + ";" : "(L" + BLOCKPOS + ";)L" + FLUIDSTATE + ";";
        InsnList il = new InsnList();
        sectionCoord(il, 1, 2, "X");
        sectionCoord(il, 1, 3, "Z");
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "warnIfReadOutsideWriteZone", "(II)V", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "getChunk",
            "(II)L" + CHUNKACCESS + ";", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNKACCESS, read, readDesc, false));
        if (read.equals("getBlockState")) {
            il.add(new InsnNode(Opcodes.ARETURN));
        } else {
            il.add(new InsnNode(Opcodes.ARETURN));
        }
        return il;
    }

    /** {@code return isWithinWriteZone((int)(getLongX()>>4), (int)(getLongZ()>>4));} */
    private static InsnList isWithinWriteZoneBody() {
        InsnList il = new InsnList();
        sectionCoord(il, 1, 2, "X");
        sectionCoord(il, 1, 3, "Z");
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "isWithinWriteZone", "(II)Z", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    /**
     * Vanilla {@code getCurrentDifficultyAt(BlockPos)} throws
     * {@code "We are asking a region for a chunk out of bound"} when
     * {@code hasChunk(blockToSectionCoord(pos.getX()), blockToSectionCoord(pos.getZ()))}
     * is false. The block coordinate is int-wrapped, so past 2^31 this test is
     * unsound. The difficulty instance does not need the chunk, so drop the
     * guard: {@code new DifficultyInstance(level.getDifficulty(),
     * level.getOverworldClockTime(), 0L, level.getMoonBrightness(pos))}. At
     * normal coordinates the guard never fired, so the produced value is
     * unchanged.
     */
    private static InsnList difficultyBody() {
        InsnList il = new InsnList();
        il.add(new TypeInsnNode(Opcodes.NEW, DIFFICULTY));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "level",
            "L" + SERVERLEVEL + ";"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SERVERLEVEL, "getDifficulty",
            "()Lnet/minecraft/world/Difficulty;", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "level",
            "L" + SERVERLEVEL + ";"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SERVERLEVEL, "getOverworldClockTime",
            "()J", false));
        il.add(new InsnNode(Opcodes.LCONST_0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "level",
            "L" + SERVERLEVEL + ";"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SERVERLEVEL, "getMoonBrightness",
            "(L" + BLOCKPOS + ";)F", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, DIFFICULTY, "<init>",
            DIFFICULTY_DESC, false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    /** New override of the {@code LevelReader.getChunk(BlockPos)} default. */
    private static MethodNode getChunkBlockPos() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "getChunk",
            "(L" + BLOCKPOS + ";)L" + CHUNKACCESS + ";", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        sectionCoord(il, 1, 2, "X");
        sectionCoord(il, 1, 3, "Z");
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "getChunk",
            "(II)L" + CHUNKACCESS + ";", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    /** {@code dst = (int)(local.getLongA >> 4)}. */
    private static void sectionCoord(InsnList il, int posSlot, int dst, String axis) {
        il.add(new VarInsnNode(Opcodes.ALOAD, posSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLong" + axis, "()J", false));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 4));
        il.add(new InsnNode(Opcodes.LSHR));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ISTORE, dst));
    }

    private static MethodNode find(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) {
            if (name.equals(m.name) && desc.equals(m.desc)) {
                return m;
            }
        }
        return null;
    }

    private static void rewrite(ClassNode node, String name, String desc, InsnList body) {
        MethodNode m = find(node, name, desc);
        if (m == null) {
            throw new IllegalStateException(TARGET + "#" + name + desc + " not found");
        }
        m.instructions.clear();
        m.localVariables = null;
        m.tryCatchBlocks.clear();
        m.instructions.add(body);
    }
}
