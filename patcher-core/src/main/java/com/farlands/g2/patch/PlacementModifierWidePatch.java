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
 * W5-A: keeps the feature PLACEMENT ORIGIN wide through the placement chain.
 *
 * <p>{@code ChunkGenerator.applyBiomeDecoration} hands
 * {@code SectionPos.origin()} (already widened by {@link SectionPosPatch}) to
 * {@code PlacedFeature.placeWithBiomeCheck}. That origin is correct, but the
 * placement modifiers immediately truncate it back to the int-wrapped view:
 * at the boundary chunk {@code 134217728} the wide origin x is
 * {@code 2147483648}, so {@code BlockPos.getX()} returns {@code -2147483648}.
 * The wrapped position is then written through {@code WorldGenRegion.setBlock},
 * whose patched wide {@code isWithinWriteZone} resolves the <em>real</em> chunk
 * {@code -134217728}, and every such write is logged as
 * "Detected setBlock in a far chunk" and dropped (in the observed boundary run
 * ~1300 lines, mostly ores/seagrass/springs).</p>
 *
 * <p>The wrap sites, verified in the 26.2 bytecode:</p>
 * <ul>
 *   <li>{@code InSquarePlacement.getPositions}:
 *       {@code random.nextInt(16) + pos.getX()} (int) then
 *       {@code new BlockPos(int,int,int)};</li>
 *   <li>{@code HeightmapPlacement.getPositions}: {@code pos.getX()/getZ()} (int)
 *       fed into the int heightmap query and {@code new BlockPos(int,int,int)};</li>
 *   <li>{@code RandomOffsetPlacement.getPositions}: same int pattern;</li>
 *   <li>{@code SurfaceRelativeThresholdFilter.shouldPlace}: heightmap query
 *       through the wrapped int column (filter decision, no write);</li>
 *   <li>{@code SurfaceWaterDepthFilter.shouldPlace}: two heightmap queries
 *       through the wrapped int column (filter decision, no write). Without
 *       the rewrite the wrapped int section resolves to an unloaded chunk and
 *       {@code WorldGenRegion.getChunk(int,int)} throws
 *       "Requested chunk unavailable during world generation".</li>
 * </ul>
 *
 * <p>Each is rewritten to derive the position from
 * {@code getLongX/getLongY/getLongZ}. The heightmap reads resolve the owning
 * chunk from the wide section coordinate ({@code x>>4}) and use the exact
 * local column ({@code x&15}) - identical to
 * {@code WorldGenRegion.getHeight} at normal coordinates, where the long
 * equals the int it replaced. {@code HeightRangePlacement} needs no change:
 * it already goes through the widened {@code BlockPos.atY}.</p>
 */
public final class PlacementModifierWidePatch implements ClassPatch {

    private static final String IN_SQUARE =
        "net/minecraft/world/level/levelgen/placement/InSquarePlacement";
    private static final String HEIGHTMAP =
        "net/minecraft/world/level/levelgen/placement/HeightmapPlacement";
    private static final String RANDOM_OFFSET =
        "net/minecraft/world/level/levelgen/placement/RandomOffsetPlacement";
    private static final String SURFACE_FILTER =
        "net/minecraft/world/level/levelgen/placement/SurfaceRelativeThresholdFilter";
    private static final String WATER_DEPTH_FILTER =
        "net/minecraft/world/level/levelgen/placement/SurfaceWaterDepthFilter";

    private static final Set<String> TARGETS =
        Set.of(IN_SQUARE, HEIGHTMAP, RANDOM_OFFSET, SURFACE_FILTER, WATER_DEPTH_FILTER);

    private static final String BLOCKPOS = "net/minecraft/core/BlockPos";
    private static final String STREAM = "java/util/stream/Stream";
    private static final String RANDOM = "net/minecraft/util/RandomSource";
    private static final String CONTEXT = "net/minecraft/world/level/levelgen/placement/PlacementContext";
    private static final String HEIGHTMAP_TYPES = "net/minecraft/world/level/levelgen/Heightmap$Types";
    private static final String WORLDGENLEVEL = "net/minecraft/world/level/WorldGenLevel";
    private static final String CHUNKACCESS = "net/minecraft/world/level/chunk/ChunkAccess";

    private static final String GET_POSITIONS =
        "(L" + CONTEXT + ";L" + RANDOM + ";L" + BLOCKPOS + ";)Ljava/util/stream/Stream;";

    /** {@code static int farlands$heightAt(PlacementContext, Types, long, long)}. */
    private static final String HEIGHT_HELPER = "farlands$heightAt";
    private static final String HEIGHT_HELPER_DESC =
        "(L" + CONTEXT + ";L" + HEIGHTMAP_TYPES + ";JJ)I";

    /** {@code static boolean farlands$within(PlacementContext, Types, long, long, int, int, int)}. */
    private static final String WITHIN_HELPER = "farlands$within";
    private static final String WITHIN_HELPER_DESC =
        "(L" + CONTEXT + ";L" + HEIGHTMAP_TYPES + ";JJIII)Z";

    @Override
    public boolean matches(String internalName) {
        return TARGETS.contains(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        boolean changed;
        if (IN_SQUARE.equals(node.name)) {
            MethodNode m = find(node, "getPositions", GET_POSITIONS);
            if (m == null || uses(m, BLOCKPOS, "getLongX")) {
                return original;
            }
            rewrite(m, inSquareBody());
            changed = true;
        } else if (HEIGHTMAP.equals(node.name)) {
            if (find(node, HEIGHT_HELPER, HEIGHT_HELPER_DESC) != null) {
                return original;
            }
            rewrite(find(node, "getPositions", GET_POSITIONS), heightmapBody());
            node.methods.add(heightHelper());
            changed = true;
        } else if (RANDOM_OFFSET.equals(node.name)) {
            MethodNode m = find(node, "getPositions", GET_POSITIONS);
            if (m == null || uses(m, BLOCKPOS, "getLongX")) {
                return original;
            }
            rewrite(m, randomOffsetBody());
            changed = true;
        } else if (SURFACE_FILTER.equals(node.name)) {
            if (find(node, HEIGHT_HELPER, HEIGHT_HELPER_DESC) != null) {
                return original;
            }
            MethodNode m = find(node, "shouldPlace",
                "(L" + CONTEXT + ";L" + RANDOM + ";L" + BLOCKPOS + ";)Z");
            if (m == null) {
                throw new IllegalStateException(node.name + "#shouldPlace not found");
            }
            rewrite(m, surfaceFilterBody());
            node.methods.add(heightHelper());
            node.methods.add(withinHelper());
            changed = true;
        } else if (WATER_DEPTH_FILTER.equals(node.name)) {
            if (find(node, HEIGHT_HELPER, HEIGHT_HELPER_DESC) != null) {
                return original;
            }
            MethodNode m = find(node, "shouldPlace",
                "(L" + CONTEXT + ";L" + RANDOM + ";L" + BLOCKPOS + ";)Z");
            if (m == null) {
                throw new IllegalStateException(node.name + "#shouldPlace not found");
            }
            rewrite(m, surfaceWaterDepthBody());
            node.methods.add(heightHelper());
            changed = true;
        } else {
            return original;
        }

        if (changed) {
            ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
            node.accept(cw);
            return cw.toByteArray();
        }
        return original;
    }

    /**
     * {@code long x = pos.getLongX() + random.nextInt(16);
     *  long z = pos.getLongZ() + random.nextInt(16);
     *  return Stream.of(new BlockPos(x, pos.getLongY(), z));}
     *
     * <p>The two {@code nextInt(16)} calls keep the vanilla order, so the same
     * RNG state produces the same offsets at normal coordinates.</p>
     */
    private static InsnList inSquareBody() {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongX", "()J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 16));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, RANDOM, "nextInt", "(I)I", true));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongZ", "()J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 16));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, RANDOM, "nextInt", "(I)I", true));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 6));
        il.add(new TypeInsnNode(Opcodes.NEW, BLOCKPOS));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.LLOAD, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongY", "()J", false));
        il.add(new VarInsnNode(Opcodes.LLOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, BLOCKPOS, "<init>", "(JJJ)V", false));
        il.add(streamOfObject());
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    /**
     * {@code int h = farlands$heightAt(context, this.heightmap, pos.getLongX(), pos.getLongZ());
     *  if (h <= context.getMinY()) return Stream.of(new BlockPos[0]);
     *  return Stream.of(pos.atY(h));}
     */
    private static InsnList heightmapBody() {
        InsnList il = new InsnList();
        heightAt(il, HEIGHTMAP);
        il.add(new VarInsnNode(Opcodes.ISTORE, 4));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CONTEXT, "getMinY", "()I", false));
        LabelNode empty = new LabelNode();
        il.add(new JumpInsnNode(Opcodes.IF_ICMPLE, empty));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "atY", "(I)L" + BLOCKPOS + ";", false));
        il.add(streamOfObject());
        il.add(new InsnNode(Opcodes.ARETURN));
        il.add(empty);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new TypeInsnNode(Opcodes.ANEWARRAY, BLOCKPOS));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, STREAM, "of",
            "([Ljava/lang/Object;)Ljava/util/stream/Stream;", true));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    /**
     * {@code long x = pos.getLongX() + this.xzSpread.sample(random);
     *  long y = pos.getLongY() + this.ySpread.sample(random);
     *  long z = pos.getLongZ() + this.xzSpread.sample(random);
     *  return Stream.of(new BlockPos(x, y, z));}
     */
    private static InsnList randomOffsetBody() {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongX", "()J", false));
        sample(il, "xzSpread");
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongY", "()J", false));
        sample(il, "ySpread");
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 6));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongZ", "()J", false));
        sample(il, "xzSpread");
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 8));
        il.add(new TypeInsnNode(Opcodes.NEW, BLOCKPOS));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.LLOAD, 4));
        il.add(new VarInsnNode(Opcodes.LLOAD, 6));
        il.add(new VarInsnNode(Opcodes.LLOAD, 8));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, BLOCKPOS, "<init>", "(JJJ)V", false));
        il.add(streamOfObject());
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    /**
     * {@code return farlands$within(context, this.heightmap, pos.getLongX(), pos.getLongZ(),
     *  pos.getY(), this.minInclusive, this.maxInclusive);}
     *
     * <p>The long comparisons live in the injected helper instead of the
     * rewritten method: ASM's {@code COMPUTE_FRAMES} mis-merges a reused
     * {@code MethodNode} that stores longs into fresh locals across a branch
     * (verified against the 26.2 class), while a fresh method node with the
     * same body computes fine.</p>
     */
    private static InsnList surfaceFilterBody() {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, SURFACE_FILTER, "heightmap",
            "L" + HEIGHTMAP_TYPES + ";"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongX", "()J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongZ", "()J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getY", "()I", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, SURFACE_FILTER, "minInclusive", "I"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, SURFACE_FILTER, "maxInclusive", "I"));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, SURFACE_FILTER, WITHIN_HELPER,
            WITHIN_HELPER_DESC, false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    /**
     * {@code int ocean = farlands$heightAt(ctx, OCEAN_FLOOR, pos.getLongX(), pos.getLongZ());
     *  int surface = farlands$heightAt(ctx, WORLD_SURFACE, pos.getLongX(), pos.getLongZ());
     *  return surface - ocean <= this.maxWaterDepth;}
     */
    private static InsnList surfaceWaterDepthBody() {
        InsnList il = new InsnList();
        heightAtType(il, WATER_DEPTH_FILTER, "OCEAN_FLOOR");
        il.add(new VarInsnNode(Opcodes.ISTORE, 4));
        heightAtType(il, WATER_DEPTH_FILTER, "WORLD_SURFACE");
        il.add(new VarInsnNode(Opcodes.ISTORE, 5));
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new InsnNode(Opcodes.ISUB));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, WATER_DEPTH_FILTER, "maxWaterDepth", "I"));
        LabelNode no = new LabelNode();
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGT, no));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(no);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    /**
     * {@code int h = farlands$heightAt(ctx, type, x, z);
     *  long min = (long)h + minInclusive; long max = (long)h + maxInclusive;
     *  return min <= (long)y && (long)y <= max;}
     */
    private static MethodNode withinHelper() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
            WITHIN_HELPER, WITHIN_HELPER_DESC, null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.LLOAD, 2));
        il.add(new VarInsnNode(Opcodes.LLOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, SURFACE_FILTER, HEIGHT_HELPER,
            HEIGHT_HELPER_DESC, false));
        il.add(new VarInsnNode(Opcodes.ISTORE, 9));
        il.add(new VarInsnNode(Opcodes.ILOAD, 9));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ILOAD, 7));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 10));
        il.add(new VarInsnNode(Opcodes.ILOAD, 9));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 12));
        il.add(new VarInsnNode(Opcodes.LLOAD, 10));
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LCMP));
        LabelNode no = new LabelNode();
        il.add(new JumpInsnNode(Opcodes.IFGT, no));
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.LLOAD, 12));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFGT, no));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(no);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    /** Pushes {@code farlands$heightAt(context, this.heightmap, pos.getLongX(), pos.getLongZ())}. */
    private static void heightAt(InsnList il, String owner) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, owner, "heightmap",
            "L" + HEIGHTMAP_TYPES + ";"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongX", "()J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongZ", "()J", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, HEIGHT_HELPER,
            HEIGHT_HELPER_DESC, false));
    }

    /**
     * Pushes {@code farlands$heightAt(context, Heightmap.Types.<type>, pos.getLongX(), pos.getLongZ())}
     * for classes without an instance {@code heightmap} field (the water depth
     * filter queries two fixed types).
     */
    private static void heightAtType(InsnList il, String owner, String typeField) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, HEIGHTMAP_TYPES, typeField,
            "L" + HEIGHTMAP_TYPES + ";"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongX", "()J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongZ", "()J", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, HEIGHT_HELPER,
            HEIGHT_HELPER_DESC, false));
    }

    /**
     * {@code chunk = context.getLevel().getChunk((int)(x>>4), (int)(z>>4));
     *  return chunk.getHeight(type, (int)(x&15), (int)(z&15)) + 1;}
     *
     * <p>Mirrors {@code WorldGenRegion.getHeight(Types,int,int)} (which adds
     * the +1) but derives the chunk from the wide section coordinate. At normal
     * coordinates the chunk and local column are the same.</p>
     */
    private static MethodNode heightHelper() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
            HEIGHT_HELPER, HEIGHT_HELPER_DESC, null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CONTEXT, "getLevel",
            "()L" + WORLDGENLEVEL + ";", false));
        section(il, 2);
        section(il, 4);
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, WORLDGENLEVEL, "getChunk",
            "(II)L" + CHUNKACCESS + ";", true));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        local(il, 2);
        local(il, 4);
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNKACCESS, "getHeight",
            "(L" + HEIGHTMAP_TYPES + ";II)I", false));
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

    /** {@code this.<field>.sample(random)} pushed as an int. */
    private static void sample(InsnList il, String field) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, RANDOM_OFFSET, field,
            "Lnet/minecraft/util/valueproviders/IntProvider;"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
            "net/minecraft/util/valueproviders/IntProvider", "sample",
            "(L" + RANDOM + ";)I", true));
        il.add(new InsnNode(Opcodes.I2L));
    }

    private static MethodInsnNode streamOfObject() {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, STREAM, "of",
            "(Ljava/lang/Object;)Ljava/util/stream/Stream;", true);
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
        if (m == null) {
            throw new IllegalStateException("placement method not found");
        }
        m.instructions.clear();
        m.localVariables = null;
        m.tryCatchBlocks.clear();
        m.instructions.add(body);
    }
}
