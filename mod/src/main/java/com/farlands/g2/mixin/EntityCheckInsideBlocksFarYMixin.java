package com.farlands.g2.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block-effect sweep at far Y.
 *
 * <p>{@code checkInsideBlocks} asks {@code forEachBlockIntersectedBetween} to
 * visit every block of the entity box; the range maths is int and overflows at
 * y≈2^31, turning the sweep into an enormous loop of chunk lookups (observed:
 * server thread RUNNABLE, tick stalled). Vanilla block effects do not apply at
 * far Y, so skip the sweep there.</p>
 */
@Mixin(Entity.class)
public class EntityCheckInsideBlocksFarYMixin {

    @Inject(method = "checkInsideBlocks()V", at = @At("HEAD"), cancellable = true)
    private void farlands$skipFarY(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (Math.abs((long) self.getY()) > 1_000_000L) {
            ci.cancel();
        }
    }
}