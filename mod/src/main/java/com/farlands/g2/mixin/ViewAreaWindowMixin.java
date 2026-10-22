package com.farlands.g2.mixin;

import com.farlands.g2.runtime.WindowedChunk;
import net.minecraft.client.Camera;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Y-F: make the client's section grid cover the chunk window instead of the
 * dimension band.
 *
 * <p>The band is taken from the chunk under the camera (so it follows band
 * changes, Y-D/Y-F dynamic); if the camera chunk is not loaded yet, the fixed
 * {@code -Dfarlands.y.center} test band is used, and otherwise the dimension
 * band is kept.</p>
 */
@Mixin(LevelRenderer.class)
public class ViewAreaWindowMixin {

    @Unique private static final int HALF = 11;
    @Unique private static Camera farlands$camera;
    @Unique private static ClientLevel farlands$level;

    @Inject(method = "invalidateCompiledGeometry", at = @At("HEAD"))
    private void farlands$capture(ClientLevel level, Options options, Camera camera,
        BlockColors blockColors, CallbackInfo ci) {
        farlands$camera = camera;
        farlands$level = level;
    }

    @Unique
    private static int[] farlands$band() {
        if (!Boolean.getBoolean("farlands.y.window")) {
            return null;
        }
        // Centre the grid on the camera section: the camera is then always inside
        // the view area (otherwise out-of-band graph nodes resolve to null).
        if (farlands$camera != null) {
            int camSection = Math.floorDiv(farlands$camera.blockPosition().getY(), 16);
            return new int[] {camSection - HALF, camSection - HALF + 23};
        }
        String center = System.getProperty("farlands.y.center");
        if (center != null && !center.isEmpty()) {
            int min = Integer.parseInt(center) - HALF;
            return new int[] {min, min + 23};
        }
        return null;
    }

    @Redirect(method = "invalidateCompiledGeometry",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMinY()I"))
    private int farlands$minY(ClientLevel level) {
        int[] band = farlands$band();
        return band != null ? band[0] * 16 : level.getMinY();
    }

    @Redirect(method = "invalidateCompiledGeometry",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMaxY()I"))
    private int farlands$maxY(ClientLevel level) {
        int[] band = farlands$band();
        return band != null ? band[1] * 16 + 15 : level.getMaxY();
    }

    @Redirect(method = "invalidateCompiledGeometry",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMinSectionY()I"))
    private int farlands$minSectionY(ClientLevel level) {
        int[] band = farlands$band();
        return band != null ? band[0] : level.getMinSectionY();
    }

    @Redirect(method = "invalidateCompiledGeometry",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMaxSectionY()I"))
    private int farlands$maxSectionY(ClientLevel level) {
        int[] band = farlands$band();
        return band != null ? band[1] : level.getMaxSectionY();
    }
}
