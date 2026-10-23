package com.farlands.g2.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hard guard for the dynamic window: the section-update list can still contain a
 * node that the current {@link ViewArea} does not cover (a teleport between the
 * graph build and this frame), and vanilla dereferences it unguarded
 * ({@code RenderSection.wasPreviouslyEmpty()} on null). Drop those entries before
 * the loop instead of crashing the render frame.
 */
@Mixin(LevelRenderer.class)
public class SectionGuardMixin {

    @Shadow @Final private LevelRenderState levelRenderState;
    @Shadow private ViewArea viewArea;

    @Inject(method = "compileSections", at = @At("HEAD"))
    private void farlands$dropOutOfGrid(CallbackInfo ci) {
        if (this.levelRenderState == null || this.viewArea == null) {
            return;
        }
        var states = this.levelRenderState.sectionUpdateRenderStates;
        if (states == null || states.isEmpty()) {
            return;
        }
        states.removeIf(state -> ((ViewAreaAccessor) this.viewArea)
            .farlands$getRenderSection(state.sectionNode()) == null);
    }
}
