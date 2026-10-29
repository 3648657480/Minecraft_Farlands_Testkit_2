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
    private static int[] farlands$clamp(int min, int max) {
        int maxPlayable = com.farlands.g2.runtime.VerticalBounds.MAX_PLAYABLE_SECTION;
        int minPlayable = com.farlands.g2.runtime.VerticalBounds.MIN_PLAYABLE_SECTION;
        if (max > maxPlayable) {
            int shift = max - maxPlayable;
            min -= shift;
            max -= shift;
        }
        if (min < minPlayable) {
            int shift = minPlayable - min;
            min += shift;
            max += shift;
        }
        return new int[] {min, max};
    }

    @Unique
    private static int[] farlands$result(int min, int max) {
        int[] band = farlands$clamp(min, max);
        com.farlands.g2.client.BandState.setFar(Math.abs((long) band[0]) > 1_000_000L);
        return band;
    }

    @Unique
    private static int[] farlands$band() {
        if (!Boolean.getBoolean("farlands.y.window")) {
            return null;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && !mc.hasSingleplayerServer()) {
            return null; // multiplayer unsupported: keep the dimension band
        }
        // Prefer the player's own page for far Y. The per-chunk window may have
        // been pushed to a different page (prefetch/other neighbours); taking it
        // as the ViewArea band made viewY disagree with the player's page and the
        // client rendered an empty band (smoke: chunkWin 672..695, viewY 696..719).
        if (mc != null && mc.level != null && mc.player != null) {
            int playerSection = (int) Math.floorDiv(
                mc.player.blockPosition().getLongY(), 16L);
            if (playerSection < mc.level.getMinSectionY()
                || playerSection > mc.level.getMaxSectionY()) {
                int pageMin = com.farlands.g2.runtime.VerticalBounds.pageStart(playerSection, 24);
                System.out.println("[YF-BAND] source=player-page band=" + pageMin
                    + ".." + (pageMin + 23));
                System.out.flush();
                return farlands$result(pageMin, pageMin + 23);
            }
        }
        // Prefer the client chunk's own window band: it is the same band the server
        // regenerated and sent, so the section graph and the grid cannot disagree.
        if (mc != null && mc.level != null && mc.player != null) {
            var pos = mc.player.blockPosition();
            var chunk = mc.level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
            if (chunk instanceof com.farlands.g2.runtime.WindowedChunk windowed) {
                int min = windowed.farlands$windowMinSectionY();
                System.out.println("[YF-BAND] source=chunk-window band=" + min + ".."
                    + windowed.farlands$windowMaxSectionY());
                System.out.flush();
                return farlands$result(min, windowed.farlands$windowMaxSectionY());
            }
            System.out.println("[YF-BAND] source=player-section chunk="
                + (chunk == null ? "null" : chunk.getClass().getSimpleName()));
            System.out.flush();
        }
        // Centre the grid on the player section (the camera follows the player within
        // one section), so a teleport cannot leave render graph nodes outside the grid.
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft != null && minecraft.player != null) {
            int playerSection = (int) Math.floorDiv(minecraft.player.blockPosition().getLongY(), 16L);
            return farlands$result(playerSection - HALF, playerSection - HALF + 23);
        }
        if (farlands$camera != null) {
            int camSection = (int) Math.floorDiv(farlands$camera.blockPosition().getLongY(), 16L);
            return farlands$result(camSection - HALF, camSection - HALF + 23);
        }
        String center = System.getProperty("farlands.y.center");
        if (center != null && !center.isEmpty()) {
            int min = Integer.parseInt(center) - HALF;
            return farlands$result(min, min + 23);
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
