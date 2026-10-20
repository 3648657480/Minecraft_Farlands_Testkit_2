package com.farlands.g2.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientChunkCache.class)
public class ClientChunkCacheSeamLookupMixin {

    @Unique
    private static int farlands$remap(int chunk, int playerChunk) {
        long raw = chunk;
        long player = playerChunk;
        if (Math.abs(raw - player) <= 32L) {
            return chunk;
        }
        long plus = raw + 268_435_456L;
        if (Math.abs(plus - player) <= 32L) {
            return (int) plus;
        }
        long minus = raw - 268_435_456L;
        if (Math.abs(minus - player) <= 32L) {
            return (int) minus;
        }
        return chunk;
    }

    @Unique
    private static int farlands$playerX() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? Integer.MIN_VALUE : mc.player.chunkPosition().x();
    }

    @Unique
    private static int farlands$playerZ() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? Integer.MIN_VALUE : mc.player.chunkPosition().z();
    }

    @ModifyVariable(method = "getChunk", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int farlands$remapX(int x) {
        int player = farlands$playerX();
        return player == Integer.MIN_VALUE ? x : farlands$remap(x, player);
    }

    @ModifyVariable(method = "getChunk", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int farlands$remapZ(int z) {
        int player = farlands$playerZ();
        return player == Integer.MIN_VALUE ? z : farlands$remap(z, player);
    }
}
