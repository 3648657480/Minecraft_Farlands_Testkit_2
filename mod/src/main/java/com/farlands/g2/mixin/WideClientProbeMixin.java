package com.farlands.g2.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Thin client entry point; production bridge lives in WideClientBridge. */
@Mixin(Minecraft.class)
public class WideClientProbeMixin {

    @Inject(method = "runTick", at = @At("TAIL"))
    private void farlands$applyWideChunks(boolean advanceGameTime, CallbackInfo ci) {
        com.farlands.g2.client.WideClientBridge.apply((Minecraft) (Object) this);
    }
}
