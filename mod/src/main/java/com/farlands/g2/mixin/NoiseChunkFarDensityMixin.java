package com.farlands.g2.mixin;

import com.farlands.g2.runtime.RealContext;
import com.farlands.g2.util.FarProjection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Far generation origins cannot rely on the interpolated density lattice: the
 * cell cache is filled through wrapped int cell indices and, at 2^31, ends up
 * with values that differ from the raw router by ~0.45 (solid terrain written
 * far above the true surface). For those chunks sample the raw (unwrapped)
 * router finalDensity at the exact long coordinate instead; normal origins
 * keep the vanilla interpolation path untouched.
 */
@Mixin(NoiseChunk.class)
public class NoiseChunkFarDensityMixin {

    @Shadow private Aquifer aquifer;
    @Shadow private long farlands$cellStartBlockXLong;
    @Shadow private long farlands$cellStartBlockZLong;
    @Shadow private int cellStartBlockY;
    @Shadow private int inCellX;
    @Shadow private int inCellY;
    @Shadow private int inCellZ;

    @Inject(method = "getInterpolatedState", at = @At("HEAD"), cancellable = true)
    private void farlands$rawState(CallbackInfoReturnable<BlockState> cir) {
        if (!Boolean.getBoolean("farlands.far.rawDensity") || !FarProjection.isFarOrigin()) {
            return;
        }
        DensityFunction raw = FarProjection.rawFinal();
        if (raw == null) {
            return;
        }
        long x = this.farlands$cellStartBlockXLong + this.inCellX;
        long y = (long) this.cellStartBlockY + this.inCellY;
        long z = this.farlands$cellStartBlockZLong + this.inCellZ;
        RealContext context = new RealContext(x, y, z);
        double density = raw.compute(context);
        cir.setReturnValue(this.aquifer.computeSubstance(context, density));
    }
}