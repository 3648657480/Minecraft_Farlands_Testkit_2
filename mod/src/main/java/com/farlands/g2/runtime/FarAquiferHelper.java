package com.farlands.g2.runtime;

import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Aquifer grid coordinates for the current {@code computeSubstance} call.
 *
 * <p>Vanilla's {@code gridX(int)} receives a wrapped block coordinate. The
 * exact grid is computed once at method entry from the context's wide
 * coordinates and published on the calling thread; {@code gridX/gridZ} read it
 * while the call is active. The scope is one method call with no nested
 * generation inside it, so this cannot be clobbered the way a chunk-wide
 * generation origin can.</p>
 */
public final class FarAquiferHelper {

    private static final ThreadLocal<Long> GRID_X = new ThreadLocal<>();
    private static final ThreadLocal<Long> GRID_Z = new ThreadLocal<>();

    private FarAquiferHelper() {
    }

    public static void begin(DensityFunction.FunctionContext context) {
        if (context instanceof WideContext wide) {
            GRID_X.set(Math.floorDiv((long) wide.getBlockXDouble() - 5L, 16L));
            GRID_Z.set(Math.floorDiv((long) wide.getBlockZDouble() - 5L, 16L));
        } else {
            GRID_X.set(Math.floorDiv((long) context.blockX() - 5L, 16L));
            GRID_Z.set(Math.floorDiv((long) context.blockZ() - 5L, 16L));
        }
    }

    public static void end() {
        GRID_X.remove();
        GRID_Z.remove();
    }

    /** Called by the patched {@code gridX}; {@code blockCoord} is already offset by -5. */
    public static int gridX(int blockCoord) {
        Long wide = GRID_X.get();
        return wide != null ? (int) (long) wide : blockCoord >> 4;
    }

    /** Called by the patched {@code gridZ}; {@code blockCoord} is already offset by -5. */
    public static int gridZ(int blockCoord) {
        Long wide = GRID_Z.get();
        return wide != null ? (int) (long) wide : blockCoord >> 4;
    }
}
