package com.farlands.g2.mixin;

import com.mojang.blaze3d.vertex.VertexSorting;
import com.farlands.g2.client.WideRenderBase;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Diagnostic ({@code -Dfarlands.trace.compile=true}): logs how the section
 * compiler sees the world once the local render frame is active. Used to tell
 * "compile never runs" apart from "compile runs on an empty region".
 */
@Mixin(SectionCompiler.class)
public class SectionCompilerDiagMixin {

    @Unique private static int farlands$logs;

    @Inject(method = "compile", at = @At("HEAD"))
    private void farlands$probe(SectionPos sectionPos, RenderSectionRegion region,
        VertexSorting vertexSorting, SectionBufferBuilderPack builders,
        CallbackInfoReturnable<SectionCompiler.Results> cir) {
        if (!Boolean.getBoolean("farlands.trace.compile")
            || !WideRenderBase.established() || farlands$logs++ >= 80) {
            return;
        }
        BlockState centerState;
        try {
            centerState = region.getBlockState(sectionPos.origin().offset(8, 8, 8));
        } catch (Throwable t) {
            centerState = null;
        }
        int sampled = 0;
        try {
            for (int dy = 0; dy < 16; dy += 5) {
                for (int dx = 0; dx < 16; dx += 5) {
                    for (int dz = 0; dz < 16; dz += 5) {
                        if (!region.getBlockState(sectionPos.origin().offset(dx, dy, dz)).isAir()) {
                            sampled++;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            sampled = -1;
        }
        System.out.println("[CMP] y=" + sectionPos.y() + " pos=" + sectionPos
            + " center=" + (centerState == null ? "err" : centerState.toString())
            + " nonAirSample=" + sampled);
        System.out.flush();
    }
}
