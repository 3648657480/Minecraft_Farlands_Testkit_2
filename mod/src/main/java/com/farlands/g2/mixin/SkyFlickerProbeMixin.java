package com.farlands.g2.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * W0 sky probe: log the extracted sky state per frame when {@code
 * -Dfarlands.diag.sky=true}. It does not change rendering; it only makes
 * "sky flicker" measurable (sky color / star brightness changes) at whatever
 * camera position the client can currently reach.
 */
@Mixin(SkyRenderer.class)
public class SkyFlickerProbeMixin {

    @Unique private static int farlands$frames;
    @Unique private static int farlands$lastLogged;
    @Unique private static int farlands$lastSky = Integer.MIN_VALUE;
    @Unique private static float farlands$lastStar = Float.NaN;

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void farlands$skyProbe(ClientLevel level, float partialTick, Camera camera,
        SkyRenderState state, CallbackInfo ci) {
        if (!Boolean.getBoolean("farlands.diag.sky")) {
            return;
        }
        farlands$frames++;
        boolean changed = state.skyColor != farlands$lastSky;
        if (!Float.isNaN(farlands$lastStar)
            && Math.abs(state.starBrightness - farlands$lastStar) > 0.05F) {
            changed = true;
        }
        if (!changed && farlands$frames - farlands$lastLogged < 100) {
            return;
        }
        farlands$lastLogged = farlands$frames;
        farlands$lastSky = state.skyColor;
        farlands$lastStar = state.starBrightness;
        var p = camera.position();
        System.out.println("[SKYPROBE] n=" + farlands$frames
            + " pos=" + p.x + "," + p.y + "," + p.z
            + " sky=" + String.format("%08x", state.skyColor)
            + " sun=" + state.sunAngle + " moon=" + state.moonAngle
            + " starAngle=" + state.starAngle + " starBrightness=" + state.starBrightness
            + " rain=" + state.rainBrightness + " dark=" + state.shouldRenderDarkDisc
            + " sunrise=" + String.format("%08x", state.sunriseAndSunsetColor));
        System.out.flush();
    }
}
