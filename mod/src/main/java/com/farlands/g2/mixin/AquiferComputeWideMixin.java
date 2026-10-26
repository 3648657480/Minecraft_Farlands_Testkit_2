package com.farlands.g2.mixin;

import com.farlands.g2.runtime.FarAquiferHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Publishes the exact aquifer grid for the duration of one {@code computeSubstance}
 * call; the patched {@code gridX/gridZ} read it. Normal coordinates produce the
 * same values as vanilla's int arithmetic.
 */
@Mixin(targets = "net/minecraft/world/level/levelgen/Aquifer$NoiseBasedAquifer")
public class AquiferComputeWideMixin {

    @Inject(method = "computeSubstance", at = @At("HEAD"))
    private void farlands$begin(DensityFunction.FunctionContext context, double density,
        CallbackInfoReturnable<BlockState> cir) {
        FarAquiferHelper.begin(context);
    }

    @Inject(method = "computeSubstance", at = @At("RETURN"))
    private void farlands$end(DensityFunction.FunctionContext context, double density,
        CallbackInfoReturnable<BlockState> cir) {
        FarAquiferHelper.end();
    }
}
