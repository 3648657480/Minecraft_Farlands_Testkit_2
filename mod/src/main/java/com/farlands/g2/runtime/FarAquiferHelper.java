package com.farlands.g2.runtime;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/**
 * Positional random factory entry with unsigned coordinate normalization.
 * The aquifer seeds its random cells from int grid coordinates; converting
 * the wrapped negative band to the positive unsigned domain keeps the seeds
 * continuous across +/-2^31.
 */
public final class FarAquiferHelper {

    private FarAquiferHelper() {
    }

    public static RandomSource at(PositionalRandomFactory factory, int x, int y, int z) {
        if (x < -100_000_000) {
            x = (int) ((long) x + 4294967296L);
        }
        if (z < -100_000_000) {
            z = (int) ((long) z + 4294967296L);
        }
        return factory.at(x, y, z);
    }

    /**
     * Aquifer X grid index ({@code Aquifer$NoiseBasedAquifer.gridX(blockX-5)})
     * computed from the WIDE block coordinate.
     *
     * <p>The int {@code blockX} the aquifer is handed wraps at +/-2^31, so
     * {@code blockX - 5} overflows on the high edge of a chunk and the grid
     * index lands on the wrong lattice cell (the trench). {@link
     * com.farlands.g2.util.FarProjection#unwrapX(int)} restores the real
     * coordinate from the generation origin before the long-domain floor
     * division. At normal coordinates it is the exact signed int, so the
     * result is bit-identical to {@code (blockX - 5) >> 4}.</p>
     */
    public static int aquiferGridX(int blockX) {
        long real = (long) com.farlands.g2.util.FarProjection.unwrapX(blockX);
        return (int) Math.floorDiv(real - 5L, 16L);
    }

    /** Aquifer Z grid index; see {@link #aquiferGridX(int)}. */
    public static int aquiferGridZ(int blockZ) {
        long real = (long) com.farlands.g2.util.FarProjection.unwrapZ(blockZ);
        return (int) Math.floorDiv(real - 5L, 16L);
    }
}
