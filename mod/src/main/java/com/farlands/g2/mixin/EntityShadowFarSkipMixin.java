package com.farlands.g2.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skip entity shadow extraction in the far domain (no reliable chunk lookup). */
@Mixin(EntityRenderer.class)
public class EntityShadowFarSkipMixin {

    @Inject(method = "extractShadow", at = @At("HEAD"), cancellable = true)
    private void farlands$skipFarShadow(EntityRenderState state, Minecraft minecraft, Level level, CallbackInfo ci) {
        if (Math.abs(state.x) > 2_000_000_000.0 || Math.abs(state.z) > 2_000_000_000.0) {
            state.shadowRadius = 0.0F;
            ci.cancel();
        }
    }
}
