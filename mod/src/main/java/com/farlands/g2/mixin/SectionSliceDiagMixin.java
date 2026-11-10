package com.farlands.g2.mixin;

import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Diagnostic ({@code -Dfarlands.trace.slice=true}): counts slice lookups per
 * second. A compiled mesh whose buffers were never allocated into the uber
 * buffer yields a null slice and is silently not drawn.
 */
@Mixin(SectionRenderDispatcher.class)
public class SectionSliceDiagMixin {

    @Unique private static int farlands$calls;
    @Unique private static int farlands$nonNull;
    @Unique private static long farlands$last;

    @Inject(method = "getRenderSectionSlice", at = @At("RETURN"))
    private void farlands$count(SectionMesh sectionMesh, ChunkSectionLayer layer,
        CallbackInfoReturnable<SectionRenderDispatcher.RenderSectionBufferSlice> cir) {
        if (!Boolean.getBoolean("farlands.trace.slice")) {
            return;
        }
        farlands$calls++;
        if (cir.getReturnValue() != null) {
            farlands$nonNull++;
        }
        long now = System.currentTimeMillis();
        if (now - farlands$last > 2000L) {
            farlands$last = now;
            System.out.println("[SLICE] calls=" + farlands$calls + " nonNull=" + farlands$nonNull);
            System.out.flush();
            farlands$calls = 0;
            farlands$nonNull = 0;
        }
    }
}
