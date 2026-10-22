package com.farlands.g2.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block-selection outline at far coordinates.
 *
 * <p>Vanilla translates the outline by {@code pos.getX() - cameraPos.x}: an int
 * view minus a double. East of block 2^31 the int view wraps, so the outline is
 * drawn ~4.29e9 blocks away and is invisible. This replaces the body with the
 * same logic using {@code getLongX/Y/Z}; the original method stays intact so
 * other mixins injecting into it keep compiling.</p>
 */
@Mixin(LevelRenderer.class)
public abstract class BlockOutlineWideMixin {

    /** Opt-in until the far-domain raycast/selection path is verified in game. */
    private static boolean farlands$enabled() {
        return Boolean.getBoolean("farlands.outline.wide");
    }

    private static boolean farlands$logged;
    private static int farlands$calls;
    private static int farlands$nonNull;

    @Shadow private net.minecraft.client.renderer.GameRenderer gameRenderer;

    @Shadow
    private void submitHitOutline(PoseStack poseStack, SubmitNodeCollector collector,
        net.minecraft.client.renderer.rendertype.RenderType renderType, BlockOutlineRenderState state,
        int color, float width, boolean afterTerrain) {
        throw new AssertionError();
    }

    @Inject(method = "submitBlockOutline", at = @At("HEAD"), cancellable = true)
    private void farlands$wideOutline(PoseStack poseStack, SubmitNodeCollector collector,
        LevelRenderState levelRenderState, CallbackInfo ci) {
        if (!farlands$enabled()) {
            return;
        }
        BlockOutlineRenderState state = levelRenderState.blockOutlineRenderState;
        if (Boolean.getBoolean("farlands.diag.render")) {
            farlands$calls++;
            if (state != null) {
                farlands$nonNull++;
            }
            if (farlands$calls % 200 == 0) {
                System.out.println("[RDIAG-OUTLINE-STAT] calls=" + farlands$calls + " nonNull=" + farlands$nonNull
                    + " hit=" + (net.minecraft.client.Minecraft.getInstance().hitResult == null
                        ? "null" : net.minecraft.client.Minecraft.getInstance().hitResult.getType()));
                System.out.flush();
            }
        }
        if (state == null) {
            return;
        }
        Vec3 cameraPos = levelRenderState.cameraRenderState.pos;
        BlockPos pos = state.pos();
        if (Boolean.getBoolean("farlands.diag.render") && !farlands$logged) {
            farlands$logged = true;
            System.out.println("[RDIAG-OUTLINE] posLong=" + pos.getLongX() + "," + pos.getLongY() + "," + pos.getLongZ()
                + " cam=" + cameraPos.x + "," + cameraPos.y + "," + cameraPos.z
                + " dx=" + ((double) pos.getLongX() - cameraPos.x)
                + " intDx=" + ((double) pos.getX() - cameraPos.x));
            System.out.flush();
        }
        poseStack.pushPose();
        poseStack.translate(
            (double) pos.getLongX() - cameraPos.x,
            (double) pos.getLongY() - cameraPos.y,
            (double) pos.getLongZ() - cameraPos.z);
        if (state.highContrast()) {
            this.submitHitOutline(poseStack, collector, RenderTypes.secondaryBlockOutline(), state,
                -16777216, 7.0F, state.isTranslucent());
        }
        int outlineColor = state.highContrast() ? -11010079 : ARGB.black(102);
        this.submitHitOutline(poseStack, collector, RenderTypes.lines(), state, outlineColor,
            this.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth,
            state.isTranslucent());
        poseStack.popPose();
        ci.cancel();
    }
}