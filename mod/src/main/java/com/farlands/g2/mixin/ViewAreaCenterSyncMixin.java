package com.farlands.g2.mixin;

import com.farlands.g2.client.RenderSync;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The rotating grid moved its centre (once per section, every 16 blocks). The
 * visible-section list has to be rebuilt for the new ring even when the player
 * does not turn the camera; {@link com.farlands.g2.mixin.LevelExtractorViewSyncMixin}
 * consumes this flag in the next extract.
 */
@Mixin(ViewArea.class)
public abstract class ViewAreaCenterSyncMixin {

    @Inject(method = "repositionCamera", at = @At("RETURN"))
    private void farlands$noteCenterMove(SectionPos cameraSectionPos,
        CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            RenderSync.centerChanged();
        }
    }
}
