package com.farlands.g2.mixin;

import com.farlands.g2.client.RenderSync;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Rebuilds the visible-section list when the view grid centre moved, not only
 * when the camera rotation changed.
 *
 * <p>Vanilla reaches this branch on {@code consumeFrustumUpdate()} or a rotation
 * delta. The fork's {@code SectionOcclusionGraph.update} patch removed the
 * vanilla scheduling, so the flag never became true; at far coordinates the grid
 * moves once per section and the list went stale until the player looked
 * around (holes at the leading edge, edges popping in and out while turning).</p>
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorViewSyncMixin {

    private static int farlands$forced;

    @Redirect(method = "extract",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;consumeFrustumUpdate()Z"))
    private boolean farlands$refillOnCenterMove(SectionOcclusionGraph graph) {
        boolean consumed = graph.consumeFrustumUpdate();
        boolean centerMoved = RenderSync.consumeCenterChanged();
        if (centerMoved && Boolean.getBoolean("farlands.diag.viewSync")
            && farlands$forced++ < 10) {
            System.out.println("[VIEWSYNC] refill forced by center move #" + farlands$forced);
            System.out.flush();
        }
        return consumed || centerMoved;
    }
}
