package com.farlands.g2.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player-only preview: non-player entities at far X/Z are not ticked. */
@Mixin(ServerLevel.class)
public class ServerLevelFarEntityTickMixin {

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void farlands$skipFarEntity(Entity entity, CallbackInfo ci) {
        if (entity instanceof ServerPlayer) {
            return;
        }
        if (Math.abs(entity.getX()) > 2.0E9 || Math.abs(entity.getZ()) > 2.0E9) {
            ci.cancel();
        }
    }
}
