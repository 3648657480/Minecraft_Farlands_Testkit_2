package com.farlands.g2.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Diagnostic: bypass aquifer substance so terrain uses raw density only. */
@Mixin(targets = "net.minecraft.world.level.levelgen.Aquifer$NoiseBasedAquifer")
public class AquiferSkipDiagMixin {

    @Inject(method = "computeSubstance", at = @At("HEAD"), cancellable = true)
    private void farlands$skip(DensityFunction.FunctionContext context, double density,
        CallbackInfoReturnable<BlockState> cir) {
        if (Boolean.getBoolean("farlands.diag.noAquifer")) {
            cir.setReturnValue(null);
        }
    }
}