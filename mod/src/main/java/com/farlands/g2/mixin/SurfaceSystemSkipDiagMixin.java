package com.farlands.g2.mixin;

import net.minecraft.world.level.levelgen.SurfaceSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Diagnostic: skip surface rules to isolate the seam height difference. */
@Mixin(SurfaceSystem.class)
public class SurfaceSystemSkipDiagMixin {

    @Inject(method = "buildSurface", at = @At("HEAD"), cancellable = true)
    private void farlands$skip(CallbackInfo ci) {
        if (Boolean.getBoolean("farlands.diag.noSurface")) {
            ci.cancel();
        }
    }
}