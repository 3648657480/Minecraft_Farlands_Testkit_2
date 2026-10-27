package com.farlands.g2.mixin;

import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Real-coordinate accessors for {@code NoiseChunk} (which doubles as the
 * noise pipeline's {@code FunctionContext}).
 *
 * <p>The authoritative sampling position is the long cell start published by
 * the patched {@code NoiseChunk} ({@code farlands$cellStartBlockX/ZLong});
 * the int {@code cellStartBlockX/Z} fields are only the local view used for
 * cache indexing. Using the int fields at the 2^31 seam wrapped the
 * right-hand corner of the last cell before the seam to
 * {@code Integer.MIN_VALUE}, which pulled two columns of that chunk up by
 * more than ten blocks.</p>
 */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkRealCoordsMixin implements com.farlands.g2.runtime.WideContext {

    @Shadow private int cellStartBlockY;
    @Shadow private int inCellX;
    @Shadow private int inCellY;
    @Shadow private int inCellZ;

    /** Authoritative long cell origin (chunk origin included); set by the patcher. */
    @Shadow private long farlands$cellStartBlockXLong;
    @Shadow private long farlands$cellStartBlockZLong;

    public double getBlockXDouble() {
        return (double) (this.farlands$cellStartBlockXLong + this.inCellX);
    }

    public double getBlockYDouble() {
        return (double) (cellStartBlockY + inCellY);
    }

    public double getBlockZDouble() {
        return (double) (this.farlands$cellStartBlockZLong + this.inCellZ);
    }
}
