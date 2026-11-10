package com.farlands.g2.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Far-coordinate guard for the weather column loop.
 *
 * <p>Vanilla builds rain/snow columns with</p>
 * <pre>
 * for (int x = cameraBlockX - radius; x &lt;= cameraBlockX + radius; x++)
 * </pre>
 * <p>At {@code cameraBlockX = Integer.MAX_VALUE} the {@code x++} overflows to a
 * negative value while the (already wrapped) upper bound still compares true, so
 * the loop runs about 2^32 times - the render thread hangs inside
 * {@code ClientChunkCache.getChunk} (observed: 190 s wall, 89 s CPU, JFR 83% in
 * {@code getChunk}). Weather is purely cosmetic, so far columns are skipped;
 * this is recorded as a distance phenomenon, not hidden.</p>
 */
@Mixin(WeatherEffectRenderer.class)
public class WeatherEffectFarGuardMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void farlands$skipFarWeather(ClientLevel level, float partialTicks, Vec3 cameraPos,
        WeatherRenderState renderState, CallbackInfo ci) {
        long x = (long) Math.floor(cameraPos.x);
        long z = (long) Math.floor(cameraPos.z);
        long margin = Integer.MAX_VALUE - 64L;
        if (Math.abs(x) > margin || Math.abs(z) > margin) {
            // Keep the previous frame's state consistent: an empty column set.
            renderState.rainColumns.clear();
            renderState.snowColumns.clear();
            ci.cancel();
        }
    }
}
