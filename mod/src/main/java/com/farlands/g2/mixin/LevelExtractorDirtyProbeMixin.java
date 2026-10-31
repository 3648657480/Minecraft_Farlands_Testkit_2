package com.farlands.g2.mixin;

import com.farlands.g2.client.RenderKeys;
import com.farlands.g2.runtime.section.SectionKey;
import net.minecraft.client.SectionUpdateTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A single measurement of the four dirty/compile conjuncts for far X sections. */
@Mixin(LevelExtractor.class)
public class LevelExtractorDirtyProbeMixin {

    @Shadow private LevelRenderer levelRenderer;
    @Shadow private ClientLevel level;
    @Shadow private SectionUpdateTracker sectionUpdateTracker;
    @Shadow @Final private LevelRenderState levelRenderState;

    @org.spongepowered.asm.mixin.Unique
    private static int farlands$logged;

    @Inject(method = "extract", at = @At("RETURN"))
    private void farlands$dirtyProbe(CallbackInfo ci) {
        if (farlands$logged >= 12 || levelRenderer == null
            || sectionUpdateTracker == null || level == null) {
            return;
        }
        for (var section : levelRenderer.visibleSections()) {
            SectionKey key = RenderKeys.keyOf(section.getSectionNode());
            if (key == null) {
                continue;
            }
            boolean far = key.lx != (long) (int) key.lx || key.ly != (long) (int) key.ly
                || key.lz != (long) (int) key.lz;
            if (!far) {
                continue;
            }
            var dirty = sectionUpdateTracker.getDirtyState(section.getSectionNode());
            boolean inStates = false;
            for (var state : levelRenderState.sectionUpdateRenderStates) {
                if (state.sectionNode() == section.getSectionNode()) {
                    inStates = true;
                    break;
                }
            }
            System.out.println("[DIRTYPROBE] node=" + section.getSectionNode()
                + " key=" + key
                + " pos=" + SectionPos.x(section.getSectionNode()) + ","
                + SectionPos.y(section.getSectionNode()) + ","
                + SectionPos.z(section.getSectionNode())
                + " dirty=" + (dirty != null)
                + " isDirty=" + (dirty != null && dirty.isDirty())
                + " mesh=" + (section.getSectionMesh() != CompiledSectionMesh.UNCOMPILED)
                + " neighbors=" + sectionUpdateTracker.hasAllNeighbors(level,
                    section.getSectionNode())
                + " inStates=" + inStates);
            System.out.flush();
            if (++farlands$logged >= 12) {
                break;
            }
        }
    }
}
