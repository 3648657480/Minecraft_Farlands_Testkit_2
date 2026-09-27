package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Consumer widening (W5): makes {@code OreFeature} fully long end-to-end.
 *
 * <p>Vanilla computes the vein-search bounds as int
 * ({@code minX = pos.getX() - Mth.ceil(f) - i}) and calls
 * {@code doPlace(... int x5)}. At the 2^31 block boundary the int view
 * wraps, the search runs in the wrong/huge range and the worker spins on
 * out-of-region reads (Watchdog STUCK). This patch rebuilds both methods in
 * the long domain:</p>
 *
 * <ul>
 *   <li>{@code place}: origin is read through {@code getLongX/getLongZ}; the
 *       min-X / min-Z bounds and the X/Z span are computed and looped as
 *       long; the call to {@code doPlace} now passes five longs.</li>
 *   <li>{@code doPlace}: the five int bounds become long (descriptor
 *       {@code ...DDDDDDJJJJJ}), the clamp/index/section arithmetic runs in
 *       long, and the mutable position is written with the new
 *       {@code MutableBlockPos.set(long,long,long)}.</li>
 * </ul>
 *
 * <p>Y is carried as long too (the 5th-widened parameter), but stays a
 * world-height value. At normal coordinates every long equals the int it
 * replaced, so behavior is bit-identical.</p>
 */
public final class OreFeaturePatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/levelgen/feature/OreFeature";
    private static final String PLACE_DESC =
        "(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z";
    private static final String DO_PLACE_OLD =
        "(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/util/RandomSource;"
        + "Lnet/minecraft/world/level/levelgen/feature/configurations/OreConfiguration;"
        + "DDDDDDIIIII)Z";
    static final String DO_PLACE_NEW =
        "(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/util/RandomSource;"
        + "Lnet/minecraft/world/level/levelgen/feature/configurations/OreConfiguration;"
        + "DDDDDDJJJJJ)Z";

    private static final String WGL = "net/minecraft/world/level/WorldGenLevel";
    private static final String RS = "net/minecraft/util/RandomSource";
    private static final String ORE_CFG =
        "net/minecraft/world/level/levelgen/feature/configurations/OreConfiguration";
    private static final String TARGET_BLOCK_STATE =
        "net/minecraft/world/level/levelgen/feature/configurations/OreConfiguration$TargetBlockState";
    private static final String BLOCKPOS = "net/minecraft/core/BlockPos";
    private static final String MUTABLE = "net/minecraft/core/BlockPos$MutableBlockPos";
    private static final String SECTION = "net/minecraft/world/level/chunk/LevelChunkSection";
    private static final String BSACCESS = "net/minecraft/world/level/chunk/BulkSectionAccess";
    private static final String BLOCKSTATE = "net/minecraft/world/level/block/state/BlockState";
    private static final String MTH = "net/minecraft/util/Mth";
    private static final String BITSET = "java/util/BitSet";
    private static final String HTYPES = "net/minecraft/world/level/levelgen/Heightmap$Types";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        MethodNode place = find(node, "place", PLACE_DESC);
        MethodNode doPlace = find(node, "doPlace", DO_PLACE_OLD);
        if (place == null || doPlace == null) {
            throw new IllegalStateException(TARGET + ": place/doPlace signature not found");
        }

        // The lambda (access::getBlockState) created for canPlaceOre lives in
        // the region we rebuild; keep a clone of the bootstrap so we can emit
        // the identical invokedynamic again.
        InvokeDynamicInsnNode indy = null;
        for (AbstractInsnNode insn : doPlace.instructions) {
            if (insn instanceof InvokeDynamicInsnNode id) {
                indy = id;
                break;
            }
        }
        if (indy == null) {
            throw new IllegalStateException(TARGET + "#doPlace: canPlaceOre lambda not found");
        }
        InvokeDynamicInsnNode indyClone = (InvokeDynamicInsnNode) indy.clone(new HashMap<>());

        // The fill/link loops (between the double[] allocation and the
        // BulkSectionAccess construction) only use doubles and ints, so they
        // are copied verbatim with a +5 local-slot remap.
        IntInsnNode newArray = null;
        TypeInsnNode newAccess = null;
        for (AbstractInsnNode insn = doPlace.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof IntInsnNode ii && ii.getOpcode() == Opcodes.NEWARRAY
                && ii.operand == Opcodes.T_DOUBLE) {
                newArray = ii;
            } else if (insn instanceof TypeInsnNode t && t.getOpcode() == Opcodes.NEW
                && BSACCESS.equals(t.desc)) {
                newAccess = t;
                break;
            }
        }
        if (newArray == null || newAccess == null) {
            throw new IllegalStateException(TARGET + "#doPlace: body markers not found");
        }
        AbstractInsnNode copyStart = newArray.getNext().getNext();
        AbstractInsnNode copyEnd = newAccess;

        InsnList dp = new InsnList();
        ArrayList<org.objectweb.asm.tree.TryCatchBlockNode> tcbs = new ArrayList<>();
        emitDoPlacePreamble(dp);
        appendCloned(dp, copyStart, copyEnd);
        emitDoPlacePlacement(dp, indyClone, tcbs);

        doPlace.instructions = dp;
        doPlace.desc = DO_PLACE_NEW;
        doPlace.localVariables = null;
        doPlace.tryCatchBlocks = tcbs;

        InsnList pl = new InsnList();
        /** try/finally around BulkSectionAccess is rebuilt during placement. */
        emitPlace(pl);
        place.instructions = pl;
        place.localVariables = null;
        place.tryCatchBlocks.clear();

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    // ------------------------------------------------------------------
    // place
    // ------------------------------------------------------------------

    private static void emitPlace(InsnList il) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(call(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/levelgen/feature/FeaturePlaceContext", "random", "()Lnet/minecraft/util/RandomSource;"));
        il.add(new VarInsnNode(Opcodes.ASTORE, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(call(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/levelgen/feature/FeaturePlaceContext", "origin", "()Lnet/minecraft/core/BlockPos;"));
        il.add(new VarInsnNode(Opcodes.ASTORE, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(call(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/levelgen/feature/FeaturePlaceContext", "level", "()Lnet/minecraft/world/level/WorldGenLevel;"));
        il.add(new VarInsnNode(Opcodes.ASTORE, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(call(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/levelgen/feature/FeaturePlaceContext", "config", "()Lnet/minecraft/world/level/levelgen/feature/configurations/FeatureConfiguration;"));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, ORE_CFG));
        il.add(new VarInsnNode(Opcodes.ASTORE, 5));

        // f = random.nextFloat() * PI
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(call(Opcodes.INVOKEINTERFACE, RS, "nextFloat", "()F"));
        il.add(new LdcInsnNode((float) Math.PI));
        il.add(new InsnNode(Opcodes.FMUL));
        il.add(new VarInsnNode(Opcodes.FSTORE, 6));

        // g = (float) config.size / 8.0f
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, ORE_CFG, "size", "I"));
        il.add(new InsnNode(Opcodes.I2F));
        il.add(new LdcInsnNode(8.0f));
        il.add(new InsnNode(Opcodes.FDIV));
        il.add(new VarInsnNode(Opcodes.FSTORE, 7));

        // i = Mth.ceil(((size/16f)*2f + 1f)/2f)
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, ORE_CFG, "size", "I"));
        il.add(new InsnNode(Opcodes.I2F));
        il.add(new LdcInsnNode(16.0f));
        il.add(new InsnNode(Opcodes.FDIV));
        il.add(new InsnNode(Opcodes.FCONST_2));
        il.add(new InsnNode(Opcodes.FMUL));
        il.add(new InsnNode(Opcodes.FCONST_1));
        il.add(new InsnNode(Opcodes.FADD));
        il.add(new InsnNode(Opcodes.FCONST_2));
        il.add(new InsnNode(Opcodes.FDIV));
        il.add(call(Opcodes.INVOKESTATIC, MTH, "ceil", "(F)I"));
        il.add(new VarInsnNode(Opcodes.ISTORE, 8));

        // d = getLongX() + Math.sin(f) * g
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(call(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongX", "()J"));
        il.add(new InsnNode(Opcodes.L2D));
        sinF(il);
        il.add(loadF(7));
        il.add(new InsnNode(Opcodes.F2D));
        il.add(new InsnNode(Opcodes.DMUL));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(new VarInsnNode(Opcodes.DSTORE, 9));

        // e = getLongX() - Math.sin(f) * g
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(call(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongX", "()J"));
        il.add(new InsnNode(Opcodes.L2D));
        sinF(il);
        il.add(loadF(7));
        il.add(new InsnNode(Opcodes.F2D));
        il.add(new InsnNode(Opcodes.DMUL));
        il.add(new InsnNode(Opcodes.DSUB));
        il.add(new VarInsnNode(Opcodes.DSTORE, 11));

        // h = getLongZ() + Math.cos(f) * g
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(call(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongZ", "()J"));
        il.add(new InsnNode(Opcodes.L2D));
        cosF(il);
        il.add(loadF(7));
        il.add(new InsnNode(Opcodes.F2D));
        il.add(new InsnNode(Opcodes.DMUL));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(new VarInsnNode(Opcodes.DSTORE, 13));

        // j = getLongZ() - Math.cos(f) * g
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(call(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongZ", "()J"));
        il.add(new InsnNode(Opcodes.L2D));
        cosF(il);
        il.add(loadF(7));
        il.add(new InsnNode(Opcodes.F2D));
        il.add(new InsnNode(Opcodes.DMUL));
        il.add(new InsnNode(Opcodes.DSUB));
        il.add(new VarInsnNode(Opcodes.DSTORE, 15));

        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new VarInsnNode(Opcodes.ISTORE, 17));

        // k = getY() + random.nextInt(3) - 2
        yRandom(il);
        il.add(new VarInsnNode(Opcodes.DSTORE, 18));
        // l = getY() + random.nextInt(3) - 2
        yRandom(il);
        il.add(new VarInsnNode(Opcodes.DSTORE, 20));

        // m = getLongX() - Mth.ceil(g) - i
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(call(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongX", "()J"));
        il.add(loadF(7));
        il.add(call(Opcodes.INVOKESTATIC, MTH, "ceil", "(F)I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new VarInsnNode(Opcodes.LSTORE, 22));

        // n = getY() - 2 - i
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(call(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getY", "()I"));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new InsnNode(Opcodes.ISUB));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new InsnNode(Opcodes.ISUB));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.LSTORE, 24));

        // o = getLongZ() - Mth.ceil(g) - i
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(call(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongZ", "()J"));
        il.add(loadF(7));
        il.add(call(Opcodes.INVOKESTATIC, MTH, "ceil", "(F)I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new VarInsnNode(Opcodes.LSTORE, 26));

        // p = 2L * (Mth.ceil(g) + i)
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(loadF(7));
        il.add(call(Opcodes.INVOKESTATIC, MTH, "ceil", "(F)I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new VarInsnNode(Opcodes.LSTORE, 28));

        // q = 2L * (2L + i)
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new VarInsnNode(Opcodes.LSTORE, 30));

        // r = m
        il.add(new VarInsnNode(Opcodes.LLOAD, 22));
        il.add(new VarInsnNode(Opcodes.LSTORE, 32));

        LabelNode loop = new LabelNode();
        LabelNode inner = new LabelNode();
        LabelNode incR = new LabelNode();
        LabelNode incS = new LabelNode();
        LabelNode retFalse = new LabelNode();

        il.add(loop);
        // if (r > m + p) return false
        il.add(new VarInsnNode(Opcodes.LLOAD, 32));
        il.add(new VarInsnNode(Opcodes.LLOAD, 22));
        il.add(new VarInsnNode(Opcodes.LLOAD, 28));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFGT, retFalse));
        // s = o
        il.add(new VarInsnNode(Opcodes.LLOAD, 26));
        il.add(new VarInsnNode(Opcodes.LSTORE, 34));
        il.add(inner);
        // if (s > o + p) { r++; continue; }
        il.add(new VarInsnNode(Opcodes.LLOAD, 34));
        il.add(new VarInsnNode(Opcodes.LLOAD, 26));
        il.add(new VarInsnNode(Opcodes.LLOAD, 28));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFGT, incR));
        // if (n <= level.getHeight(OCEAN_FLOOR_WG, (int) r, (int) s)) return doPlace(...)
        il.add(new VarInsnNode(Opcodes.LLOAD, 24));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, HTYPES, "OCEAN_FLOOR_WG", "L" + HTYPES + ";"));
        il.add(new VarInsnNode(Opcodes.LLOAD, 32));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.LLOAD, 34));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(call(Opcodes.INVOKEINTERFACE, WGL, "getHeight", "(L" + HTYPES + ";II)I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFGT, incS));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new VarInsnNode(Opcodes.DLOAD, 9));
        il.add(new VarInsnNode(Opcodes.DLOAD, 11));
        il.add(new VarInsnNode(Opcodes.DLOAD, 13));
        il.add(new VarInsnNode(Opcodes.DLOAD, 15));
        il.add(new VarInsnNode(Opcodes.DLOAD, 18));
        il.add(new VarInsnNode(Opcodes.DLOAD, 20));
        il.add(new VarInsnNode(Opcodes.LLOAD, 22));
        il.add(new VarInsnNode(Opcodes.LLOAD, 24));
        il.add(new VarInsnNode(Opcodes.LLOAD, 26));
        il.add(new VarInsnNode(Opcodes.LLOAD, 28));
        il.add(new VarInsnNode(Opcodes.LLOAD, 30));
        il.add(call(Opcodes.INVOKEVIRTUAL, TARGET, "doPlace", DO_PLACE_NEW));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(incS);
        il.add(new VarInsnNode(Opcodes.LLOAD, 34));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 34));
        il.add(gotoLabel(inner));
        il.add(incR);
        il.add(new VarInsnNode(Opcodes.LLOAD, 32));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 32));
        il.add(gotoLabel(loop));
        il.add(retFalse);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));
    }

    private static void sinF(InsnList il) {
        il.add(loadF(6));
        il.add(new InsnNode(Opcodes.F2D));
        il.add(call(Opcodes.INVOKESTATIC, "java/lang/Math", "sin", "(D)D"));
    }

    private static void cosF(InsnList il) {
        il.add(loadF(6));
        il.add(new InsnNode(Opcodes.F2D));
        il.add(call(Opcodes.INVOKESTATIC, "java/lang/Math", "cos", "(D)D"));
    }

    private static void yRandom(InsnList il) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(call(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getY", "()I"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new InsnNode(Opcodes.ICONST_3));
        il.add(call(Opcodes.INVOKEINTERFACE, RS, "nextInt", "(I)I"));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new InsnNode(Opcodes.ISUB));
        il.add(new InsnNode(Opcodes.I2D));
    }

    // ------------------------------------------------------------------
    // doPlace
    // ------------------------------------------------------------------

    private static void emitDoPlacePreamble(InsnList il) {
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 26)); // count
        // new BitSet((int)(xspan * yspan * xspan))
        il.add(new TypeInsnNode(Opcodes.NEW, BITSET));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.LLOAD, 22));
        il.add(new VarInsnNode(Opcodes.LLOAD, 24));
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new VarInsnNode(Opcodes.LLOAD, 22));
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(call(Opcodes.INVOKESPECIAL, BITSET, "<init>", "(I)V"));
        il.add(new VarInsnNode(Opcodes.ASTORE, 27)); // bitset
        // new MutableBlockPos()
        il.add(new TypeInsnNode(Opcodes.NEW, MUTABLE));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(call(Opcodes.INVOKESPECIAL, MUTABLE, "<init>", "()V"));
        il.add(new VarInsnNode(Opcodes.ASTORE, 28)); // pos
        // int size = config.size
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, ORE_CFG, "size", "I"));
        il.add(new VarInsnNode(Opcodes.ISTORE, 29));
        // double[] ds = new double[size * 4]
        il.add(new VarInsnNode(Opcodes.ILOAD, 29));
        il.add(new InsnNode(Opcodes.ICONST_4));
        il.add(new InsnNode(Opcodes.IMUL));
        il.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_DOUBLE));
        il.add(new VarInsnNode(Opcodes.ASTORE, 30));
    }

    private static void emitDoPlacePlacement(InsnList il, InvokeDynamicInsnNode indy,
            java.util.List<org.objectweb.asm.tree.TryCatchBlockNode> tcbs) {
        // access = new BulkSectionAccess(level)
        il.add(new TypeInsnNode(Opcodes.NEW, BSACCESS));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(call(Opcodes.INVOKESPECIAL, BSACCESS, "<init>", "(Lnet/minecraft/world/level/LevelAccessor;)V"));
        il.add(new VarInsnNode(Opcodes.ASTORE, 44)); // access

        LabelNode start = new LabelNode();
        LabelNode mainCond = new LabelNode();
        LabelNode mainInc = new LabelNode();
        LabelNode ok = new LabelNode();
        LabelNode kCond = new LabelNode();
        LabelNode kInc = new LabelNode();
        LabelNode lCond = new LabelNode();
        LabelNode lInc = new LabelNode();
        LabelNode iCond = new LabelNode();
        LabelNode iInc = new LabelNode();
        LabelNode iterCond = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode done = new LabelNode();
        LabelNode handler = new LabelNode();
        LabelNode closeStart = new LabelNode();
        LabelNode closeEnd = new LabelNode();
        LabelNode handler2 = new LabelNode();
        LabelNode after = new LabelNode();
        LabelNode ret0 = new LabelNode();

        il.add(start);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 31)); // m = 0
        il.add(mainCond);
        il.add(new VarInsnNode(Opcodes.ILOAD, 31));
        il.add(new VarInsnNode(Opcodes.ILOAD, 29));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGE, end));
        // R = ds[m*4+3]
        il.add(arrayLoadDouble(30, 31, 3));
        il.add(new VarInsnNode(Opcodes.DSTORE, 52));
        il.add(new VarInsnNode(Opcodes.DLOAD, 52));
        il.add(new InsnNode(Opcodes.DCONST_0));
        il.add(new InsnNode(Opcodes.DCMPG));
        il.add(new JumpInsnNode(Opcodes.IFGE, ok));
        il.add(gotoLabel(mainInc));
        il.add(ok);
        // X, Y, Z = ds[m*4+0..2]
        il.add(arrayLoadDouble(30, 31, 0));
        il.add(new VarInsnNode(Opcodes.DSTORE, 46));
        il.add(arrayLoadDouble(30, 31, 1));
        il.add(new VarInsnNode(Opcodes.DSTORE, 48));
        il.add(arrayLoadDouble(30, 31, 2));
        il.add(new VarInsnNode(Opcodes.DSTORE, 50));

        clamp(il, 46, 52, 16, 60); // x0 = max(floor(X-R), minX)
        clamp(il, 48, 52, 18, 62); // y0
        clamp(il, 50, 52, 20, 64); // z0
        clampMax(il, 46, 52, 60, 66); // x1 = max(floor(X+R), x0)
        clampMax(il, 48, 52, 62, 68);
        clampMax(il, 50, 52, 64, 70);

        il.add(new VarInsnNode(Opcodes.LLOAD, 60));
        il.add(new VarInsnNode(Opcodes.LSTORE, 72)); // k4 = x0
        il.add(kCond);
        il.add(new VarInsnNode(Opcodes.LLOAD, 72));
        il.add(new VarInsnNode(Opcodes.LLOAD, 66));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFGT, mainInc));
        // d9 = ((double)k4 + 0.5 - X)/R
        il.add(new VarInsnNode(Opcodes.LLOAD, 72));
        il.add(new InsnNode(Opcodes.L2D));
        il.add(new LdcInsnNode(0.5d));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(new VarInsnNode(Opcodes.DLOAD, 46));
        il.add(new InsnNode(Opcodes.DSUB));
        il.add(new VarInsnNode(Opcodes.DLOAD, 52));
        il.add(new InsnNode(Opcodes.DDIV));
        il.add(new VarInsnNode(Opcodes.DSTORE, 54));
        // if (d9*d9 >= 1.0) continue k
        il.add(new VarInsnNode(Opcodes.DLOAD, 54));
        il.add(new VarInsnNode(Opcodes.DLOAD, 54));
        il.add(new InsnNode(Opcodes.DMUL));
        il.add(new InsnNode(Opcodes.DCONST_1));
        il.add(new InsnNode(Opcodes.DCMPG));
        il.add(new JumpInsnNode(Opcodes.IFGE, kInc));

        il.add(new VarInsnNode(Opcodes.LLOAD, 62));
        il.add(new VarInsnNode(Opcodes.LSTORE, 74)); // l4 = y0
        il.add(lCond);
        il.add(new VarInsnNode(Opcodes.LLOAD, 74));
        il.add(new VarInsnNode(Opcodes.LLOAD, 68));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFGT, kInc));
        // d10 = ((double)l4 + 0.5 - Y)/R
        il.add(new VarInsnNode(Opcodes.LLOAD, 74));
        il.add(new InsnNode(Opcodes.L2D));
        il.add(new LdcInsnNode(0.5d));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(new VarInsnNode(Opcodes.DLOAD, 48));
        il.add(new InsnNode(Opcodes.DSUB));
        il.add(new VarInsnNode(Opcodes.DLOAD, 52));
        il.add(new InsnNode(Opcodes.DDIV));
        il.add(new VarInsnNode(Opcodes.DSTORE, 56));
        // if (d9^2 + d10^2 >= 1.0) continue l
        il.add(new VarInsnNode(Opcodes.DLOAD, 54));
        il.add(new VarInsnNode(Opcodes.DLOAD, 54));
        il.add(new InsnNode(Opcodes.DMUL));
        il.add(new VarInsnNode(Opcodes.DLOAD, 56));
        il.add(new VarInsnNode(Opcodes.DLOAD, 56));
        il.add(new InsnNode(Opcodes.DMUL));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(new InsnNode(Opcodes.DCONST_1));
        il.add(new InsnNode(Opcodes.DCMPG));
        il.add(new JumpInsnNode(Opcodes.IFGE, lInc));

        il.add(new VarInsnNode(Opcodes.LLOAD, 64));
        il.add(new VarInsnNode(Opcodes.LSTORE, 76)); // i5 = z0
        il.add(iCond);
        il.add(new VarInsnNode(Opcodes.LLOAD, 76));
        il.add(new VarInsnNode(Opcodes.LLOAD, 70));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFGT, lInc));
        // d11
        il.add(new VarInsnNode(Opcodes.LLOAD, 76));
        il.add(new InsnNode(Opcodes.L2D));
        il.add(new LdcInsnNode(0.5d));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(new VarInsnNode(Opcodes.DLOAD, 50));
        il.add(new InsnNode(Opcodes.DSUB));
        il.add(new VarInsnNode(Opcodes.DLOAD, 52));
        il.add(new InsnNode(Opcodes.DDIV));
        il.add(new VarInsnNode(Opcodes.DSTORE, 58));
        // if (d9^2+d10^2+d11^2 >= 1.0) continue i
        il.add(new VarInsnNode(Opcodes.DLOAD, 54));
        il.add(new VarInsnNode(Opcodes.DLOAD, 54));
        il.add(new InsnNode(Opcodes.DMUL));
        il.add(new VarInsnNode(Opcodes.DLOAD, 56));
        il.add(new VarInsnNode(Opcodes.DLOAD, 56));
        il.add(new InsnNode(Opcodes.DMUL));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(new VarInsnNode(Opcodes.DLOAD, 58));
        il.add(new VarInsnNode(Opcodes.DLOAD, 58));
        il.add(new InsnNode(Opcodes.DMUL));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(new InsnNode(Opcodes.DCONST_1));
        il.add(new InsnNode(Opcodes.DCMPG));
        il.add(new JumpInsnNode(Opcodes.IFGE, iInc));
        // if (level.isOutsideBuildHeight((int) l4)) continue
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.LLOAD, 74));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(call(Opcodes.INVOKEINTERFACE, WGL, "isOutsideBuildHeight", "(I)Z"));
        il.add(new JumpInsnNode(Opcodes.IFNE, iInc));
        // idx = (int)((k4-minX) + (l4-minY)*xspan + (i5-minZ)*xspan*yspan)
        il.add(new VarInsnNode(Opcodes.LLOAD, 72));
        il.add(new VarInsnNode(Opcodes.LLOAD, 16));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new VarInsnNode(Opcodes.LLOAD, 74));
        il.add(new VarInsnNode(Opcodes.LLOAD, 18));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new VarInsnNode(Opcodes.LLOAD, 22));
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LLOAD, 76));
        il.add(new VarInsnNode(Opcodes.LLOAD, 20));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new VarInsnNode(Opcodes.LLOAD, 22));
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new VarInsnNode(Opcodes.LLOAD, 24));
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ISTORE, 78));
        // if (bitset.get(idx)) continue
        il.add(new VarInsnNode(Opcodes.ALOAD, 27));
        il.add(new VarInsnNode(Opcodes.ILOAD, 78));
        il.add(call(Opcodes.INVOKEVIRTUAL, BITSET, "get", "(I)Z"));
        il.add(new JumpInsnNode(Opcodes.IFNE, iInc));
        il.add(new VarInsnNode(Opcodes.ALOAD, 27));
        il.add(new VarInsnNode(Opcodes.ILOAD, 78));
        il.add(call(Opcodes.INVOKEVIRTUAL, BITSET, "set", "(I)V"));
        // pos.set(k4, l4, i5)
        il.add(new VarInsnNode(Opcodes.ALOAD, 28));
        il.add(new VarInsnNode(Opcodes.LLOAD, 72));
        il.add(new VarInsnNode(Opcodes.LLOAD, 74));
        il.add(new VarInsnNode(Opcodes.LLOAD, 76));
        il.add(call(Opcodes.INVOKEVIRTUAL, MUTABLE, "set", "(JJJ)L" + MUTABLE + ";"));
        il.add(new InsnNode(Opcodes.POP));
        // if (!level.ensureCanWrite(pos)) continue
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 28));
        il.add(call(Opcodes.INVOKEINTERFACE, WGL, "ensureCanWrite", "(L" + BLOCKPOS + ";)Z"));
        il.add(new JumpInsnNode(Opcodes.IFEQ, iInc));
        // section = access.getSection(pos)
        il.add(new VarInsnNode(Opcodes.ALOAD, 44));
        il.add(new VarInsnNode(Opcodes.ALOAD, 28));
        il.add(call(Opcodes.INVOKEVIRTUAL, BSACCESS, "getSection", "(L" + BLOCKPOS + ";)L" + SECTION + ";"));
        il.add(new VarInsnNode(Opcodes.ASTORE, 79));
        il.add(new VarInsnNode(Opcodes.ALOAD, 79));
        il.add(new JumpInsnNode(Opcodes.IFNULL, iInc));
        // rx/ry/rz = (int)(coord & 15)
        sectionRelative(il, 72, 80);
        sectionRelative(il, 74, 81);
        sectionRelative(il, 76, 82);
        // state = section.getBlockState(rx,ry,rz)
        il.add(new VarInsnNode(Opcodes.ALOAD, 79));
        il.add(new VarInsnNode(Opcodes.ILOAD, 80));
        il.add(new VarInsnNode(Opcodes.ILOAD, 81));
        il.add(new VarInsnNode(Opcodes.ILOAD, 82));
        il.add(call(Opcodes.INVOKEVIRTUAL, SECTION, "getBlockState", "(III)L" + BLOCKSTATE + ";"));
        il.add(new VarInsnNode(Opcodes.ASTORE, 83));
        // iterator = config.targetStates.iterator()
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, ORE_CFG, "targetStates", "Ljava/util/List;"));
        il.add(call(Opcodes.INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;"));
        il.add(new VarInsnNode(Opcodes.ASTORE, 84));
        il.add(iterCond);
        il.add(new VarInsnNode(Opcodes.ALOAD, 84));
        il.add(call(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z"));
        il.add(new JumpInsnNode(Opcodes.IFEQ, iInc));
        il.add(new VarInsnNode(Opcodes.ALOAD, 84));
        il.add(call(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;"));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, TARGET_BLOCK_STATE));
        il.add(new VarInsnNode(Opcodes.ASTORE, 85));
        // canPlaceOre(state, access::getBlockState, random, config, target, pos)
        il.add(new VarInsnNode(Opcodes.ALOAD, 83));
        il.add(new VarInsnNode(Opcodes.ALOAD, 44));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(call(Opcodes.INVOKESTATIC, "java/util/Objects", "requireNonNull", "(Ljava/lang/Object;)Ljava/lang/Object;"));
        il.add(new InsnNode(Opcodes.POP));
        il.add(indy);
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 85));
        il.add(new VarInsnNode(Opcodes.ALOAD, 28));
        il.add(call(Opcodes.INVOKESTATIC, TARGET, "canPlaceOre",
            "(L" + BLOCKSTATE + ";Ljava/util/function/Function;L" + RS + ";L" + ORE_CFG + ";L" + TARGET_BLOCK_STATE + ";L" + MUTABLE + ";)Z"));
        il.add(new JumpInsnNode(Opcodes.IFEQ, iterCond));
        // section.setBlockState(rx,ry,rz, target.state, false)
        il.add(new VarInsnNode(Opcodes.ALOAD, 79));
        il.add(new VarInsnNode(Opcodes.ILOAD, 80));
        il.add(new VarInsnNode(Opcodes.ILOAD, 81));
        il.add(new VarInsnNode(Opcodes.ILOAD, 82));
        il.add(new VarInsnNode(Opcodes.ALOAD, 85));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET_BLOCK_STATE, "state", "L" + BLOCKSTATE + ";"));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(call(Opcodes.INVOKEVIRTUAL, SECTION, "setBlockState", "(III" + "L" + BLOCKSTATE + ";Z)L" + BLOCKSTATE + ";"));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new IincInsnNode(26, 1)); // count++
        il.add(gotoLabel(iInc));

        il.add(iInc);
        il.add(new VarInsnNode(Opcodes.LLOAD, 76));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 76));
        il.add(gotoLabel(iCond));
        il.add(lInc);
        il.add(new VarInsnNode(Opcodes.LLOAD, 74));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 74));
        il.add(gotoLabel(lCond));
        il.add(kInc);
        il.add(new VarInsnNode(Opcodes.LLOAD, 72));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 72));
        il.add(gotoLabel(kCond));
        il.add(mainInc);
        il.add(new IincInsnNode(31, 1));
        il.add(gotoLabel(mainCond));

        // try/finally around the BulkSectionAccess
        il.add(end);
        il.add(new VarInsnNode(Opcodes.ALOAD, 44));
        il.add(call(Opcodes.INVOKEVIRTUAL, BSACCESS, "close", "()V"));
        il.add(gotoLabel(done));
        il.add(handler);
        il.add(new VarInsnNode(Opcodes.ASTORE, 86)); // last
        il.add(closeStart);
        il.add(new VarInsnNode(Opcodes.ALOAD, 44));
        il.add(call(Opcodes.INVOKEVIRTUAL, BSACCESS, "close", "()V"));
        il.add(closeEnd);
        il.add(gotoLabel(after));
        il.add(handler2);
        il.add(new VarInsnNode(Opcodes.ASTORE, 87)); // suppressed
        il.add(new VarInsnNode(Opcodes.ALOAD, 86));
        il.add(new VarInsnNode(Opcodes.ALOAD, 87));
        il.add(call(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable", "addSuppressed", "(Ljava/lang/Throwable;)V"));
        il.add(after);
        il.add(new VarInsnNode(Opcodes.ALOAD, 86));
        il.add(new InsnNode(Opcodes.ATHROW));
        il.add(done);
        il.add(new VarInsnNode(Opcodes.ILOAD, 26));
        il.add(new JumpInsnNode(Opcodes.IFLE, ret0));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(ret0);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));

        tcbs.add(new org.objectweb.asm.tree.TryCatchBlockNode(start, end, handler, "java/lang/Throwable"));
        tcbs.add(new org.objectweb.asm.tree.TryCatchBlockNode(closeStart, closeEnd, handler2, "java/lang/Throwable"));
    }

    /** {@code local = Math.max((long) Mth.floor(d - r), low)}. */
    private static void clamp(InsnList il, int dSlot, int rSlot, int lowSlot, int dst) {
        il.add(new VarInsnNode(Opcodes.DLOAD, dSlot));
        il.add(new VarInsnNode(Opcodes.DLOAD, rSlot));
        il.add(new InsnNode(Opcodes.DSUB));
        il.add(call(Opcodes.INVOKESTATIC, MTH, "floor", "(D)I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.LLOAD, lowSlot));
        il.add(call(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(JJ)J"));
        il.add(new VarInsnNode(Opcodes.LSTORE, dst));
    }

    /** {@code local = Math.max((long) Mth.floor(d + r), lo)}. */
    private static void clampMax(InsnList il, int dSlot, int rSlot, int loSlot, int dst) {
        il.add(new VarInsnNode(Opcodes.DLOAD, dSlot));
        il.add(new VarInsnNode(Opcodes.DLOAD, rSlot));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(call(Opcodes.INVOKESTATIC, MTH, "floor", "(D)I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.LLOAD, loSlot));
        il.add(call(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(JJ)J"));
        il.add(new VarInsnNode(Opcodes.LSTORE, dst));
    }

    /** {@code dst = (int)(coord & 15L)} (section-relative, wide-safe). */
    private static void sectionRelative(InsnList il, int coordSlot, int dst) {
        il.add(new VarInsnNode(Opcodes.LLOAD, coordSlot));
        il.add(new LdcInsnNode(15L));
        il.add(new InsnNode(Opcodes.LAND));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ISTORE, dst));
    }

    private static InsnList arrayLoadDouble(int arraySlot, int indexSlot, int offset) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, arraySlot));
        il.add(new VarInsnNode(Opcodes.ILOAD, indexSlot));
        il.add(new InsnNode(Opcodes.ICONST_4));
        il.add(new InsnNode(Opcodes.IMUL));
        il.add(intConst(offset));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new InsnNode(Opcodes.DALOAD));
        return il;
    }

    private static void appendCloned(InsnList target, AbstractInsnNode start, AbstractInsnNode end) {
        Map<LabelNode, LabelNode> labels = new HashMap<>();
        for (AbstractInsnNode n = start; n != null && n != end; n = n.getNext()) {
            if (n instanceof LabelNode l) {
                labels.put(l, new LabelNode());
            }
        }
        for (AbstractInsnNode n = start; n != null && n != end; n = n.getNext()) {
            AbstractInsnNode clone = n.clone(labels);
            if (clone instanceof VarInsnNode v && v.var >= 21) {
                v.var += 5;
            } else if (clone instanceof IincInsnNode i && i.var >= 21) {
                i.var += 5;
            }
            target.add(clone);
        }
    }

    private static MethodInsnNode call(int opcode, String owner, String name, String desc) {
        boolean itf = opcode == Opcodes.INVOKEINTERFACE;
        return new MethodInsnNode(opcode, owner, name, desc, itf);
    }

    private static JumpInsnNode gotoLabel(LabelNode label) {
        return new JumpInsnNode(Opcodes.GOTO, label);
    }

    private static AbstractInsnNode intConst(int value) {
        if (value >= -1 && value <= 5) {
            return new InsnNode(Opcodes.ICONST_0 + value);
        }
        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            return new IntInsnNode(Opcodes.BIPUSH, value);
        }
        return new IntInsnNode(Opcodes.SIPUSH, value);
    }

    private static VarInsnNode loadF(int slot) {
        return new VarInsnNode(Opcodes.FLOAD, slot);
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
