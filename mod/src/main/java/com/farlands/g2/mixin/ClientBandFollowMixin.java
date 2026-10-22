package com.farlands.g2.mixin;

import com.farlands.g2.runtime.WindowedChunk;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Y-F dynamic: rebuild the client's view area when the camera enters a chunk
 * whose window band differs from the one the view area currently covers.
 */
@Mixin(Minecraft.class)
public class ClientBandFollowMixin {

    @Unique private static int farlands$lastBand = Integer.MIN_VALUE;

    @Inject(method = "tick", at = @At("HEAD"))
    private void farlands$followBand(CallbackInfo ci) {
        if (!Boolean.getBoolean("farlands.y.window")) {
            return;
        }
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.level == null || mc.gameRenderer == null || mc.levelRenderer == null) {
            return;
        }
        Camera camera = mc.gameRenderer.mainCamera();
        var pos = camera.blockPosition();
        var chunk = mc.level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
            ChunkStatus.FULL, false);
        if (!(chunk instanceof WindowedChunk windowed)) {
            return;
        }
        int band = windowed.farlands$windowMinSectionY();
        if (band == farlands$lastBand) {
            return;
        }
        farlands$lastBand = band;
        mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options, camera, mc.getBlockColors());
    }
}
