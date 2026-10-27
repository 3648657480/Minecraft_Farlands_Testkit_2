package com.farlands.g2.mixin;

import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionCopy;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Diagnostic: log which section copies the render region cache requests at far X. */
@Mixin(RenderRegionCache.class)
public class RenderRegionCacheDiagMixin {

    @Inject(method = "getSectionDataCopy", at = @At("HEAD"))
    private void farlands$log(Level level, int sectionX, int sectionY, int sectionZ,
        CallbackInfoReturnable<SectionCopy> cir) {
        if (Boolean.getBoolean("farlands.diag.seccopy")
            && (Math.abs((long) sectionX) > 1_000_000L || Math.abs((long) sectionZ) > 1_000_000L)) {
            var chunk = level.getChunk(sectionX, sectionZ);
            System.out.println("[SECCOPY] req=" + sectionX + "," + sectionY + "," + sectionZ
                + " chunk=" + (chunk == null ? "null" : chunk.getPos().x() + "," + chunk.getPos().z())
                + " idx=" + chunk.getSectionIndexFromSectionY(sectionY));
            System.out.flush();
        }
    }
}