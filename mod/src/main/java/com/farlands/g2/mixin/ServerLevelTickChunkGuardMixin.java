package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The precipitation/thunder paths derive positions and biome lookups from the
 * int chunk origin; at block 2^31 they mirror into the negative band and can
 * recursively load chunks. Guard only those paths. Random block/fluid ticks
 * stay enabled: they use the chunk's local section coordinates and now resolve
 * block access through the wide Level path.
 */
@Mixin(ServerLevel.class)
public class ServerLevelTickChunkGuardMixin {

    @Unique
    private static boolean farlands$outsideInt(long v) {
        return v < Integer.MIN_VALUE || v > Integer.MAX_VALUE;
    }

    @Inject(method = "tickPrecipitation", at = @At("HEAD"), cancellable = true)
    private void farlands$guardPrecipitation(BlockPos pos, CallbackInfo ci) {
        if (Math.abs((long) pos.getX()) > 2_147_483_600L
            || Math.abs((long) pos.getZ()) > 2_147_483_600L) {
            ci.cancel();
        }
    }

    @Inject(method = "tickThunder", at = @At("HEAD"), cancellable = true)
    private void farlands$guardThunder(LevelChunk chunk, CallbackInfo ci) {
        long x = chunk.getPos().getMinBlockXLong();
        long z = chunk.getPos().getMinBlockZLong();
        if (farlands$outsideInt(x) || farlands$outsideInt(z)) {
            ci.cancel();
        }
    }
}
