package com.farlands.g2.mixin;

import com.farlands.g2.runtime.G2Runtime;
import com.farlands.g2.runtime.YBandOverrides;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Loads the persisted vertical-window overrides before the server creates its
 * levels/chunks. Without this the first chunks of a restarted world are created
 * at the dimension band, their saved sections fall outside the window and the
 * client shows air even though the band file says otherwise (EXPERIMENTS §87).
 */
@Mixin(MinecraftServer.class)
public class MinecraftServerBandsMixin {

    @Inject(method = "createLevels", at = @At("HEAD"))
    private void farlands$loadBandsEarly(CallbackInfo ci) {
        // A new server (including world A -> world B in the same JVM) must not
        // inherit the previous world's prepared pages / follow state. Reset first,
        // then load this world's band file.
        G2Runtime.resetSession();
        YBandOverrides.ensureLoaded((MinecraftServer) (Object) this);
    }
}
