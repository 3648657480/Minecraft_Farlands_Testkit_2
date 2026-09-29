package com.farlands.g2.patch;

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
 * W5-B: widens the structure placement/generation chunk and box computations
 * so they do not wrap at block 2^31.
 *
 * <p>The structure pipeline derives block coordinates from chunk coordinates
 * with 32-bit arithmetic:</p>
 * <ul>
 *   <li>{@code StructurePlacement.getLocatePos} used
 *       {@code chunkPos.getMinBlockX()} = {@code x << 4}, which wraps at chunk
 *       2^27 (block 2^31);</li>
 *   <li>{@code RandomSpreadStructurePlacement.getPotentialStructureChunk}
 *       multiplied the spaced grid back out in int and returned an int-packed
 *       {@code ChunkPos};</li>
 *   <li>{@code StructurePiece.isCloseToChunk} padded
 *       {@code getMinBlockX() ± distance} in int, which wraps on the negative
 *       half-axis and turns the query window into an inverted range;</li>
 *   <li>{@code StructurePiece.makeBoundingBox} and
 *       {@code ChunkGenerator.getWritableArea} computed box extents
 *       ({@code x + width - 1}, {@code minBlockX + 15}) in int, so a piece or
 *       window crossing the seam produced a wrapped (min &gt; max) box.</li>
 * </ul>
 *
 * <p>Every rewritten expression is computed in the {@code long} domain and the
 * box ends are clamped into the int range the int-storage
 * {@code BoundingBox} supports. At normal coordinates the long value equals
 * the int it replaced, so the output is bit-identical.</p>
 *
 * <p>Scope note: {@code StructureStart}/{@code StructureManager}/
 * {@code StructureCheck} key everything on chunk coordinates and delegate
 * through {@code ChunkPos} accessors; at the 2^31 <em>block</em> boundary the
 * chunk domain is still int-representable (chunk 2^27), so no int wrap occurs
 * there. The wrapping points are the chunk-to-block conversions and the box
 * extents above, which this patch widens.</p>
 */
public final class StructureWidePatch implements ClassPatch {

    private static final String PLACEMENT =
        "net/minecraft/world/level/levelgen/structure/placement/StructurePlacement";
    private static final String RANDOM_SPREAD =
        "net/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement";
    private static final String PIECE = "net/minecraft/world/level/levelgen/structure/StructurePiece";
    private static final String CHUNK_GENERATOR = "net/minecraft/world/level/chunk/ChunkGenerator";

    private static final String CHUNKPOS = "net/minecraft/world/level/ChunkPos";
    private static final String BLOCKPOS = "net/minecraft/core/BlockPos";
    private static final String BOUNDING_BOX = "net/minecraft/world/level/levelgen/structure/BoundingBox";
    private static final String CHUNK_ACCESS = "net/minecraft/world/level/chunk/ChunkAccess";
    private static final String LEVEL_HEIGHT = "net/minecraft/world/level/LevelHeightAccessor";
    private static final String DIRECTION = "net/minecraft/core/Direction";
    private static final String AXIS = "net/minecraft/core/Direction$Axis";
    private static final String WORLDGEN_RANDOM = "net/minecraft/world/level/levelgen/WorldgenRandom";
    private static final String LEGACY_RANDOM = "net/minecraft/world/level/levelgen/LegacyRandomSource";
    private static final String RANDOM_SOURCE = "net/minecraft/util/RandomSource";
    private static final String SPREAD_TYPE =
        "net/minecraft/world/level/levelgen/structure/placement/RandomSpreadType";
    private static final String MATH = "java/lang/Math";

    @Override
    public boolean matches(String internalName) {
        return PLACEMENT.equals(internalName) || RANDOM_SPREAD.equals(internalName)
            || PIECE.equals(internalName) || CHUNK_GENERATOR.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        boolean changed;
        switch (node.name) {
            case PLACEMENT -> changed = patchStructurePlacement(node);
            case RANDOM_SPREAD -> changed = patchRandomSpread(node);
            case PIECE -> changed = patchStructurePiece(node);
            case CHUNK_GENERATOR -> changed = patchChunkGenerator(node);
            default -> {
                return original;
            }
        }
        if (!changed) {
            return original;
        }
        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    /**
     * {@code StructurePlacement.getLocatePos(ChunkPos)}: build the block
     * position from the wide chunk coordinates
     * ({@code chunkPos.xLong() << 4}) instead of the int-wrapping
     * {@code getMinBlockX()}. {@code locateOffset} is tiny, so the wide
     * {@code BlockPos(JJJ)} constructor plus the already-wide
     * {@code offset(Vec3i)} carry the result unchanged.
     */
    private static boolean patchStructurePlacement(ClassNode node) {
        MethodNode m = find(node, "getLocatePos",
            "(L" + CHUNKPOS + ";)L" + BLOCKPOS + ";");
        if (m == null) {
            throw new IllegalStateException(PLACEMENT + "#getLocatePos not found");
        }
        if (hasCall(m, CHUNKPOS, "xLong")) {
            return false; // already patched
        }
        InsnList il = new InsnList();
        il.add(new TypeInsnNode(Opcodes.NEW, BLOCKPOS));
        il.add(new InsnNode(Opcodes.DUP));
        chunkBlockOrigin(il, 1, "X");
        il.add(new InsnNode(Opcodes.LCONST_0));
        chunkBlockOrigin(il, 1, "Z");
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, BLOCKPOS, "<init>", "(JJJ)V", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, PLACEMENT, "locateOffset",
            "()Lnet/minecraft/core/Vec3i;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "offset",
            "(Lnet/minecraft/core/Vec3i;)L" + BLOCKPOS + ";", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        replace(m, il);
        return true;
    }

    /**
     * {@code RandomSpreadStructurePlacement.getPotentialStructureChunk}: keep
     * the spaced grid in long ({@code floorDiv((long) source, spacing)}) and
     * multiply it back out in long, so the candidate chunk no longer depends
     * on 32-bit overflow. The RNG salt still receives the (int) grid values
     * (identical in the int domain, which is what the seed contract uses) and
     * the result is returned through the wide {@code ChunkPos(JJ)}
     * constructor.
     */
    private static boolean patchRandomSpread(ClassNode node) {
        MethodNode m = find(node, "getPotentialStructureChunk",
            "(JII)L" + CHUNKPOS + ";");
        if (m == null) {
            throw new IllegalStateException(RANDOM_SPREAD + "#getPotentialStructureChunk not found");
        }
        if (hasCall(m, MATH, "floorDiv", "(JJ)J")) {
            return false; // already patched
        }
        InsnList il = new InsnList();
        // long spacedGridX = Math.floorDiv((long) sourceX, (long) spacing);
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new InsnNode(Opcodes.I2L));
        spacing(il);
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MATH, "floorDiv", "(JJ)J", false));
        il.add(new VarInsnNode(Opcodes.LSTORE, 5));
        // long spacedGridZ = Math.floorDiv((long) sourceZ, (long) spacing);
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new InsnNode(Opcodes.I2L));
        spacing(il);
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MATH, "floorDiv", "(JJ)J", false));
        il.add(new VarInsnNode(Opcodes.LSTORE, 7));
        // WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        il.add(new TypeInsnNode(Opcodes.NEW, WORLDGEN_RANDOM));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new TypeInsnNode(Opcodes.NEW, LEGACY_RANDOM));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new InsnNode(Opcodes.LCONST_0));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, LEGACY_RANDOM, "<init>", "(J)V", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, WORLDGEN_RANDOM, "<init>",
            "(L" + RANDOM_SOURCE + ";)V", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 9));
        // random.setLargeFeatureWithSalt(seed, (int) spacedGridX, (int) spacedGridZ, salt());
        il.add(new VarInsnNode(Opcodes.ALOAD, 9));
        il.add(new VarInsnNode(Opcodes.LLOAD, 1));
        il.add(new VarInsnNode(Opcodes.LLOAD, 5));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.LLOAD, 7));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, RANDOM_SPREAD, "salt", "()I", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WORLDGEN_RANDOM, "setLargeFeatureWithSalt",
            "(JIII)V", false));
        // int limit = this.spacing - this.separation;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, RANDOM_SPREAD, "spacing", "I"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, RANDOM_SPREAD, "separation", "I"));
        il.add(new InsnNode(Opcodes.ISUB));
        il.add(new VarInsnNode(Opcodes.ISTORE, 10));
        // int spreadX = this.spreadType.evaluate(random, limit);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, RANDOM_SPREAD, "spreadType",
            "L" + SPREAD_TYPE + ";"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 9));
        il.add(new VarInsnNode(Opcodes.ILOAD, 10));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SPREAD_TYPE, "evaluate",
            "(L" + RANDOM_SOURCE + ";I)I", false));
        il.add(new VarInsnNode(Opcodes.ISTORE, 11));
        // int spreadZ = this.spreadType.evaluate(random, limit);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, RANDOM_SPREAD, "spreadType",
            "L" + SPREAD_TYPE + ";"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 9));
        il.add(new VarInsnNode(Opcodes.ILOAD, 10));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SPREAD_TYPE, "evaluate",
            "(L" + RANDOM_SOURCE + ";I)I", false));
        il.add(new VarInsnNode(Opcodes.ISTORE, 12));
        // return new ChunkPos(spacedGridX * spacing + spreadX, spacedGridZ * spacing + spreadZ);
        il.add(new TypeInsnNode(Opcodes.NEW, CHUNKPOS));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.LLOAD, 5));
        spacing(il);
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new VarInsnNode(Opcodes.ILOAD, 11));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LLOAD, 7));
        spacing(il);
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new VarInsnNode(Opcodes.ILOAD, 12));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, CHUNKPOS, "<init>", "(JJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        replace(m, il);
        return true;
    }

    /**
     * {@code StructurePiece.isCloseToChunk}: derive the padded query window
     * from the wide chunk origin and clamp it into the int range, so the
     * window never turns into an inverted (min &gt; max) rectangle on the
     * negative half-axis.
     */
    private static boolean patchStructurePiece(ClassNode node) {
        boolean changed = false;
        MethodNode close = find(node, "isCloseToChunk", "(L" + CHUNKPOS + ";I)Z");
        if (close == null) {
            throw new IllegalStateException(PIECE + "#isCloseToChunk not found");
        }
        if (!hasCall(close, MATH, "clamp", "(JII)I")) {
            InsnList il = new InsnList();
            // long cx = pos.xLong() << 4;  long cz = pos.zLong() << 4;
            chunkBlockOrigin(il, 1, "X");
            il.add(new VarInsnNode(Opcodes.LSTORE, 3));
            chunkBlockOrigin(il, 1, "Z");
            il.add(new VarInsnNode(Opcodes.LSTORE, 5));
            // int minX = clamp(cx - distance, MIN, MAX);
            il.add(new VarInsnNode(Opcodes.LLOAD, 3));
            il.add(new VarInsnNode(Opcodes.ILOAD, 2));
            il.add(new InsnNode(Opcodes.I2L));
            il.add(new InsnNode(Opcodes.LSUB));
            il.add(new LdcInsnNode(Integer.MIN_VALUE));
            il.add(new LdcInsnNode(Integer.MAX_VALUE));
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MATH, "clamp", "(JII)I", false));
            il.add(new VarInsnNode(Opcodes.ISTORE, 7));
            // int minZ = clamp(cz - distance, MIN, MAX);
            il.add(new VarInsnNode(Opcodes.LLOAD, 5));
            il.add(new VarInsnNode(Opcodes.ILOAD, 2));
            il.add(new InsnNode(Opcodes.I2L));
            il.add(new InsnNode(Opcodes.LSUB));
            il.add(new LdcInsnNode(Integer.MIN_VALUE));
            il.add(new LdcInsnNode(Integer.MAX_VALUE));
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MATH, "clamp", "(JII)I", false));
            il.add(new VarInsnNode(Opcodes.ISTORE, 8));
            // int maxX = clamp(cx + distance + 15, MIN, MAX);
            il.add(new VarInsnNode(Opcodes.LLOAD, 3));
            il.add(new VarInsnNode(Opcodes.ILOAD, 2));
            il.add(new InsnNode(Opcodes.I2L));
            il.add(new InsnNode(Opcodes.LADD));
            il.add(new LdcInsnNode(15L));
            il.add(new InsnNode(Opcodes.LADD));
            il.add(new LdcInsnNode(Integer.MIN_VALUE));
            il.add(new LdcInsnNode(Integer.MAX_VALUE));
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MATH, "clamp", "(JII)I", false));
            il.add(new VarInsnNode(Opcodes.ISTORE, 9));
            // int maxZ = clamp(cz + distance + 15, MIN, MAX);
            il.add(new VarInsnNode(Opcodes.LLOAD, 5));
            il.add(new VarInsnNode(Opcodes.ILOAD, 2));
            il.add(new InsnNode(Opcodes.I2L));
            il.add(new InsnNode(Opcodes.LADD));
            il.add(new LdcInsnNode(15L));
            il.add(new InsnNode(Opcodes.LADD));
            il.add(new LdcInsnNode(Integer.MIN_VALUE));
            il.add(new LdcInsnNode(Integer.MAX_VALUE));
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MATH, "clamp", "(JII)I", false));
            il.add(new VarInsnNode(Opcodes.ISTORE, 10));
            // return this.boundingBox.intersects(minX, minZ, maxX, maxZ);
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, PIECE, "boundingBox", "L" + BOUNDING_BOX + ";"));
            il.add(new VarInsnNode(Opcodes.ILOAD, 7));
            il.add(new VarInsnNode(Opcodes.ILOAD, 8));
            il.add(new VarInsnNode(Opcodes.ILOAD, 9));
            il.add(new VarInsnNode(Opcodes.ILOAD, 10));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BOUNDING_BOX, "intersects", "(IIII)Z", false));
            il.add(new InsnNode(Opcodes.IRETURN));
            replace(close, il);
            changed = true;
        }

        MethodNode box = find(node, "makeBoundingBox",
            "(III" + "L" + DIRECTION + ";III)L" + BOUNDING_BOX + ";");
        if (box == null) {
            throw new IllegalStateException(PIECE + "#makeBoundingBox not found");
        }
        if (!hasCall(box, MATH, "clamp", "(JII)I")) {
            InsnList il = new InsnList();
            LabelNode other = new LabelNode();
            // if (direction.getAxis() != Axis.Z) goto other;
            il.add(new VarInsnNode(Opcodes.ALOAD, 3));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, DIRECTION, "getAxis",
                "()L" + AXIS + ";", false));
            il.add(new FieldInsnNode(Opcodes.GETSTATIC, AXIS, "Z", "L" + AXIS + ";"));
            il.add(new JumpInsnNode(Opcodes.IF_ACMPNE, other));
            // Axis.Z: maxX = x + width - 1, maxY = y + height - 1, maxZ = z + depth - 1
            boundingBoxWithClampedMax(il, 4, 5, 6);
            il.add(other);
            // else: maxX = x + depth - 1, maxY = y + height - 1, maxZ = z + width - 1
            boundingBoxWithClampedMax(il, 6, 5, 4);
            replace(box, il);
            changed = true;
        }
        return changed;
    }

    /**
     * {@code ChunkGenerator.getWritableArea}: the chunk's write box. Derive
     * its X/Z span from the wide chunk origin and clamp the ends into the int
     * range instead of wrapping {@code minBlockX + 15}.
     */
    private static boolean patchChunkGenerator(ClassNode node) {
        MethodNode m = find(node, "getWritableArea", "(L" + CHUNK_ACCESS + ";)L" + BOUNDING_BOX + ";");
        if (m == null) {
            // The private helper is present on 26.2; a missing one is a hard error.
            throw new IllegalStateException(CHUNK_GENERATOR + "#getWritableArea not found");
        }
        if (hasCall(m, MATH, "clamp", "(JII)I")) {
            return false; // already patched
        }
        InsnList il = new InsnList();
        // long minBlockX = chunk.getPos().xLong() << 4;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNK_ACCESS, "getPos",
            "()L" + CHUNKPOS + ";", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNKPOS, "xLong", "()J", false));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 4));
        il.add(new InsnNode(Opcodes.LSHL));
        il.add(new VarInsnNode(Opcodes.LSTORE, 1));
        // long minBlockZ = chunk.getPos().zLong() << 4;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNK_ACCESS, "getPos",
            "()L" + CHUNKPOS + ";", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNKPOS, "zLong", "()J", false));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 4));
        il.add(new InsnNode(Opcodes.LSHL));
        il.add(new VarInsnNode(Opcodes.LSTORE, 3));
        // LevelHeightAccessor heightAccessor = chunk.getHeightAccessorForGeneration();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNK_ACCESS, "getHeightAccessorForGeneration",
            "()L" + LEVEL_HEIGHT + ";", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 5));
        // int minY = heightAccessor.getMinY() + 1;
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, LEVEL_HEIGHT, "getMinY", "()I", true));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new VarInsnNode(Opcodes.ISTORE, 6));
        // int maxY = heightAccessor.getMaxY();
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, LEVEL_HEIGHT, "getMaxY", "()I", true));
        il.add(new VarInsnNode(Opcodes.ISTORE, 7));
        // return new BoundingBox(clamp(minBlockX), minY, clamp(minBlockZ),
        //                        clamp(minBlockX + 15), maxY, clamp(minBlockZ + 15));
        il.add(new TypeInsnNode(Opcodes.NEW, BOUNDING_BOX));
        il.add(new InsnNode(Opcodes.DUP));
        clampLong(il, 1, 0L);
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        clampLong(il, 3, 0L);
        clampLong(il, 1, 15L);
        il.add(new VarInsnNode(Opcodes.ILOAD, 7));
        clampLong(il, 3, 15L);
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, BOUNDING_BOX, "<init>", "(IIIIII)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        replace(m, il);
        return true;
    }

    // ---- body emitters ----------------------------------------------------

    /** {@code (int) chunk.xLong() << 4} (push the long, no store). */
    private static void chunkBlockOrigin(InsnList il, int chunkSlot, String axis) {
        il.add(new VarInsnNode(Opcodes.ALOAD, chunkSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNKPOS, axis.toLowerCase() + "Long",
            "()J", false));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 4));
        il.add(new InsnNode(Opcodes.LSHL));
    }

    /** Push {@code this.spacing} as a long. */
    private static void spacing(InsnList il) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, RANDOM_SPREAD, "spacing", "I"));
        il.add(new InsnNode(Opcodes.I2L));
    }

    /** Push {@code clamp(long local + add, MIN, MAX)} as an int. */
    private static void clampLong(InsnList il, int longSlot, long add) {
        il.add(new VarInsnNode(Opcodes.LLOAD, longSlot));
        if (add != 0L) {
            il.add(new LdcInsnNode(add));
            il.add(new InsnNode(Opcodes.LADD));
        }
        il.add(new LdcInsnNode(Integer.MIN_VALUE));
        il.add(new LdcInsnNode(Integer.MAX_VALUE));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MATH, "clamp", "(JII)I", false));
    }

    /**
     * {@code new BoundingBox(x, y, z, clamp((long) x + wX - 1), clamp((long) y
     * + height - 1), clamp((long) z + wZ - 1))} where the width slots are the
     * X/Z extents for the current direction branch, then {@code ARETURN}.
     */
    private static void boundingBoxWithClampedMax(InsnList il, int xExtentSlot, int heightSlot,
            int zExtentSlot) {
        il.add(new TypeInsnNode(Opcodes.NEW, BOUNDING_BOX));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.ILOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        extentMinusOne(il, 0, xExtentSlot);
        extentMinusOne(il, 1, heightSlot);
        extentMinusOne(il, 2, zExtentSlot);
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, BOUNDING_BOX, "<init>", "(IIIIII)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
    }

    /** Push {@code clamp((long) base + extent - 1, MIN, MAX)} as an int. */
    private static void extentMinusOne(InsnList il, int baseSlot, int extentSlot) {
        il.add(new VarInsnNode(Opcodes.ILOAD, baseSlot));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ILOAD, extentSlot));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new LdcInsnNode(Integer.MIN_VALUE));
        il.add(new LdcInsnNode(Integer.MAX_VALUE));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MATH, "clamp", "(JII)I", false));
    }

    // ---- helpers ----------------------------------------------------------

    private static MethodNode find(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) {
            if (name.equals(m.name) && desc.equals(m.desc)) {
                return m;
            }
        }
        return null;
    }

    /** Whether the method already invokes {@code owner.name}. */
    private static boolean hasCall(MethodNode m, String owner, String name) {
        for (AbstractInsnNode n : m.instructions) {
            if (n instanceof MethodInsnNode mi && owner.equals(mi.owner) && name.equals(mi.name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCall(MethodNode m, String owner, String name, String desc) {
        for (AbstractInsnNode n : m.instructions) {
            if (n instanceof MethodInsnNode mi && owner.equals(mi.owner) && name.equals(mi.name)
                && desc.equals(mi.desc)) {
                return true;
            }
        }
        return false;
    }

    private static void replace(MethodNode m, InsnList body) {
        m.instructions.clear();
        m.localVariables = null;
        m.tryCatchBlocks.clear();
        m.instructions.add(body);
    }
}
