package com.farlands.g2.mixin;

import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player-only preview: far minecarts must not query chunks while unloaded. */
@Mixin(AbstractMinecart.class)
public class AbstractMinecartFarTickMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void farlands$skipFarMinecart(CallbackInfo ci) {
        AbstractMinecart self = (AbstractMinecart) (Object) this;
        if (Math.abs(self.getX()) > 2.0E9 || Math.abs(self.getZ()) > 2.0E9) {
            ci.cancel();
        }
    }
}
