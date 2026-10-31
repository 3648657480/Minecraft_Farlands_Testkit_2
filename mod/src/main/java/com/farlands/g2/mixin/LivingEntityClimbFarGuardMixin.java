package com.farlands.g2.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * At far X/Z the climb probe asks the server chunk cache for a block state.
 * Before the wide chunk is resident that call blocks the server thread in
 * managedBlock. Player-only preview treats far columns as non-climbable.
 */
@Mixin(LivingEntity.class)
public class LivingEntityClimbFarGuardMixin {

    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void farlands$guardClimb(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (Math.abs(self.getX()) > 2.0E9 || Math.abs(self.getZ()) > 2.0E9) {
            cir.setReturnValue(false);
        }
    }
}
