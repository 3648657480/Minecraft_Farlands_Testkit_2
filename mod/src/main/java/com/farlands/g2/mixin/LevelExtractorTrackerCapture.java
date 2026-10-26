package com.farlands.g2.mixin;

import net.minecraft.client.SectionUpdateTracker;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Diagnostic: publishes the extractor's section dirty tracker. */
@Mixin(LevelExtractor.class)
public class LevelExtractorTrackerCapture {

    @Shadow
    private SectionUpdateTracker sectionUpdateTracker;

    @Inject(method = "extract", at = @At("HEAD"))
    private void farlands$capture(net.minecraft.client.DeltaTracker deltaTracker,
        net.minecraft.client.Camera camera, float partialTick, CallbackInfo ci) {
        com.farlands.g2.runtime.TrackerProbe.LAST = this.sectionUpdateTracker;
    }
}