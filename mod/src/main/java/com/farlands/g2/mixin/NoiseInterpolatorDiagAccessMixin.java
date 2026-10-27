package com.farlands.g2.mixin;

import com.farlands.g2.runtime.NoiseInterpolatorDiagAccess;
import com.farlands.g2.runtime.RealContext;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Diagnostic accessors for the slice arrays of a noise interpolator. */
@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$NoiseInterpolator")
public abstract class NoiseInterpolatorDiagAccessMixin implements NoiseInterpolatorDiagAccess {

    @Shadow private double[][] slice0;
    @Shadow private double[][] slice1;
    @Shadow private DensityFunction noiseFiller;

    public int farlands$slice0Hash() {
        return System.identityHashCode(this.slice0);
    }

    public int farlands$slice1Hash() {
        return System.identityHashCode(this.slice1);
    }

    public String farlands$fillerType() {
        return this.noiseFiller == null ? "null" : this.noiseFiller.getClass().getSimpleName();
    }

    public double farlands$slice0(int z, int y) {
        if (this.slice0 == null || z < 0 || z >= this.slice0.length
            || this.slice0[z] == null || y < 0 || y >= this.slice0[z].length) {
            return Double.NaN;
        }
        return this.slice0[z][y];
    }

    public double farlands$slice1(int z, int y) {
        if (this.slice1 == null || z < 0 || z >= this.slice1.length
            || this.slice1[z] == null || y < 0 || y >= this.slice1[z].length) {
            return Double.NaN;
        }
        return this.slice1[z][y];
    }

    public double farlands$rawAt(long x, int y, long z) {
        if (this.noiseFiller == null) {
            return Double.NaN;
        }
        return this.noiseFiller.compute(new RealContext(x, y, z));
    }
}
