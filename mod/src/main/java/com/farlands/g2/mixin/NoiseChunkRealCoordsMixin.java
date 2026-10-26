package com.farlands.g2.mixin;

import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Real-coordinate accessors for {@code NoiseChunk} (which doubles as the
 * noise pipeline's {@code FunctionContext}).
 *
 * <p>All unwrapping is delegated to {@link FarProjection}: on generation
 * threads the patched {@code forChunk} publishes the wide chunk origin and
 * the accessors rebuild continuous real coordinates (both half-axes); on a
 * stable client jar no origin is ever published and the unsigned fallback
 * applies, so one mod jar serves both client jar states.</p>
 */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkRealCoordsMixin implements com.farlands.g2.runtime.WideContext {

    @Shadow private int cellStartBlockX;
    @Shadow private int cellStartBlockY;
    @Shadow private int cellStartBlockZ;
    @Shadow private int inCellX;
    @Shadow private int inCellY;
    @Shadow private int inCellZ;

    /** Per-instance long origin (constructor argument); no ThreadLocal, no nesting hazard. */
    @Shadow private long farlands$originX;
    @Shadow private long farlands$originZ;

    public double getBlockXDouble() {
        long offset = (long) this.cellStartBlockX - (long) (int) this.farlands$originX + this.inCellX;
        return (double) (this.farlands$originX + offset);
    }

    public double getBlockYDouble() {
        return (double) (cellStartBlockY + inCellY);
    }

    public double getBlockZDouble() {
        long offset = (long) this.cellStartBlockZ - (long) (int) this.farlands$originZ + this.inCellZ;
        return (double) (this.farlands$originZ + offset);
    }
}
