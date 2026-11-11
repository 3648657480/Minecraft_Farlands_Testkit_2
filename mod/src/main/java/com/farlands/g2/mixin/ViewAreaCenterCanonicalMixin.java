package com.farlands.g2.mixin;

import com.farlands.g2.client.WideRenderBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Single source of truth for the view grid centre.
 *
 * <p>Two callers re-centre the grid ({@code LevelRenderer.repositionCamera} and
 * {@code invalidateCompiledGeometry}), and in a real install they can hand over
 * different positions: one live far camera, one a stale/other position (observed
 * as the centre alternating between {@code 0} and {@code -134217728}, i.e. the
 * local section of block {@code 2^31} and of block {@code -2^31}). Every flip makes
 * {@code RotatingSectionStorage} re-lay the whole grid, re-key all 1943 sections and
 * drop their meshes - the "renders only after F3+A / intermittently blank" symptom.</p>
 *
 * <p>While the local frame is established the centre is therefore always recomputed
 * from the live camera, whatever the caller passed.</p>
 */
@Mixin(ViewArea.class)
public class ViewAreaCenterCanonicalMixin {

    @Unique private static int farlands$logs;

    @ModifyVariable(method = "repositionCamera", at = @At("HEAD"), argsOnly = true)
    private SectionPos farlands$canonicalCenter(SectionPos passed) {
        if (!WideRenderBase.established()) {
            return passed;
        }
        var cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
        SectionPos live = WideRenderBase.localSectionOf(cam.x, cam.y, cam.z);
        if (live == null) {
            return passed;
        }
        if (farlands$logs++ < 16 && !live.equals(passed)) {
            System.out.println("[VAREPOS] canonical " + passed.x() + "," + passed.y()
                + "," + passed.z() + " -> " + live.x() + "," + live.y() + "," + live.z());
            System.out.flush();
        }
        return live;
    }

    @Inject(method = "repositionCamera", at = @At("HEAD"))
    private void farlands$count(SectionPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (farlands$logs < 16) {
            farlands$logs++;
            System.out.println("[VAREPOS] center=" + pos.x() + "," + pos.y() + ","
                + pos.z() + " established=" + WideRenderBase.established());
            System.out.flush();
        }
    }
}
