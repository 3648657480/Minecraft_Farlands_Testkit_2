package com.farlands.g2.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * Wide-coordinate construction helpers used by the W5 consumer patches.
 *
 * <p>These are called from patched vanilla bytecode; the class lives in the mod
 * jar and is loaded by the same Knot class loader as the patched game classes.
 * At normal coordinates every helper produces exactly the same value as the
 * int constructor it replaces.</p>
 */
public final class WidePos {

    private WidePos() {
    }

    /**
     * Rebuild a world position from the wide origin plus an int delta. The int
     * coordinates are the origin's int view plus a small local offset, so the
     * delta is exact and the wide value is recovered without truncation.
     */
    public static BlockPos fromOrigin(BlockPos origin, int x, int y, int z) {
        return new BlockPos(
            origin.getLongX() + (x - origin.getX()),
            y,
            origin.getLongZ() + (z - origin.getZ())
        );
    }

    /** Chunk-centre block position with a chosen Y, keeping the wide chunk X/Z. */
    public static BlockPos chunkMiddle(ChunkPos pos, int y) {
        return new BlockPos(pos.getMiddleBlockXLong(), y, pos.getMiddleBlockZLong());
    }
}
