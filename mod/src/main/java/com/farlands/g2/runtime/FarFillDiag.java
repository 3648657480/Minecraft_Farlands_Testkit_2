package com.farlands.g2.runtime;

import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Diagnostic holder (-Dfarlands.diag.filldiag): publishes the world's raw
 * {@code NoiseRouter.finalDensity()} (the unwrapped tree, no NoiseChunk
 * interpolators) for the fill probe. The router is seed-wide, so a single
 * static reference is enough; it is only used by diagnostics.
 */
public final class FarFillDiag {

    private static volatile DensityFunction finalDensity;

    private FarFillDiag() {
    }

    public static void setFinalDensity(DensityFunction density) {
        finalDensity = density;
    }

    public static DensityFunction finalDensity() {
        return finalDensity;
    }
}
