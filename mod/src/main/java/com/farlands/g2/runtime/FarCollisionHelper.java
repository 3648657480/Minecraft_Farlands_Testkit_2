package com.farlands.g2.runtime;

import com.scalarwide.mc.WideChunkPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CollisionGetter;

/**
 * W1 collision chunk access. {@code BlockCollisions} already carries long block
 * coordinates, but its chunk lookup still narrows to {@code int} and
 * {@code ChunkPos.pack(int,int)}; at block 2^53 (chunk 2^49) that returns chunk 0.
 * This helper keeps the normal int path unchanged and uses the wide chunk map
 * only when the chunk coordinate does not fit int.
 */
public final class FarCollisionHelper {

    private FarCollisionHelper() {
    }

    public static BlockGetter getChunkForCollisions(CollisionGetter getter,
        long blockX, long blockZ) {
        long chunkX = Math.floorDiv(blockX, 16L);
        long chunkZ = Math.floorDiv(blockZ, 16L);
        if (chunkX == (long) (int) chunkX && chunkZ == (long) (int) chunkZ) {
            return getter.getChunkForCollisions((int) chunkX, (int) chunkZ);
        }
        if (getter instanceof ServerLevel server) {
            WideChunkPos wide = WideChunkPos.of(chunkX, chunkZ);
            return server.getChunkSource().farlands$getChunkWide(new ChunkPos(wide));
        }
        // No wide client path yet: preserve the old behavior rather than
        // silently changing normal worlds.
        return getter.getChunkForCollisions((int) chunkX, (int) chunkZ);
    }
}
