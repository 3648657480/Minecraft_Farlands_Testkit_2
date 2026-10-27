package com.farlands.g2.mixin;

import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NoiseChunk.FlatCache pre-fills a 2D noise cache from int quart coordinates
 * (QuartPos.toBlock of a wrapped base), so at chunk 2^27 the cached density is
 * sampled at the negative mirror column and terrain height tears. For far
 * columns bypass the cache and evaluate the wrapped function with the real
 * (wide) context.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$FlatCache")
public class NoiseChunkFlatCacheFarMixin {

    @Shadow
    private DensityFunction noiseFiller;

    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    private void farlands$bypass(DensityFunction.FunctionContext context,
        CallbackInfoReturnable<Double> cir) {
        if (Math.abs((long) context.blockX()) > 1_000_000L
            || Math.abs((long) context.blockZ()) > 1_000_000L) {
            cir.setReturnValue(this.noiseFiller.compute(context));
        }
    }
}