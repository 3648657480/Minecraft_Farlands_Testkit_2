package com.farlands.g2.mixin;

import com.farlands.g2.client.WideRenderBase;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.Position;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Feeds the rotating grid a <b>local</b> camera section instead of using the
 * int view of a far camera position ({@code Mth.floor} truncates to the low 32
 * bits, so the grid centre is not the world section).
 *
 * <p>Only the two camera-section entry points of {@code LevelRenderer} are
 * redirected; the grid itself still rotates in vanilla code
 * ({@code RotatingSectionStorage.repositionCenter}). Normal worlds, and far
 * cameras before the base is established, fall through to vanilla.</p>
 */
@Mixin(LevelRenderer.class)
public class LevelRendererLocalFrameMixin {

    /**
     * A stale camera render state (previous teleport target) fed through the
     * local-frame subtraction yields a centre like {@code -base}, so the grid
     * re-centres every frame and re-keys the whole set. Prefer the live camera
     * whenever the incoming position is far away from it.
     */
    @Unique
    private static SectionPos farlands$localOrVanilla(Position pos) {
        var cam = net.minecraft.client.Minecraft.getInstance()
            .gameRenderer.mainCamera().position();
        SectionPos out;
        if (Math.abs(pos.x() - cam.x) > 1.0E7 || Math.abs(pos.z() - cam.z) > 1.0E7) {
            SectionPos live = WideRenderBase.localSectionOf(cam.x, cam.y, cam.z);
            out = live != null ? live : SectionPos.of(cam);
        } else {
            SectionPos local = WideRenderBase.localSectionOf(pos.x(), pos.y(), pos.z());
            out = local != null ? local : SectionPos.of(pos);
        }
        if (farlands$frameLogs++ < 40) {
            System.out.println("[LFRAME] src=renderer in=" + (long) pos.x() + ","
                + (long) pos.z() + " cam=" + (long) cam.x + "," + (long) cam.z
                + " out=" + out.x() + "," + out.y() + "," + out.z()
                + " established=" + WideRenderBase.established());
            System.out.flush();
        }
        return out;
    }

    @Unique private static int farlands$frameLogs;

    /** Per-frame camera reposition ({@code LevelRenderer.repositionCamera}). */
    @Redirect(method = "repositionCamera",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/core/Position;)Lnet/minecraft/core/SectionPos;"))
    private static SectionPos farlands$localCameraSection(Position pos) {
        return farlands$localOrVanilla(pos);
    }

    /** ViewArea rebuild ({@code LevelRenderer.invalidateCompiledGeometry}). */
    @Redirect(method = "invalidateCompiledGeometry",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/core/Position;)Lnet/minecraft/core/SectionPos;"))
    private static SectionPos farlands$localRebuildSection(Position pos) {
        return farlands$localOrVanilla(pos);
    }
}
