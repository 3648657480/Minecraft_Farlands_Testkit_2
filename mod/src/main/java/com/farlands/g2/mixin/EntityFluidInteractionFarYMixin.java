package com.farlands.g2.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fluid interaction at far Y.
 *
 * <p>{@code update} floors the entity box with int maths and then probes every
 * block of that box through {@code getFluidState}; at y≈2^31 the section index
 * overflows and the probes fall into the far-Y section machinery, which keeps
 * the server thread busy (observed: server tick stalled, network RX 0). Vanilla
 * fluids do not exist at far Y, so skip the interaction entirely there.</p>
 */
@Mixin(EntityFluidInteraction.class)
public class EntityFluidInteractionFarYMixin {

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void farlands$skipFarY(Entity entity, boolean ignoreCurrent, CallbackInfo ci) {
        if (Math.abs((long) entity.getY()) > 1_000_000L) {
            ci.cancel();
        }
    }
}