package com.farlands.g2.mixin;

import com.farlands.g2.client.WideRenderBase;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.Position;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The section update tracker owns its own rotating grid (dirty flags, neighbour
 * gate, light fill). It must rotate in the same local frame as the view grid,
 * otherwise it keeps dirtying slots for world sections the view grid never
 * holds and far sections never compile.
 */
@Mixin(LevelExtractor.class)
public class LevelExtractorLocalFrameMixin {

    /**
     * Camera-position inputs here can be one frame stale (the render state still
     * carries the previous teleport target). Feeding a stale far position into the
     * local-frame subtraction produces a centre like {@code -base}, and the grid
     * then re-centres every frame - 650+ full re-keys, chunks reloading, no render.
     * When the incoming position is far away from the live camera, use the live one.
     */
    @Unique
    private static SectionPos farlands$localOrVanilla(Position pos) {
        var cam = net.minecraft.client.Minecraft.getInstance()
            .gameRenderer.mainCamera().position();
        if (Math.abs(pos.x() - cam.x) > 1.0E7 || Math.abs(pos.z() - cam.z) > 1.0E7) {
            SectionPos live = WideRenderBase.localSectionOf(cam.x, cam.y, cam.z);
            return live != null ? live : SectionPos.of(cam);
        }
        SectionPos local = WideRenderBase.localSectionOf(pos.x(), pos.y(), pos.z());
        return local != null ? local : SectionPos.of(pos);
    }

    @Redirect(method = "extract",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/core/Position;)Lnet/minecraft/core/SectionPos;"))
    private static SectionPos farlands$trackerCameraSection(Position pos) {
        return farlands$localOrVanilla(pos);
    }

    @Redirect(method = "allChanged",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/core/Position;)Lnet/minecraft/core/SectionPos;"))
    private static SectionPos farlands$trackerRebuildSection(Position pos) {
        return farlands$localOrVanilla(pos);
    }
}
