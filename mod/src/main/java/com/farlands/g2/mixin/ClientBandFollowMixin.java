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
        if (!mc.hasSingleplayerServer()) {
            return; // multiplayer unsupported: keep the dimension band
        }
        Camera camera = mc.gameRenderer.mainCamera();
        var viewArea = mc.levelRenderer.viewArea();
        if (viewArea == null) {
            return;
        }
        int camSection = Math.floorDiv(camera.blockPosition().getY(), 16);
        if (camSection >= viewArea.minSectionY() + 2 && camSection <= viewArea.maxSectionY() - 2) {
            return;
        }
        mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options, camera, mc.getBlockColors());
    }
}
