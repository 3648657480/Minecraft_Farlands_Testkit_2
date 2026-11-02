package com.farlands.g2.mixin;

import java.util.function.Consumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player-only preview: skip every non-player entity tick at far X/Z. */
@Mixin(Level.class)
public class LevelFarEntityTickGuardMixin {

    @Inject(method = "guardEntityTick", at = @At("HEAD"), cancellable = true)
    private void farlands$guardFarEntity(Consumer<Entity> tick, Entity entity, CallbackInfo ci) {
        if (entity instanceof Player) {
            return;
        }
        if (Math.abs(entity.getX()) > 2.0E9 || Math.abs(entity.getZ()) > 2.0E9) {
            ci.cancel();
        }
    }
}
