package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Temporary far-domain workaround: every light query (block and sky) returns
 * 14, so the broken packed-light lookups at the seam cannot produce
 * dark/bright artefacts or shadow bands. Remove once wide light storage is
 * implemented.
 */
@Mixin(LightEngine.class)
public class LightEngineBrightnessMixin {

    @Inject(method = "getLightValue", at = @At("HEAD"), cancellable = true)
    private void farlands$fixedBrightness(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (Math.abs((long) pos.getX()) > 33_554_000L
            || Math.abs((long) pos.getZ()) > 33_554_000L) {
            cir.setReturnValue(14);
        }
    }
}
