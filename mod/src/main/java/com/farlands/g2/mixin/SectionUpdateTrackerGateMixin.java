package com.farlands.g2.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.SectionUpdateTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Diagnostic: bypass the neighbour/light compile gate to isolate hole cause. */
@Mixin(SectionUpdateTracker.class)
public class SectionUpdateTrackerGateMixin {

    @Inject(method = "hasAllNeighbors", at = @At("HEAD"), cancellable = true)
    private void farlands$force(ClientLevel level, long sectionNode, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.getBoolean("farlands.diag.forceNeighbors")) {
            cir.setReturnValue(true);
        }
    }
}