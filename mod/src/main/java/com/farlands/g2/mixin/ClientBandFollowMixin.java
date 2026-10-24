package com.farlands.g2.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Y-F dynamic: keep the client's section grid on the player's current band.
 *
 * <p>Runs at the END of {@code Minecraft.tick}, i.e. after the network tick has
 * applied teleports: a rebuild placed at the tick head would use the pre-tp
 * position and the render frame's graph nodes would fall outside the grid
 * ({@code section == null} in {@code compileSections}). The camera follows the
 * player within one section, so centring the grid on the player keeps the camera
 * inside it.</p>
 */
@Mixin(Minecraft.class)
public class ClientBandFollowMixin {

    /** Last level the wide-key table was built for; cleared when the level changes. */
    private static Object farlands$lastLevel;

    @Inject(method = "tick", at = @At("TAIL"))
    private void farlands$followBand(CallbackInfo ci) {
        if (!Boolean.getBoolean("farlands.y.window")) {
            return;
        }
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.level == null || mc.gameRenderer == null || mc.levelRenderer == null
            || mc.player == null) {
            return;
        }
        if (mc.level != farlands$lastLevel) {
            farlands$lastLevel = mc.level;
            try {
                com.farlands.g2.client.RenderKeys.clear();
            } catch (Throwable ignored) {
                // client classes only
            }
        }
        if (!mc.hasSingleplayerServer()) {
            return; // multiplayer unsupported: keep the dimension band
        }
        try {
            com.farlands.g2.client.ClientBandBridge.retry();
        } catch (Throwable ignored) {
            // client classes only; never let the bridge break the tick
        }
        var viewArea = mc.levelRenderer.viewArea();
        if (viewArea == null) {
            return;
        }
        Camera camera = mc.gameRenderer.mainCamera();
        // A ViewArea rebuilt while the camera was still uninitialised never got its
        // rotating centre, so every lookup returns null and nothing compiles.
        if (viewArea.getRenderSectionAt(mc.player.blockPosition()) == null) {
            viewArea.repositionCamera(net.minecraft.core.SectionPos.of(camera.blockPosition()));
            var playerSec = net.minecraft.core.SectionPos.of(mc.player.blockPosition());
            var midPos = new net.minecraft.core.BlockPos(mc.player.blockPosition().getX(),
                viewArea.minSectionY() * 16 + 8, mc.player.blockPosition().getZ());
            System.out.println("[YF-REPOS] playerSec=" + playerSec
                + " center=" + viewArea.getCameraSectionPos()
                + " midFound=" + (viewArea.getRenderSectionAt(midPos) != null)
                + " stillAbsent=" + (viewArea.getRenderSectionAt(mc.player.blockPosition()) == null)
                + " viewY=" + viewArea.minSectionY() + ".." + viewArea.maxSectionY());
            System.out.flush();
        }
        int playerSection = Math.floorDiv(mc.player.blockPosition().getY(), 16);
        if (playerSection >= viewArea.minSectionY() + 2 && playerSection <= viewArea.maxSectionY() - 2) {
            return;
        }
        System.out.println("[YF-REBUILD] playerSection=" + playerSection + " oldView="
            + viewArea.minSectionY() + ".." + viewArea.maxSectionY());
        System.out.flush();
        mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options, camera, mc.getBlockColors());
    }
}
