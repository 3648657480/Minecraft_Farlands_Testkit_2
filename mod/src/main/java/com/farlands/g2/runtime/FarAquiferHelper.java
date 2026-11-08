package com.farlands.g2.runtime;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/**
 * Aquifer grid coordinates for the current {@code computeSubstance} call.
 *
 * <p>Vanilla's {@code gridX(int)} receives a wrapped block coordinate. The
 * exact grid is computed once at method entry from the context's wide
 * coordinates and published on the calling thread; {@code gridX/gridZ} read it
 * while the call is active. The scope is one method call with no nested
 * generation inside it, so this cannot be clobbered the way a chunk-wide
 * generation origin can.</p>
 *
 * <p>The per-thread state is a single mutable holder: the old version did two
 * {@code ThreadLocal.set} calls with boxed {@code Long}s per aquifer sample,
 * which showed up as 5.3% {@code ThreadLocalMap.set} + 1.1% {@code remove} in a
 * JFR profile of far chunk generation.</p>
 */
public final class FarAquiferHelper {

    private static final ThreadLocal<Grid> GRID = ThreadLocal.withInitial(Grid::new);

    private static final class Grid {
        long x;
        long z;
        boolean active;
    }

    private FarAquiferHelper() {
    }

    public static void begin(DensityFunction.FunctionContext context) {
        Grid grid = GRID.get();
        if (context instanceof WideContext wide) {
            grid.x = Math.floorDiv((long) wide.getBlockXDouble() - 5L, 16L);
            grid.z = Math.floorDiv((long) wide.getBlockZDouble() - 5L, 16L);
        } else {
            grid.x = Math.floorDiv((long) context.blockX() - 5L, 16L);
            grid.z = Math.floorDiv((long) context.blockZ() - 5L, 16L);
        }
        grid.active = true;
    }

    public static void end() {
        GRID.get().active = false;
    }

    /**
     * Link target for {@code AquiferContextPatch}: patched MC calls
     * {@code FarAquiferHelper.at(factory,x,y,z)}. Before this method existed the
     * call threw {@code NoSuchMethodError} inside
     * {@code Aquifer$NoiseBasedAquifer#computeSubstance}; NoiseChunkMixin caught
     * it and silently fell back to the global fluid picker. Delegate to the
     * vanilla int factory for now; wide aquifer positions come with the wide
     * generation entry, not this hotfix.
     */
    public static RandomSource at(PositionalRandomFactory factory, int x, int y, int z) {
        return factory.at(x, y, z);
    }

    /** Called by the patched {@code gridX}; {@code blockCoord} is already offset by -5. */
    public static int gridX(int blockCoord) {
        Grid grid = GRID.get();
        return grid.active ? (int) grid.x : blockCoord >> 4;
    }

    /** Called by the patched {@code gridZ}; {@code blockCoord} is already offset by -5. */
    public static int gridZ(int blockCoord) {
        Grid grid = GRID.get();
        return grid.active ? (int) grid.z : blockCoord >> 4;
    }
}
