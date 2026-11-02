package com.farlands.g2.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bounds the stopServer chunk-work wait so a stuck far chunk cannot block save. */
@Mixin(MinecraftServer.class)
public class MinecraftServerStopBoundMixin {

    @Unique
    private static long farlands$stopDeadline;

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void farlands$resetStopDeadline(CallbackInfo ci) {
        farlands$stopDeadline = System.nanoTime() + 3_000_000_000L;
    }

    @Redirect(method = "lambda$stopServer$0",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;hasWork()Z"))
    private static boolean farlands$boundedHasWork(ChunkMap chunkMap) {
        if (System.nanoTime() > farlands$stopDeadline) {
            System.out.println("[STOP] chunkMap.hasWork timeout; forcing save");
            System.out.flush();
            return false;
        }
        return chunkMap.hasWork();
    }
}
