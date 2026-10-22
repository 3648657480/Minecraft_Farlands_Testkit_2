package com.farlands.g2.mixin;

import com.farlands.g2.runtime.WindowFollower;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Y-D: run the player-follow window check once per server tick. */
@Mixin(MinecraftServer.class)
public class ServerTickFollowMixin {

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void farlands$followY(BooleanSupplier hasTime, CallbackInfo ci) {
        WindowFollower.tick((MinecraftServer) (Object) this);
    }
}
