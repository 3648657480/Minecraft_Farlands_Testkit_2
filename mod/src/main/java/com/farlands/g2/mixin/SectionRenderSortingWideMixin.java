package com.farlands.g2.mixin;

import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/** Translucent vertex sorting must use the wide section origin. */
@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection")
public class SectionRenderSortingWideMixin {

    /**
     * @author G2
     * @reason SectionPos.minBlockX() is int and wraps at section 2^27, so the
     *         camera-to-section vector used for water/translucent sorting points
     *         ~4.29G blocks the wrong way.
     */
    @Overwrite
    private VertexSorting createVertexSorting(SectionPos sectionPos, Vec3 cameraPos) {
        return VertexSorting.byDistance(
            (float) (cameraPos.x - (double) sectionPos.origin().getLongX()),
            (float) (cameraPos.y - (double) sectionPos.origin().getLongY()),
            (float) (cameraPos.z - (double) sectionPos.origin().getLongZ())
        );
    }
}
