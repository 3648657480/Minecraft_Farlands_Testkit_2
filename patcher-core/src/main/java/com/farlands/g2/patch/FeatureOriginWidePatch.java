package com.farlands.g2.patch;

import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * W5-A: keeps the FEATURE body positions wide.
 *
 * <p>{@link PlacementModifierWidePatch} fixes the placement chain, so
 * {@code FeaturePlaceContext.origin()} is now a genuinely wide
 * {@code BlockPos} (e.g. {@code longX=2147483648} for chunk 134217728).
 * Several feature bodies then immediately truncate it back with the int
 * accessors when deriving their write positions:</p>
 *
 * <ul>
 *   <li>{@code SeagrassFeature.place}:
 *       {@code level.getHeight(OCEAN_FLOOR, origin.getX()+dx, origin.getZ()+dz)}
 *       and {@code new BlockPos(origin.getX()+dx, h, origin.getZ()+dz)};</li>
 *   <li>{@code KelpFeature.place}: the same pattern without the offset;</li>
 *   <li>{@code UnderwaterMagmaFeature.place}: derives the write cloud from
 *       {@code BoundingBox.fromCorners(floor-radius, floor+radius)}. The
 *       BoundingBox patch clamps X/Z into the int-safe range
 *       ({@code +/-2117483647}); at the boundary chunk the origin corner
 *       {@code 2147483643..2147483647} is already beyond that clamp, so every
 *       candidate collapses onto {@code x=2117483647} - a spot 30,000,000
 *       blocks away from the chunk being generated - and every write is
 *       logged as "Detected setBlock in a far chunk" and dropped.</li>
 * </ul>
 *
 * <p>At the 2^31 seam {@code origin.getX()} is {@code -2147483648}, so the
 * derived position is sign-extended back to the NEGATIVE far side and every
 * write is logged as "Detected setBlock in a far chunk" and dropped. The
 * methods are rebuilt in the long domain: X/Z stay long, the height query
 * resolves the owning chunk from the wide section coordinate
 * ({@code (int)(x>>4)}) and the exact local column ({@code (int)(x&15)}),
 * and the position is built with the widened {@code BlockPos(JJJ)}
 * constructor. The magma box is iterated in long directly (same X-fastest,
 * then Y, then Z order as {@code BlockPos$4}) instead of building an int
 * BoundingBox. The RNG call order is unchanged, so at normal coordinates the
 * produced positions, writes and random stream are bit-identical.</p>
 */
public final class FeatureOriginWidePatch implements ClassPatch {

    private static final String SEAGRASS =
        "net/minecraft/world/level/levelgen/feature/SeagrassFeature";
    private static final String KELP =
        "net/minecraft/world/level/levelgen/feature/KelpFeature";
    private static final String UNDERWATER_MAGMA =
        "net/minecraft/world/level/levelgen/feature/UnderwaterMagmaFeature";

    private static final Set<String> TARGETS = Set.of(SEAGRASS, KELP, UNDERWATER_MAGMA);

    private static final String FPC =
        "net/minecraft/world/level/levelgen/feature/FeaturePlaceContext";
    private static final String PLACE_DESC = "(L" + FPC + ";)Z";
    private static final String WGL = "net/minecraft/world/level/WorldGenLevel";
    private static final String BLOCKPOS = "net/minecraft/core/BlockPos";
    private static final String BLOCKSTATE = "net/minecraft/world/level/block/state/BlockState";
    private static final String HTYPES = "net/minecraft/world/level/levelgen/Heightmap$Types";
    private static final String CHUNKACCESS = "net/minecraft/world/level/chunk/ChunkAccess";
    private static final String BLOCKS = "net/minecraft/world/level/block/Blocks";
    private static final String PROB_CFG =
        "net/minecraft/world/level/levelgen/feature/configurations/ProbabilityFeatureConfiguration";
    private static final String PROPERTY =
        "net/minecraft/world/level/block/state/properties/Property";
    private static final String UM_CFG =
        "net/minecraft/world/level/levelgen/feature/configurations/UnderwaterMagmaConfiguration";
    private static final String OPTIONAL_INT = "java/util/OptionalInt";

    /** {@code static OptionalInt getFloorY(WorldGenLevel, BlockPos, UMConfig)}. */
    private static final String GET_FLOOR_Y = "getFloorY";
    private static final String GET_FLOOR_Y_DESC =
        "(L" + WGL + ";L" + BLOCKPOS + ";L" + UM_CFG + ";)L" + OPTIONAL_INT + ";";

    /** {@code boolean isValidPlacement(WorldGenLevel, BlockPos)} (private instance). */
    private static final String IS_VALID = "isValidPlacement";
    private static final String IS_VALID_DESC = "(L" + WGL + ";L" + BLOCKPOS + ";)Z";

    /** {@code static int farlands$heightAt(WorldGenLevel, Heightmap.Types, long, long)}. */
    private static final String HEIGHT_HELPER = "farlands$heightAt";
    private static final String HEIGHT_HELPER_DESC =
        "(L" + WGL + ";L" + HTYPES + ";JJ)I";

    @Override
    public boolean matches(String internalName) {
        return TARGETS.contains(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        if (SEAGRASS.equals(node.name) || KELP.equals(node.name)) {
            if (find(node, HEIGHT_HELPER, HEIGHT_HELPER_DESC) != null) {
                return original; // already patched
            }
            MethodNode place = find(node, "place", PLACE_DESC);
            if (place == null) {
                throw new IllegalStateException(node.name + "#place not found");
            }
            rewrite(place, SEAGRASS.equals(node.name) ? seagrassBody() : kelpBody());
            node.methods.add(heightHelper());
        } else if (UNDERWATER_MAGMA.equals(node.name)) {
            MethodNode place = find(node, "place", PLACE_DESC);
            if (place == null) {
                throw new IllegalStateException(node.name + "#place not found");
            }
            if (uses(place, BLOCKPOS, "getLongX")) {
                return original; // already patched
            }
            rewrite(place, magmaBody());
        } else {
            return original;
        }

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    /**
     * {@code WorldGenLevel level = ctx.level();
     *  BlockPos origin = ctx.origin();
     *  UnderwaterMagmaConfiguration cfg = (UnderwaterMagmaConfiguration) ctx.config();
     *  RandomSource random = ctx.random();
     *  OptionalInt floorY = getFloorY(level, origin, cfg);
     *  if (floorY.isEmpty()) return false;
     *  BlockPos floor = origin.atY(floorY.getAsInt());
     *  int radius = cfg.placementRadiusAroundFloor;
     *  long minX = floor.getLongX() - radius, maxX = floor.getLongX() + radius;
     *  int minY = floor.getY() - radius, maxY = floor.getY() + radius;
     *  long minZ = floor.getLongZ() - radius, maxZ = floor.getLongZ() + radius;
     *  int count = 0;
     *  for (long z = minZ; z <= maxZ; z++)
     *      for (int y = minY; y <= maxY; y++)
     *          for (long x = minX; x <= maxX; x++) {
     *              BlockPos pos = new BlockPos(x, (long) y, z);
     *              if (random.nextFloat() < cfg.placementProbabilityPerValidPosition
     *                      && isValidPlacement(level, pos)) {
     *                  level.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
     *                  count++;
     *              }
     *          }
     *  return count > 0;}
     *
     * <p>Matches {@code BlockPos$4}'s X-fastest, then Y, then Z order and
     * consumes exactly one {@code nextFloat()} per candidate, so the random
     * stream is identical at normal coordinates.</p>
     */
    private static InsnList magmaBody() {
        InsnList il = new InsnList();
        LabelNode retFalse = new LabelNode();
        LabelNode zCond = new LabelNode();
        LabelNode zInc = new LabelNode();
        LabelNode yCond = new LabelNode();
        LabelNode yInc = new LabelNode();
        LabelNode xCond = new LabelNode();
        LabelNode xInc = new LabelNode();
        LabelNode done = new LabelNode();

        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FPC, "level", "()L" + WGL + ";", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FPC, "origin", "()L" + BLOCKPOS + ";", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FPC, "config",
            "()Lnet/minecraft/world/level/levelgen/feature/configurations/FeatureConfiguration;", false));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, UM_CFG));
        il.add(new VarInsnNode(Opcodes.ASTORE, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FPC, "random",
            "()Lnet/minecraft/util/RandomSource;", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 5));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, UNDERWATER_MAGMA, GET_FLOOR_Y,
            GET_FLOOR_Y_DESC, false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 6));
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, OPTIONAL_INT, "isEmpty", "()Z", false));
        il.add(new JumpInsnNode(Opcodes.IFNE, retFalse));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, OPTIONAL_INT, "getAsInt", "()I", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "atY", "(I)L" + BLOCKPOS + ";", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 7));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, UM_CFG, "placementRadiusAroundFloor", "I"));
        il.add(new VarInsnNode(Opcodes.ISTORE, 8));

        axisRange(il, 9, 11, "getLongX");
        axisRange(il, 15, 17, "getLongZ");
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getY", "()I", false));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new InsnNode(Opcodes.ISUB));
        il.add(new VarInsnNode(Opcodes.ISTORE, 13));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getY", "()I", false));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new VarInsnNode(Opcodes.ISTORE, 14));

        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 25));
        il.add(new VarInsnNode(Opcodes.LLOAD, 15));
        il.add(new VarInsnNode(Opcodes.LSTORE, 19));
        il.add(zCond);
        il.add(new VarInsnNode(Opcodes.LLOAD, 19));
        il.add(new VarInsnNode(Opcodes.LLOAD, 17));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFGT, done));
        il.add(new VarInsnNode(Opcodes.ILOAD, 13));
        il.add(new VarInsnNode(Opcodes.ISTORE, 21));
        il.add(yCond);
        il.add(new VarInsnNode(Opcodes.ILOAD, 21));
        il.add(new VarInsnNode(Opcodes.ILOAD, 14));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGT, zInc));
        il.add(new VarInsnNode(Opcodes.LLOAD, 9));
        il.add(new VarInsnNode(Opcodes.LSTORE, 22));
        il.add(xCond);
        il.add(new VarInsnNode(Opcodes.LLOAD, 22));
        il.add(new VarInsnNode(Opcodes.LLOAD, 11));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFGT, yInc));
        il.add(new TypeInsnNode(Opcodes.NEW, BLOCKPOS));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.LLOAD, 22));
        il.add(new VarInsnNode(Opcodes.ILOAD, 21));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.LLOAD, 19));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, BLOCKPOS, "<init>", "(JJJ)V", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 24));
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
            "net/minecraft/util/RandomSource", "nextFloat", "()F", true));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, UM_CFG,
            "placementProbabilityPerValidPosition", "F"));
        il.add(new InsnNode(Opcodes.FCMPG));
        il.add(new JumpInsnNode(Opcodes.IFGE, xInc));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 24));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, UNDERWATER_MAGMA, IS_VALID,
            IS_VALID_DESC, false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, xInc));
        writeMagma(il, 2, 24);
        il.add(new org.objectweb.asm.tree.IincInsnNode(25, 1));
        il.add(xInc);
        il.add(new VarInsnNode(Opcodes.LLOAD, 22));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 22));
        il.add(new JumpInsnNode(Opcodes.GOTO, xCond));
        il.add(yInc);
        il.add(new org.objectweb.asm.tree.IincInsnNode(21, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, yCond));
        il.add(zInc);
        il.add(new VarInsnNode(Opcodes.LLOAD, 19));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 19));
        il.add(new JumpInsnNode(Opcodes.GOTO, zCond));
        il.add(done);
        il.add(new VarInsnNode(Opcodes.ILOAD, 25));
        il.add(new JumpInsnNode(Opcodes.IFLE, retFalse));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(retFalse);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    /** {@code min = <accessor>() - radius; max = <accessor>() + radius} (long locals). */
    private static void axisRange(InsnList il, int minSlot, int maxSlot, String accessor) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, accessor, "()J", false));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new VarInsnNode(Opcodes.LSTORE, minSlot));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, accessor, "()J", false));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, maxSlot));
    }

    /** {@code level.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), 2)}. */
    private static void writeMagma(InsnList il, int levelSlot, int posSlot) {
        il.add(new VarInsnNode(Opcodes.ALOAD, levelSlot));
        il.add(new VarInsnNode(Opcodes.ALOAD, posSlot));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, BLOCKS, "MAGMA_BLOCK",
            "Lnet/minecraft/world/level/block/Block;"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            "net/minecraft/world/level/block/Block", "defaultBlockState", "()L" + BLOCKSTATE + ";", false));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, WGL, "setBlock",
            "(L" + BLOCKPOS + ";L" + BLOCKSTATE + ";I)Z", true));
        il.add(new InsnNode(Opcodes.POP));
    }

    /**
     * {@code boolean ok = false;
     *  RandomSource random = ctx.random();
     *  WorldGenLevel level = ctx.level();
     *  BlockPos origin = ctx.origin();
     *  ProbabilityFeatureConfiguration cfg = ctx.config();
     *  int dx = random.nextInt(8) - random.nextInt(8);
     *  int dz = random.nextInt(8) - random.nextInt(8);
     *  long x = origin.getLongX() + dx;
     *  long z = origin.getLongZ() + dz;
     *  int h = farlands$heightAt(level, OCEAN_FLOOR, x, z);
     *  BlockPos pos = new BlockPos(x, h, z);
     *  if (level.getBlockState(pos).is(WATER)) {
     *      boolean tall = random.nextDouble() < cfg.probability;
     *      BlockState state = tall ? TALL_SEAGRASS : SEAGRASS;
     *      if (state.canSurvive(level, pos)) {
     *          if (tall) {
     *              BlockState upper = state.setValue(HALF, UPPER);
     *              BlockPos above = pos.above();
     *              if (level.getBlockState(above).is(WATER)) {
     *                  level.setBlock(pos, state, 2);
     *                  level.setBlock(above, upper, 2);
     *              }
     *          } else {
     *              level.setBlock(pos, state, 2);
     *          }
     *          ok = true;
     *      }
     *  }
     *  return ok;}
     */
    private static InsnList seagrassBody() {
        InsnList il = new InsnList();
        LabelNode end = new LabelNode();
        LabelNode tallFalse = new LabelNode();
        LabelNode tallStore = new LabelNode();
        LabelNode stateShort = new LabelNode();
        LabelNode stateStore = new LabelNode();
        LabelNode shortWrite = new LabelNode();
        LabelNode afterWrites = new LabelNode();

        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 2)); // ok
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FPC, "random",
            "()Lnet/minecraft/util/RandomSource;", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FPC, "level",
            "()L" + WGL + ";", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FPC, "origin",
            "()L" + BLOCKPOS + ";", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 5));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FPC, "config",
            "()Lnet/minecraft/world/level/levelgen/feature/configurations/FeatureConfiguration;", false));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, PROB_CFG));
        il.add(new VarInsnNode(Opcodes.ASTORE, 6));

        // dx = random.nextInt(8) - random.nextInt(8); dz likewise.
        nextIntMinusNextInt(il, 7, 8);
        nextIntMinusNextInt(il, 8, 8);

        // x = origin.getLongX() + dx
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongX", "()J", false));
        il.add(new VarInsnNode(Opcodes.ILOAD, 7));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 9));
        // z = origin.getLongZ() + dz
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongZ", "()J", false));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 11));

        // h = farlands$heightAt(level, OCEAN_FLOOR, x, z)
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, HTYPES, "OCEAN_FLOOR", "L" + HTYPES + ";"));
        il.add(new VarInsnNode(Opcodes.LLOAD, 9));
        il.add(new VarInsnNode(Opcodes.LLOAD, 11));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, SEAGRASS, HEIGHT_HELPER,
            HEIGHT_HELPER_DESC, false));
        il.add(new VarInsnNode(Opcodes.ISTORE, 13));

        // pos = new BlockPos(x, h, z)
        il.add(new TypeInsnNode(Opcodes.NEW, BLOCKPOS));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.LLOAD, 9));
        il.add(new VarInsnNode(Opcodes.ILOAD, 13));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.LLOAD, 11));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, BLOCKPOS, "<init>", "(JJJ)V", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 14));

        // if (!level.getBlockState(pos).is(WATER)) return ok;
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 14));
        getBlockState(il);
        blockIs(il, "WATER");
        il.add(new JumpInsnNode(Opcodes.IFEQ, end));

        // tall = random.nextDouble() < cfg.probability
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
            "net/minecraft/util/RandomSource", "nextDouble", "()D", true));
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, PROB_CFG, "probability", "F"));
        il.add(new InsnNode(Opcodes.F2D));
        il.add(new InsnNode(Opcodes.DCMPG));
        il.add(new JumpInsnNode(Opcodes.IFGE, tallFalse));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new JumpInsnNode(Opcodes.GOTO, tallStore));
        il.add(tallFalse);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(tallStore);
        il.add(new VarInsnNode(Opcodes.ISTORE, 15));

        // state = tall ? TALL_SEAGRASS.defaultBlockState() : SEAGRASS.defaultBlockState()
        il.add(new VarInsnNode(Opcodes.ILOAD, 15));
        il.add(new JumpInsnNode(Opcodes.IFEQ, stateShort));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, BLOCKS, "TALL_SEAGRASS", "Lnet/minecraft/world/level/block/Block;"));
        defaultBlockState(il);
        il.add(new JumpInsnNode(Opcodes.GOTO, stateStore));
        il.add(stateShort);
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, BLOCKS, "SEAGRASS", "Lnet/minecraft/world/level/block/Block;"));
        defaultBlockState(il);
        il.add(stateStore);
        il.add(new VarInsnNode(Opcodes.ASTORE, 16));

        // if (!state.canSurvive(level, pos)) return ok;
        il.add(new VarInsnNode(Opcodes.ALOAD, 16));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 14));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKSTATE, "canSurvive",
            "(Lnet/minecraft/world/level/LevelReader;L" + BLOCKPOS + ";)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, end));

        // if (!tall) { level.setBlock(pos, state, 2); ok = true; return ok; }
        il.add(new VarInsnNode(Opcodes.ILOAD, 15));
        il.add(new JumpInsnNode(Opcodes.IFEQ, shortWrite));

        // upper = state.setValue(HALF, UPPER)
        il.add(new VarInsnNode(Opcodes.ALOAD, 16));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC,
            "net/minecraft/world/level/block/TallSeagrassBlock", "HALF",
            "Lnet/minecraft/world/level/block/state/properties/EnumProperty;"));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC,
            "net/minecraft/world/level/block/state/properties/DoubleBlockHalf", "UPPER",
            "Lnet/minecraft/world/level/block/state/properties/DoubleBlockHalf;"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKSTATE, "setValue",
            "(L" + PROPERTY + ";Ljava/lang/Comparable;)Ljava/lang/Object;", false));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, BLOCKSTATE));
        il.add(new VarInsnNode(Opcodes.ASTORE, 17));
        // above = pos.above()
        il.add(new VarInsnNode(Opcodes.ALOAD, 14));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "above",
            "()L" + BLOCKPOS + ";", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 18));
        // if (level.getBlockState(above).is(WATER)) { write both }
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 18));
        getBlockState(il);
        blockIs(il, "WATER");
        il.add(new JumpInsnNode(Opcodes.IFEQ, afterWrites));
        writeBlock(il, 4, 14, 16);
        writeBlock(il, 4, 18, 17);
        il.add(new JumpInsnNode(Opcodes.GOTO, afterWrites));

        il.add(shortWrite);
        writeBlock(il, 4, 14, 16);
        il.add(afterWrites);
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new VarInsnNode(Opcodes.ISTORE, 2));
        il.add(end);
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    /**
     * {@code boolean ok = false;
     *  WorldGenLevel level = ctx.level();
     *  BlockPos origin = ctx.origin();
     *  RandomSource random = ctx.random();
     *  long x = origin.getLongX();
     *  long z = origin.getLongZ();
     *  int h = farlands$heightAt(level, OCEAN_FLOOR, x, z);
     *  BlockPos pos = new BlockPos(x, h, z);
     *  if (level.getBlockState(pos).is(WATER)) {
     *      BlockState kelp = KELP.defaultBlockState();
     *      BlockState plant = KELP_PLANT.defaultBlockState();
     *      int n = 1 + random.nextInt(10);
     *      for (int i = 0; i <= n; i++) {
     *          if (level.getBlockState(pos).is(WATER)
     *                  && level.getBlockState(pos.above()).is(WATER)
     *                  && plant.canSurvive(level, pos)) {
     *              if (i == n) {
     *                  level.setBlock(pos, kelp.setValue(AGE, random.nextInt(4) + 20), 2);
     *                  ok++;
     *              } else {
     *                  level.setBlock(pos, plant, 2);
     *              }
     *              pos = pos.above();
     *          } else if (i > 0) {
     *              BlockPos below = pos.below();
     *              if (kelp.canSurvive(level, below)
     *                      && !level.getBlockState(below.below()).is(KELP)) {
     *                  level.setBlock(below, kelp.setValue(AGE, random.nextInt(4) + 20), 2);
     *                  ok++;
     *              }
     *              break;
     *          } else {
     *              break;
     *          }
     *      }
     *  }
     *  return ok > 0;}
     */
    private static InsnList kelpBody() {
        InsnList il = new InsnList();
        LabelNode loop = new LabelNode();
        LabelNode elseIf = new LabelNode();
        LabelNode middle = new LabelNode();
        LabelNode advance = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode ret0 = new LabelNode();

        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 2)); // ok
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FPC, "level",
            "()L" + WGL + ";", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FPC, "origin",
            "()L" + BLOCKPOS + ";", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FPC, "random",
            "()Lnet/minecraft/util/RandomSource;", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 5));

        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongX", "()J", false));
        il.add(new VarInsnNode(Opcodes.LSTORE, 6));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongZ", "()J", false));
        il.add(new VarInsnNode(Opcodes.LSTORE, 8));

        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, HTYPES, "OCEAN_FLOOR", "L" + HTYPES + ";"));
        il.add(new VarInsnNode(Opcodes.LLOAD, 6));
        il.add(new VarInsnNode(Opcodes.LLOAD, 8));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, KELP, HEIGHT_HELPER,
            HEIGHT_HELPER_DESC, false));
        il.add(new VarInsnNode(Opcodes.ISTORE, 10));

        il.add(new TypeInsnNode(Opcodes.NEW, BLOCKPOS));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.LLOAD, 6));
        il.add(new VarInsnNode(Opcodes.ILOAD, 10));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.LLOAD, 8));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, BLOCKPOS, "<init>", "(JJJ)V", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 11));

        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 11));
        getBlockState(il);
        blockIs(il, "WATER");
        il.add(new JumpInsnNode(Opcodes.IFEQ, end));

        il.add(new FieldInsnNode(Opcodes.GETSTATIC, BLOCKS, "KELP", "Lnet/minecraft/world/level/block/Block;"));
        defaultBlockState(il);
        il.add(new VarInsnNode(Opcodes.ASTORE, 12));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, BLOCKS, "KELP_PLANT", "Lnet/minecraft/world/level/block/Block;"));
        defaultBlockState(il);
        il.add(new VarInsnNode(Opcodes.ASTORE, 13));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 10));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
            "net/minecraft/util/RandomSource", "nextInt", "(I)I", true));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new VarInsnNode(Opcodes.ISTORE, 14));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 15));

        il.add(loop);
        il.add(new VarInsnNode(Opcodes.ILOAD, 15));
        il.add(new VarInsnNode(Opcodes.ILOAD, 14));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGT, end));
        // if (!(level.getBlockState(pos).is(WATER)
        //       && level.getBlockState(pos.above()).is(WATER)
        //       && plant.canSurvive(level, pos))) goto elseIf;
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 11));
        getBlockState(il);
        blockIs(il, "WATER");
        il.add(new JumpInsnNode(Opcodes.IFEQ, elseIf));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 11));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "above",
            "()L" + BLOCKPOS + ";", false));
        getBlockState(il);
        blockIs(il, "WATER");
        il.add(new JumpInsnNode(Opcodes.IFEQ, elseIf));
        il.add(new VarInsnNode(Opcodes.ALOAD, 13));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 11));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKSTATE, "canSurvive",
            "(Lnet/minecraft/world/level/LevelReader;L" + BLOCKPOS + ";)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, elseIf));
        // if (i != n) goto middle;
        il.add(new VarInsnNode(Opcodes.ILOAD, 15));
        il.add(new VarInsnNode(Opcodes.ILOAD, 14));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPNE, middle));
        kelpWithAge(il, 11, 12);
        il.add(new org.objectweb.asm.tree.IincInsnNode(2, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, advance));
        il.add(middle);
        writeBlock(il, 3, 11, 13);
        il.add(new JumpInsnNode(Opcodes.GOTO, advance));
        il.add(elseIf);
        il.add(new VarInsnNode(Opcodes.ILOAD, 15));
        il.add(new JumpInsnNode(Opcodes.IFLE, advance));
        // below = pos.below(); if (!kelp.canSurvive(level, below)
        //     || level.getBlockState(below.below()).is(KELP)) break;
        il.add(new VarInsnNode(Opcodes.ALOAD, 11));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "below",
            "()L" + BLOCKPOS + ";", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 16));
        il.add(new VarInsnNode(Opcodes.ALOAD, 12));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 16));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKSTATE, "canSurvive",
            "(Lnet/minecraft/world/level/LevelReader;L" + BLOCKPOS + ";)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, end));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 16));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "below",
            "()L" + BLOCKPOS + ";", false));
        getBlockState(il);
        blockIs(il, "KELP");
        il.add(new JumpInsnNode(Opcodes.IFNE, end));
        kelpWithAge(il, 16, 12);
        il.add(new org.objectweb.asm.tree.IincInsnNode(2, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, end));
        il.add(advance);
        il.add(new VarInsnNode(Opcodes.ALOAD, 11));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "above",
            "()L" + BLOCKPOS + ";", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 11));
        il.add(new org.objectweb.asm.tree.IincInsnNode(15, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, loop));
        il.add(end);
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new JumpInsnNode(Opcodes.IFLE, ret0));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(ret0);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    /** {@code level.setBlock(posSlot, kelpSlot.setValue(AGE, random.nextInt(4)+20), 2)}. */
    private static void kelpWithAge(InsnList il, int posSlot, int kelpSlot) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, posSlot));
        il.add(new VarInsnNode(Opcodes.ALOAD, kelpSlot));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/level/block/KelpBlock",
            "AGE", "Lnet/minecraft/world/level/block/state/properties/IntegerProperty;"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new InsnNode(Opcodes.ICONST_4));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
            "net/minecraft/util/RandomSource", "nextInt", "(I)I", true));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 20));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf",
            "(I)Ljava/lang/Integer;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKSTATE, "setValue",
            "(L" + PROPERTY + ";Ljava/lang/Comparable;)Ljava/lang/Object;", false));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, BLOCKSTATE));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, WGL, "setBlock",
            "(L" + BLOCKPOS + ";L" + BLOCKSTATE + ";I)Z", true));
        il.add(new InsnNode(Opcodes.POP));
    }

    /**
     * {@code chunk = level.getChunk((int)(x>>4), (int)(z>>4));
     *  return chunk.getHeight(type, (int)(x&15), (int)(z&15)) + 1;}
     *
     * <p>Mirrors {@code WorldGenRegion.getHeight(Types,int,int)} (which adds
     * the +1) but derives the chunk from the wide block coordinate. At normal
     * coordinates the chunk and local column are the same.</p>
     */
    private static MethodNode heightHelper() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
            HEIGHT_HELPER, HEIGHT_HELPER_DESC, null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        section(il, 2);
        section(il, 4);
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, WGL, "getChunk",
            "(II)L" + CHUNKACCESS + ";", true));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        local(il, 2);
        local(il, 4);
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNKACCESS, "getHeight",
            "(L" + HTYPES + ";II)I", false));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    /** {@code (int)(local >> 4)} of a long parameter. */
    private static void section(InsnList il, int slot) {
        il.add(new VarInsnNode(Opcodes.LLOAD, slot));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 4));
        il.add(new InsnNode(Opcodes.LSHR));
        il.add(new InsnNode(Opcodes.L2I));
    }

    /** {@code (int)(local & 15)} of a long parameter. */
    private static void local(InsnList il, int slot) {
        il.add(new VarInsnNode(Opcodes.LLOAD, slot));
        il.add(new LdcInsnNode(15L));
        il.add(new InsnNode(Opcodes.LAND));
        il.add(new InsnNode(Opcodes.L2I));
    }

    /** {@code dst = random.nextInt(8) - random.nextInt(8)}. */
    private static void nextIntMinusNextInt(InsnList il, int dst, int bound) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new IntInsnNode(Opcodes.BIPUSH, bound));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
            "net/minecraft/util/RandomSource", "nextInt", "(I)I", true));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new IntInsnNode(Opcodes.BIPUSH, bound));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
            "net/minecraft/util/RandomSource", "nextInt", "(I)I", true));
        il.add(new InsnNode(Opcodes.ISUB));
        il.add(new VarInsnNode(Opcodes.ISTORE, dst));
    }

    /** Appends {@code level.getBlockState(<blockPosOnStack>)}. */
    private static void getBlockState(InsnList il) {
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, WGL, "getBlockState",
            "(L" + BLOCKPOS + ";)L" + BLOCKSTATE + ";", true));
    }

    /** Appends {@code <blockStateOnStack>.is(Blocks.<name>)}. */
    private static void blockIs(InsnList il, String block) {
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, BLOCKS, block,
            "Lnet/minecraft/world/level/block/Block;"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKSTATE, "is",
            "(Ljava/lang/Object;)Z", false));
    }

    /** {@code level.setBlock(posSlot, stateSlot, 2)}. */
    private static void writeBlock(InsnList il, int levelSlot, int posSlot, int stateSlot) {
        il.add(new VarInsnNode(Opcodes.ALOAD, levelSlot));
        il.add(new VarInsnNode(Opcodes.ALOAD, posSlot));
        il.add(new VarInsnNode(Opcodes.ALOAD, stateSlot));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, WGL, "setBlock",
            "(L" + BLOCKPOS + ";L" + BLOCKSTATE + ";I)Z", true));
        il.add(new InsnNode(Opcodes.POP));
    }

    /** Appends {@code <blockOnStack>.defaultBlockState()}. */
    private static void defaultBlockState(InsnList il) {
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            "net/minecraft/world/level/block/Block", "defaultBlockState",
            "()L" + BLOCKSTATE + ";", false));
    }

    private static MethodNode find(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) {
            if (name.equals(m.name) && desc.equals(m.desc)) {
                return m;
            }
        }
        return null;
    }

    private static boolean uses(MethodNode m, String owner, String name) {
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof MethodInsnNode mi && owner.equals(mi.owner) && name.equals(mi.name)) {
                return true;
            }
        }
        return false;
    }

    private static void rewrite(MethodNode m, InsnList body) {
        m.instructions.clear();
        m.localVariables = null;
        m.tryCatchBlocks.clear();
        m.instructions.add(body);
    }
}
