package com.farlands.g2.mixin;

import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Diagnostic: disable Beardifier (structure terrain) to isolate the seam delta. */
@Mixin(Beardifier.class)
public class BeardifierSkipDiagMixin {

    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    private void farlands$skip(DensityFunction.FunctionContext context,
        CallbackInfoReturnable<Double> cir) {
        if (Boolean.getBoolean("farlands.diag.noBeardifier")) {
            cir.setReturnValue(0.0);
        }
    }
}