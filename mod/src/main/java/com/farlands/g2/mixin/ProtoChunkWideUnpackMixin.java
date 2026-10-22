package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Post-processing (fluid/crop tick) positions must be built from the chunk's
 * wide origin. {@code unpackOffsetCoordinates} uses {@code ChunkPos.x()/z()}
 * (the int view), so at chunk 2^31 the reconstructed BlockPos wraps and
 * {@code LevelChunk.postProcessGeneration} schedules ticks at unloaded
 * positions ("Trying to schedule tick in not loaded position ...").
 * For normal coordinates the wide and int views are identical.
 */
@Mixin(ProtoChunk.class)
public class ProtoChunkWideUnpackMixin {

    /**
     * @author G2
     * @reason wide chunk origin for post-processing positions
     */
    @Overwrite
    public static BlockPos unpackOffsetCoordinates(short packedData, int sectionY, ChunkPos chunkPos) {
        long x = (chunkPos.xLong() << 4) + (packedData & 15);
        long y = ((long) sectionY << 4) + (packedData >>> 4 & 15);
        long z = (chunkPos.zLong() << 4) + (packedData >>> 8 & 15);
        return new BlockPos(x, y, z);
    }
}
