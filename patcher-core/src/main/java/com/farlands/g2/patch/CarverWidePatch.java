package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * W5-D: widens the carver subsystem at the 2^31 block boundary.
 *
 * <p>Carvers receive a {@code ChunkPos} and derive every world column from it
 * with the int block-coordinate accessors ({@code getMinBlockX()},
 * {@code getMiddleBlockX()}, {@code getBlockX(i)}), i.e.
 * {@code chunkX << 4} in the int domain. At chunk {@code 2^27} (block
 * {@code 2^31}) that shift wraps to {@code Integer.MIN_VALUE}: the carve
 * ellipsoid's local bounds collapse (the whole x range clamps to a single
 * wrapped column and no cave is carved), {@code canReach} rejects the chunk
 * (its middle is seen at the negative mirror), and the cave/canyon start
 * columns are wrapped. The neighbour chunk {@code 134217728} is exactly the
 * seam chunk that {@code NoiseBasedChunkGenerator.applyCarvers} generates
 * when the target chunk is {@code 134217727}, so the boundary run exercises
 * all of them.</p>
 *
 * <p>Fixes, all in the long domain and all identities at normal
 * coordinates ({@code getLongX == getX} there):</p>
 * <ul>
 *   <li>{@code WorldCarver.carveEllipsoid} is rebuilt: the min/middle block
 *       coordinates come from {@code ChunkPos.getMinBlockXLong()}/
 *       {@code getMiddleBlockXLong()}, the local loop bounds and the world
 *       column are long, and the probe position is written with
 *       {@code MutableBlockPos.set(long,long,long)} so the whole
 *       carve/write path keeps the wide column. Loop order, RNG order (none)
 *       and the {@code CarveSkipChecker}/{@code CarvingMask} call sequence are
 *       preserved.</li>
 *   <li>{@code WorldCarver.canReach} reads the middle block coordinates
 *       through the long accessors.</li>
 *   <li>{@code CaveWorldCarver.carve}/{@code CanyonWorldCarver.carve} start
 *       columns ({@code ChunkPos.getBlockX/Z(int)}) read through
 *       {@code getBlockXLong/ZLong(int)}.</li>
 *   <li>{@code NoiseBasedChunkGenerator.lambda$applyCarvers$2} (the carver
 *       biome lookup) computes {@code QuartPos.fromBlock(minBlockX)} from the
 *       wrapped int; it is folded into the long shift
 *       {@code (getMinBlockXLong() >> 2)} so the sampled biome belongs to the
 *       real chunk.</li>
 * </ul>
 */
public final class CarverWidePatch implements ClassPatch {

    private static final String WORLD_CARVER = "net/minecraft/world/level/levelgen/carver/WorldCarver";
    private static final String CAVE_CARVER = "net/minecraft/world/level/levelgen/carver/CaveWorldCarver";
    private static final String CANYON_CARVER = "net/minecraft/world/level/levelgen/carver/CanyonWorldCarver";
    private static final String NOISE_GEN = "net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator";
    private static final String CHUNKPOS = "net/minecraft/world/level/ChunkPos";
    private static final String MUTABLE = "net/minecraft/core/BlockPos$MutableBlockPos";
    private static final String CARVING_CONTEXT = "net/minecraft/world/level/levelgen/carver/CarvingContext";
    private static final String CARVER_CONFIG = "net/minecraft/world/level/levelgen/carver/CarverConfiguration";
    private static final String CHUNK_ACCESS = "net/minecraft/world/level/chunk/ChunkAccess";
    private static final String CARVING_MASK = "net/minecraft/world/level/chunk/CarvingMask";
    private static final String SKIP_CHECKER = "net/minecraft/world/level/levelgen/carver/WorldCarver$CarveSkipChecker";
    private static final String AQUIFER = "net/minecraft/world/level/levelgen/Aquifer";
    private static final String FUNCTION = "java/util/function/Function";
    private static final String MUTABLE_BOOLEAN = "org/apache/commons/lang3/mutable/MutableBoolean";
    private static final String MTH = "net/minecraft/util/Mth";
    private static final String MATH = "java/lang/Math";
    private static final String QUART_POS = "net/minecraft/core/QuartPos";

    private static final String CARVE_ELLIPSOID_DESC =
        "(L" + CARVING_CONTEXT + ";L" + CARVER_CONFIG + ";L" + CHUNK_ACCESS
        + ";L" + FUNCTION + ";L" + AQUIFER + ";DDDDDL" + CARVING_MASK
        + ";L" + SKIP_CHECKER + ";)Z";
    private static final String CARVE_BLOCK_DESC =
        "(L" + CARVING_CONTEXT + ";L" + CARVER_CONFIG + ";L" + CHUNK_ACCESS
        + ";L" + FUNCTION + ";L" + CARVING_MASK + ";L" + MUTABLE + ";L" + MUTABLE
        + ";L" + AQUIFER + ";L" + MUTABLE_BOOLEAN + ";)Z";
    private static final String SKIP_DESC =
        "(L" + CARVING_CONTEXT + ";DDDI)Z";
    private static final String MUTABLE_SET_DESC = "(JJJ)L" + MUTABLE + ";";

    @Override
    public boolean matches(String internalName) {
        return WORLD_CARVER.equals(internalName) || CAVE_CARVER.equals(internalName)
            || CANYON_CARVER.equals(internalName) || NOISE_GEN.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        boolean changed = false;
        if (WORLD_CARVER.equals(node.name)) {
            changed |= rewriteCarveEllipsoid(node);
            changed |= widenMiddleBlockCalls(node);
            changed |= widenCarveStateContext(node);
        } else if (CAVE_CARVER.equals(node.name) || CANYON_CARVER.equals(node.name)) {
            changed |= widenBlockCalls(node);
        } else if (NOISE_GEN.equals(node.name)) {
            changed |= widenCarverBiomeQuart(node);
        }

        if (!changed) {
            return original;
        }
        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    /**
     * {@code getCarveState} built its aquifer context from the int views of the
     * probe position. Read the long accessors and use the long RealContext ctor so
     * the aquifer substance is sampled at the real column.
     */
    private static boolean widenCarveStateContext(ClassNode node) {
        boolean changed = false;
        for (MethodNode method : node.methods) {
            if (!"getCarveState".equals(method.name)) {
                continue;
            }
            org.objectweb.asm.tree.InsnList list = method.instructions;
            for (org.objectweb.asm.tree.AbstractInsnNode insn = list.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof org.objectweb.asm.tree.MethodInsnNode ctor)
                    || ctor.getOpcode() != org.objectweb.asm.Opcodes.INVOKESPECIAL
                    || !"<init>".equals(ctor.name)
                    || !"com/farlands/g2/runtime/RealContext".equals(ctor.owner)
                    || !"(III)V".equals(ctor.desc)) {
                    continue;
                }
                org.objectweb.asm.tree.AbstractInsnNode gz = ctor.getPrevious();
                org.objectweb.asm.tree.AbstractInsnNode az = gz == null ? null : gz.getPrevious();
                org.objectweb.asm.tree.AbstractInsnNode gy = az == null ? null : az.getPrevious();
                org.objectweb.asm.tree.AbstractInsnNode ay = gy == null ? null : gy.getPrevious();
                org.objectweb.asm.tree.AbstractInsnNode gx = ay == null ? null : ay.getPrevious();
                org.objectweb.asm.tree.AbstractInsnNode ax = gx == null ? null : gx.getPrevious();
                org.objectweb.asm.tree.AbstractInsnNode dup = ax == null ? null : ax.getPrevious();
                org.objectweb.asm.tree.AbstractInsnNode newInsn = dup == null ? null : dup.getPrevious();
                if (!isBlockPosCall(gx, "getX") || !isBlockPosCall(gy, "getY")
                    || !isBlockPosCall(gz, "getZ")
                    || !isALoad3(ax) || !isALoad3(ay) || !isALoad3(az)
                    || dup == null || dup.getOpcode() != org.objectweb.asm.Opcodes.DUP
                    || newInsn == null || newInsn.getOpcode() != org.objectweb.asm.Opcodes.NEW) {
                    continue;
                }
                org.objectweb.asm.tree.InsnList repl = new org.objectweb.asm.tree.InsnList();
                repl.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 3));
                repl.add(new org.objectweb.asm.tree.MethodInsnNode(
                    org.objectweb.asm.Opcodes.INVOKESTATIC,
                    "com/farlands/g2/runtime/RealContext", "of",
                    "(Lnet/minecraft/core/BlockPos;)Lcom/farlands/g2/runtime/RealContext;", false));
                list.insert(newInsn, repl);
                org.objectweb.asm.tree.AbstractInsnNode after = ctor.getNext();
                for (org.objectweb.asm.tree.AbstractInsnNode n = newInsn; n != null && n != after; ) {
                    org.objectweb.asm.tree.AbstractInsnNode nNext = n.getNext();
                    list.remove(n);
                    n = nNext;
                }
                changed = true;
                insn = repl.getLast();
            }
        }
        return changed;
    }

    private static boolean isALoad3(org.objectweb.asm.tree.AbstractInsnNode insn) {
        return insn != null && insn.getOpcode() == org.objectweb.asm.Opcodes.ALOAD
            && ((org.objectweb.asm.tree.VarInsnNode) insn).var == 3;
    }

    private static boolean isBlockPosCall(org.objectweb.asm.tree.AbstractInsnNode insn, String name) {
        return insn instanceof org.objectweb.asm.tree.MethodInsnNode call
            && call.getOpcode() == org.objectweb.asm.Opcodes.INVOKEVIRTUAL
            && "net/minecraft/core/BlockPos".equals(call.owner)
            && name.equals(call.name) && "()I".equals(call.desc);
    }
    @Override
    public String describe(String internalName) {
        return "CarverWidePatch (wide carver columns at 2^31)";
    }

    // ------------------------------------------------------------------
    // carveEllipsoid
    // ------------------------------------------------------------------

    /** Rebuilds the method in the long domain; no-op when already widened. */
    private static boolean rewriteCarveEllipsoid(ClassNode node) {
        MethodNode m = find(node, "carveEllipsoid", CARVE_ELLIPSOID_DESC);
        if (m == null) {
            throw new IllegalStateException(WORLD_CARVER + "#carveEllipsoid not found");
        }
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof MethodInsnNode mi && "getMinBlockXLong".equals(mi.name)) {
                return false; // already patched
            }
        }
        m.instructions = emitCarveEllipsoid();
        m.localVariables = null;
        m.tryCatchBlocks.clear();
        m.maxLocals = 53;
        return true;
    }

    private static InsnList emitCarveEllipsoid() {
        InsnList il = new InsnList();
        LabelNode retFalse = new LabelNode();
        LabelNode optNotUpgrading = new LabelNode();
        LabelNode optDone = new LabelNode();
        LabelNode loopX = new LabelNode();
        LabelNode loopZ = new LabelNode();
        LabelNode loopY = new LabelNode();
        LabelNode nextY = new LabelNode();
        LabelNode nextZ = new LabelNode();
        LabelNode nextX = new LabelNode();
        LabelNode done = new LabelNode();
        LabelNode maskProceed = new LabelNode();

        // ChunkPos chunkPos = chunk.getPos();
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNK_ACCESS, "getPos",
            "()L" + CHUNKPOS + ";", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 52));

        // long minBlockX = chunkPos.getMinBlockXLong(); long minBlockZ = ...
        loadChunk(il, 52);
        il.add(blockLong("getMinBlockXLong"));
        il.add(new VarInsnNode(Opcodes.LSTORE, 18));
        loadChunk(il, 52);
        il.add(blockLong("getMinBlockZLong"));
        il.add(new VarInsnNode(Opcodes.LSTORE, 20));

        // double middleX = minBlockX + 8.0; double middleZ = minBlockZ + 8.0
        il.add(new VarInsnNode(Opcodes.LLOAD, 18));
        il.add(new InsnNode(Opcodes.L2D));
        il.add(new LdcInsnNode(8.0d));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(new VarInsnNode(Opcodes.DSTORE, 22));
        il.add(new VarInsnNode(Opcodes.LLOAD, 20));
        il.add(new InsnNode(Opcodes.L2D));
        il.add(new LdcInsnNode(8.0d));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(new VarInsnNode(Opcodes.DSTORE, 24));

        // double range = 16.0 + hr * 2.0
        il.add(new LdcInsnNode(16.0d));
        il.add(new VarInsnNode(Opcodes.DLOAD, 12));
        il.add(new LdcInsnNode(2.0d));
        il.add(new InsnNode(Opcodes.DMUL));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(new VarInsnNode(Opcodes.DSTORE, 26));

        // if (abs(x - middleX) > range) return false;
        il.add(new VarInsnNode(Opcodes.DLOAD, 6));
        il.add(new VarInsnNode(Opcodes.DLOAD, 22));
        il.add(new InsnNode(Opcodes.DSUB));
        il.add(call(Opcodes.INVOKESTATIC, MATH, "abs", "(D)D"));
        il.add(new VarInsnNode(Opcodes.DLOAD, 26));
        il.add(new InsnNode(Opcodes.DCMPL));
        il.add(new JumpInsnNode(Opcodes.IFGT, retFalse));
        // if (abs(z - middleZ) > range) return false;
        il.add(new VarInsnNode(Opcodes.DLOAD, 10));
        il.add(new VarInsnNode(Opcodes.DLOAD, 24));
        il.add(new InsnNode(Opcodes.DSUB));
        il.add(call(Opcodes.INVOKESTATIC, MATH, "abs", "(D)D"));
        il.add(new VarInsnNode(Opcodes.DLOAD, 26));
        il.add(new InsnNode(Opcodes.DCMPL));
        il.add(new JumpInsnNode(Opcodes.IFGT, retFalse));

        // int x0 = (int) max(floor(x - hr) - minBlockX - 1, 0)
        il.add(new VarInsnNode(Opcodes.DLOAD, 6));
        il.add(new VarInsnNode(Opcodes.DLOAD, 12));
        il.add(new InsnNode(Opcodes.DSUB));
        il.add(call(Opcodes.INVOKESTATIC, MATH, "floor", "(D)D"));
        il.add(new InsnNode(Opcodes.D2L));
        il.add(new VarInsnNode(Opcodes.LLOAD, 18));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new InsnNode(Opcodes.LCONST_0));
        il.add(call(Opcodes.INVOKESTATIC, MATH, "max", "(JJ)J"));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ISTORE, 28));

        // int x1 = (int) min(floor(x + hr) - minBlockX, 15)
        il.add(new VarInsnNode(Opcodes.DLOAD, 6));
        il.add(new VarInsnNode(Opcodes.DLOAD, 12));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(call(Opcodes.INVOKESTATIC, MATH, "floor", "(D)D"));
        il.add(new InsnNode(Opcodes.D2L));
        il.add(new VarInsnNode(Opcodes.LLOAD, 18));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new LdcInsnNode(15L));
        il.add(call(Opcodes.INVOKESTATIC, MATH, "min", "(JJ)J"));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ISTORE, 29));

        // int y0 = max(Mth.floor(y - vr) - 1, ctx.getMinGenY() + 1)
        il.add(new VarInsnNode(Opcodes.DLOAD, 8));
        il.add(new VarInsnNode(Opcodes.DLOAD, 14));
        il.add(new InsnNode(Opcodes.DSUB));
        il.add(call(Opcodes.INVOKESTATIC, MTH, "floor", "(D)I"));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.ISUB));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(call(Opcodes.INVOKEVIRTUAL, CARVING_CONTEXT, "getMinGenY", "()I"));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(call(Opcodes.INVOKESTATIC, MATH, "max", "(II)I"));
        il.add(new VarInsnNode(Opcodes.ISTORE, 30));

        // int opt = chunk.isUpgrading() ? 0 : 7
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(call(Opcodes.INVOKEVIRTUAL, CHUNK_ACCESS, "isUpgrading", "()Z"));
        il.add(new JumpInsnNode(Opcodes.IFEQ, optNotUpgrading));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new JumpInsnNode(Opcodes.GOTO, optDone));
        il.add(optNotUpgrading);
        il.add(new IntInsnNode(Opcodes.BIPUSH, 7));
        il.add(optDone);
        il.add(new VarInsnNode(Opcodes.ISTORE, 31));

        // int y1 = min(Mth.floor(y + vr) + 1,
        //              ctx.getMinGenY() + ctx.getGenDepth() - 1 - opt)
        il.add(new VarInsnNode(Opcodes.DLOAD, 8));
        il.add(new VarInsnNode(Opcodes.DLOAD, 14));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(call(Opcodes.INVOKESTATIC, MTH, "floor", "(D)I"));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(call(Opcodes.INVOKEVIRTUAL, CARVING_CONTEXT, "getMinGenY", "()I"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(call(Opcodes.INVOKEVIRTUAL, CARVING_CONTEXT, "getGenDepth", "()I"));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.ISUB));
        il.add(new VarInsnNode(Opcodes.ILOAD, 31));
        il.add(new InsnNode(Opcodes.ISUB));
        il.add(call(Opcodes.INVOKESTATIC, MATH, "min", "(II)I"));
        il.add(new VarInsnNode(Opcodes.ISTORE, 32));

        // int z0 / z1 (same as x)
        il.add(new VarInsnNode(Opcodes.DLOAD, 10));
        il.add(new VarInsnNode(Opcodes.DLOAD, 12));
        il.add(new InsnNode(Opcodes.DSUB));
        il.add(call(Opcodes.INVOKESTATIC, MATH, "floor", "(D)D"));
        il.add(new InsnNode(Opcodes.D2L));
        il.add(new VarInsnNode(Opcodes.LLOAD, 20));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new InsnNode(Opcodes.LCONST_0));
        il.add(call(Opcodes.INVOKESTATIC, MATH, "max", "(JJ)J"));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ISTORE, 33));
        il.add(new VarInsnNode(Opcodes.DLOAD, 10));
        il.add(new VarInsnNode(Opcodes.DLOAD, 12));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(call(Opcodes.INVOKESTATIC, MATH, "floor", "(D)D"));
        il.add(new InsnNode(Opcodes.D2L));
        il.add(new VarInsnNode(Opcodes.LLOAD, 20));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new LdcInsnNode(15L));
        il.add(call(Opcodes.INVOKESTATIC, MATH, "min", "(JJ)J"));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ISTORE, 34));

        // int carved = 0; MutableBlockPos pos = new ...(); posUp = new ...();
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 35));
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, MUTABLE));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, MUTABLE, "<init>", "()V", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 36));
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, MUTABLE));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, MUTABLE, "<init>", "()V", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 37));

        // for (int lx = x0; lx <= x1; lx++)
        il.add(new VarInsnNode(Opcodes.ILOAD, 28));
        il.add(new VarInsnNode(Opcodes.ISTORE, 38));
        il.add(loopX);
        il.add(new VarInsnNode(Opcodes.ILOAD, 38));
        il.add(new VarInsnNode(Opcodes.ILOAD, 29));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGT, done));
        // long worldX = minBlockX + lx
        il.add(new VarInsnNode(Opcodes.LLOAD, 18));
        il.add(new VarInsnNode(Opcodes.ILOAD, 38));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 39));
        // double dx = ((double) worldX + 0.5 - x) / hr
        il.add(new VarInsnNode(Opcodes.LLOAD, 39));
        il.add(new InsnNode(Opcodes.L2D));
        il.add(new LdcInsnNode(0.5d));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(new VarInsnNode(Opcodes.DLOAD, 6));
        il.add(new InsnNode(Opcodes.DSUB));
        il.add(new VarInsnNode(Opcodes.DLOAD, 12));
        il.add(new InsnNode(Opcodes.DDIV));
        il.add(new VarInsnNode(Opcodes.DSTORE, 41));
        // for (int lz = z0; lz <= z1; lz++)
        il.add(new VarInsnNode(Opcodes.ILOAD, 33));
        il.add(new VarInsnNode(Opcodes.ISTORE, 43));
        il.add(loopZ);
        il.add(new VarInsnNode(Opcodes.ILOAD, 43));
        il.add(new VarInsnNode(Opcodes.ILOAD, 34));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGT, nextX));
        // long worldZ = minBlockZ + lz
        il.add(new VarInsnNode(Opcodes.LLOAD, 20));
        il.add(new VarInsnNode(Opcodes.ILOAD, 43));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 44));
        // double dz = ((double) worldZ + 0.5 - z) / hr
        il.add(new VarInsnNode(Opcodes.LLOAD, 44));
        il.add(new InsnNode(Opcodes.L2D));
        il.add(new LdcInsnNode(0.5d));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(new VarInsnNode(Opcodes.DLOAD, 10));
        il.add(new InsnNode(Opcodes.DSUB));
        il.add(new VarInsnNode(Opcodes.DLOAD, 12));
        il.add(new InsnNode(Opcodes.DDIV));
        il.add(new VarInsnNode(Opcodes.DSTORE, 46));
        // if (dx*dx + dz*dz >= 1.0) continue z
        il.add(new VarInsnNode(Opcodes.DLOAD, 41));
        il.add(new VarInsnNode(Opcodes.DLOAD, 41));
        il.add(new InsnNode(Opcodes.DMUL));
        il.add(new VarInsnNode(Opcodes.DLOAD, 46));
        il.add(new VarInsnNode(Opcodes.DLOAD, 46));
        il.add(new InsnNode(Opcodes.DMUL));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(new InsnNode(Opcodes.DCONST_1));
        il.add(new InsnNode(Opcodes.DCMPG));
        il.add(new JumpInsnNode(Opcodes.IFGE, nextZ));
        // MutableBoolean surface = new MutableBoolean(false)
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, MUTABLE_BOOLEAN));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, MUTABLE_BOOLEAN, "<init>", "(Z)V", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 48));
        // for (int by = y1; by > y0; by--)
        il.add(new VarInsnNode(Opcodes.ILOAD, 32));
        il.add(new VarInsnNode(Opcodes.ISTORE, 49));
        il.add(loopY);
        il.add(new VarInsnNode(Opcodes.ILOAD, 49));
        il.add(new VarInsnNode(Opcodes.ILOAD, 30));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPLE, nextZ));
        // double dy = ((double) by - 0.5 - y) / vr
        il.add(new VarInsnNode(Opcodes.ILOAD, 49));
        il.add(new InsnNode(Opcodes.I2D));
        il.add(new LdcInsnNode(0.5d));
        il.add(new InsnNode(Opcodes.DSUB));
        il.add(new VarInsnNode(Opcodes.DLOAD, 8));
        il.add(new InsnNode(Opcodes.DSUB));
        il.add(new VarInsnNode(Opcodes.DLOAD, 14));
        il.add(new InsnNode(Opcodes.DDIV));
        il.add(new VarInsnNode(Opcodes.DSTORE, 50));
        // if (skip.shouldSkip(ctx, dx, dy, dz, by)) continue
        il.add(new VarInsnNode(Opcodes.ALOAD, 17));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.DLOAD, 41));
        il.add(new VarInsnNode(Opcodes.DLOAD, 50));
        il.add(new VarInsnNode(Opcodes.DLOAD, 46));
        il.add(new VarInsnNode(Opcodes.ILOAD, 49));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, SKIP_CHECKER, "shouldSkip", SKIP_DESC, true));
        il.add(new JumpInsnNode(Opcodes.IFNE, nextY));
        // if (mask.get(lx, by, lz) && !isDebugEnabled(config)) continue
        il.add(new VarInsnNode(Opcodes.ALOAD, 16));
        il.add(new VarInsnNode(Opcodes.ILOAD, 38));
        il.add(new VarInsnNode(Opcodes.ILOAD, 49));
        il.add(new VarInsnNode(Opcodes.ILOAD, 43));
        il.add(call(Opcodes.INVOKEVIRTUAL, CARVING_MASK, "get", "(III)Z"));
        il.add(new JumpInsnNode(Opcodes.IFEQ, maskProceed));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(call(Opcodes.INVOKESTATIC, WORLD_CARVER, "isDebugEnabled", "(L" + CARVER_CONFIG + ";)Z"));
        il.add(new JumpInsnNode(Opcodes.IFEQ, nextY));
        il.add(maskProceed);
        // mask.set(lx, by, lz)
        il.add(new VarInsnNode(Opcodes.ALOAD, 16));
        il.add(new VarInsnNode(Opcodes.ILOAD, 38));
        il.add(new VarInsnNode(Opcodes.ILOAD, 49));
        il.add(new VarInsnNode(Opcodes.ILOAD, 43));
        il.add(call(Opcodes.INVOKEVIRTUAL, CARVING_MASK, "set", "(III)V"));
        // pos.set(worldX, by, worldZ)
        il.add(new VarInsnNode(Opcodes.ALOAD, 36));
        il.add(new VarInsnNode(Opcodes.LLOAD, 39));
        il.add(new VarInsnNode(Opcodes.ILOAD, 49));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.LLOAD, 44));
        il.add(call(Opcodes.INVOKEVIRTUAL, MUTABLE, "set", MUTABLE_SET_DESC));
        il.add(new InsnNode(Opcodes.POP));
        // carved |= carveBlock(ctx, config, chunk, biomeFn, mask, pos, posUp, aquifer, surface)
        il.add(new VarInsnNode(Opcodes.ILOAD, 35));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 16));
        il.add(new VarInsnNode(Opcodes.ALOAD, 36));
        il.add(new VarInsnNode(Opcodes.ALOAD, 37));
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new VarInsnNode(Opcodes.ALOAD, 48));
        il.add(call(Opcodes.INVOKEVIRTUAL, WORLD_CARVER, "carveBlock", CARVE_BLOCK_DESC));
        il.add(new InsnNode(Opcodes.IOR));
        il.add(new VarInsnNode(Opcodes.ISTORE, 35));
        // by--
        il.add(nextY);
        il.add(new org.objectweb.asm.tree.IincInsnNode(49, -1));
        il.add(new JumpInsnNode(Opcodes.GOTO, loopY));
        // lz++
        il.add(nextZ);
        il.add(new org.objectweb.asm.tree.IincInsnNode(43, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, loopZ));
        // lx++
        il.add(nextX);
        il.add(new org.objectweb.asm.tree.IincInsnNode(38, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, loopX));
        // return carved
        il.add(done);
        il.add(new VarInsnNode(Opcodes.ILOAD, 35));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(retFalse);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    // ------------------------------------------------------------------
    // targeted call widening
    // ------------------------------------------------------------------

    /** {@code canReach}: middle block X/Z through the long accessors. */
    private static boolean widenMiddleBlockCalls(ClassNode node) {
        boolean changed = false;
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode mi && mi.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && CHUNKPOS.equals(mi.owner) && mi.desc.equals("()I")
                        && (mi.name.equals("getMiddleBlockX") || mi.name.equals("getMiddleBlockZ"))) {
                    AbstractInsnNode next = mi.getNext();
                    if (next instanceof InsnNode i2d && i2d.getOpcode() == Opcodes.I2D) {
                        mi.name = mi.name + "Long";
                        mi.desc = "()J";
                        m.instructions.set(i2d, new InsnNode(Opcodes.L2D));
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    /** {@code Cave/Canyon.carve}: start columns through the long accessors. */
    private static boolean widenBlockCalls(ClassNode node) {
        boolean changed = false;
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode mi && mi.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && CHUNKPOS.equals(mi.owner) && mi.desc.equals("(I)I")
                        && (mi.name.equals("getBlockX") || mi.name.equals("getBlockZ"))) {
                    AbstractInsnNode next = mi.getNext();
                    if (next instanceof InsnNode i2d && i2d.getOpcode() == Opcodes.I2D) {
                        mi.name = mi.name + "Long";
                        mi.desc = "(I)J";
                        m.instructions.set(i2d, new InsnNode(Opcodes.L2D));
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    /**
     * {@code lambda$applyCarvers$2}: {@code QuartPos.fromBlock(getMinBlockX())}
     * folded into the long shift {@code getMinBlockXLong() >> 2}.
     */
    private static boolean widenCarverBiomeQuart(ClassNode node) {
        boolean changed = false;
        for (MethodNode m : node.methods) {
            java.util.List<AbstractInsnNode> matches = new java.util.ArrayList<>();
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof MethodInsnNode mi && mi.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && CHUNKPOS.equals(mi.owner) && mi.desc.equals("()I")
                        && (mi.name.equals("getMinBlockX") || mi.name.equals("getMinBlockZ"))) {
                    AbstractInsnNode next = mi.getNext();
                    if (next instanceof MethodInsnNode q && q.getOpcode() == Opcodes.INVOKESTATIC
                            && QUART_POS.equals(q.owner) && "fromBlock".equals(q.name)
                            && q.desc.equals("(I)I")) {
                        matches.add(mi);
                    }
                }
            }
            for (AbstractInsnNode insn : matches) {
                MethodInsnNode mi = (MethodInsnNode) insn;
                MethodInsnNode q = (MethodInsnNode) mi.getNext();
                MethodInsnNode wide = new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNKPOS,
                    mi.name + "Long", "()J", false);
                m.instructions.set(mi, wide);
                InsnList shift = new InsnList();
                shift.add(new IntInsnNode(Opcodes.BIPUSH, 2));
                shift.add(new InsnNode(Opcodes.LSHR));
                shift.add(new InsnNode(Opcodes.L2I));
                m.instructions.insert(wide, shift);
                m.instructions.remove(q);
                changed = true;
            }
        }
        return changed;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static void loadChunk(InsnList il, int slot) {
        il.add(new VarInsnNode(Opcodes.ALOAD, slot));
    }

    private static MethodInsnNode blockLong(String name) {
        return new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNKPOS, name, "()J", false);
    }

    private static MethodInsnNode call(int opcode, String owner, String name, String desc) {
        boolean itf = opcode == Opcodes.INVOKEINTERFACE;
        return new MethodInsnNode(opcode, owner, name, desc, itf);
    }

    private static MethodNode find(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) {
            if (name.equals(m.name) && desc.equals(m.desc)) {
                return m;
            }
        }
        return null;
    }
}
