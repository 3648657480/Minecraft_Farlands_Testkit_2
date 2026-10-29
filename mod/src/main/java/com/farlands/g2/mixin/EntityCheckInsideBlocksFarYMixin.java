package com.farlands.g2.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block-effect sweep at far Y.
 *
 * <p>26.2 renamed the old {@code checkInsideBlocks} sweep to
 * {@code applyEffectsFromBlocks}; the previous guard targeted the removed name
 * (the remap warned "checkInsideBlocks()V does not exist"), so the fix was dead.
 * The sweep's range maths is int and overflows at y≈2^31, turning it into an
 * enormous loop of chunk lookups (observed: server thread RUNNABLE, tick
 * stalled). Vanilla block effects do not apply at far Y, so skip the sweep.</p>
 */
@Mixin(Entity.class)
public class EntityCheckInsideBlocksFarYMixin {

    @Inject(method = {"applyEffectsFromBlocks()V", "applyEffectsFromBlocksForLastMovements()V"},
        at = @At("HEAD"), cancellable = true)
    private void farlands$skipFarY(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (Math.abs((long) self.getY()) > 1_000_000L) {
            ci.cancel();
        }
    }

    @Inject(method = "applyEffectsFromBlocks(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V",
        at = @At("HEAD"), cancellable = true)
    private void farlands$skipFarYMove(Vec3 from, Vec3 to, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (Math.abs((long) self.getY()) > 1_000_000L) {
            ci.cancel();
        }
    }
}
