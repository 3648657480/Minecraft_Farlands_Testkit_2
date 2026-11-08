package com.farlands.g2.mixin;

import com.farlands.g2.util.FarProjection;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The 2D memoisation of {@code NoiseChunk} keys on {@code ChunkPos.pack(int,int)},
 * which wraps at far X/Z, so a far column can hit a value cached for a different
 * column ("jaggedness cached from a stale cell"). For far origins the wrapped 2D
 * function is called directly: identical values, vanilla semantics.
 *
 * <p>Measured 2026-11-05: replacing this with a long-keyed column memoisation kept
 * the far fingerprint identical ({@code surf=879024146b33 floor=9319dd6a448a}) but
 * did not move generation time — the step latencies at far coords are dominated by
 * dependency waiting, not by this cache. Reverted to the plain bypass.</p>
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$FlatCache")
public class FlatCacheFarBypassMixin {

    @Shadow private DensityFunction noiseFiller;

    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    private void farlands$bypass(DensityFunction.FunctionContext context,
        CallbackInfoReturnable<Double> cir) {
        if (FarProjection.isFarOrigin()) {
            cir.setReturnValue(this.noiseFiller.compute(context));
        }
    }
}
