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

    @Unique
    private static SectionPos farlands$localOrVanilla(Position pos) {
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
