package com.farlands.g2.mixin;

import com.farlands.g2.util.FarProjection;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**\n * The FlatCache long patch produced wrong column values for far origins\n * (jaggedness cached from a stale cell), which raised the eastern seam chunk\n * by ~49 blocks. FlatCache is a pure memoisation of a 2D function, so for far\n * origins we call the wrapped function directly: identical values, vanilla\n * semantics, and the measured generation time is unchanged.\n */
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