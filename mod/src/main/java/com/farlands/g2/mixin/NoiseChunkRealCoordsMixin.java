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
    @Shadow private long farlands$originX;
    @Shadow private long farlands$originZ;

    /** Off unless -Dfarlands.diag.noisex2: logs any out-of-chunk sampling column. */
    @org.spongepowered.asm.mixin.Unique
    private static final boolean FARLANDS$NOISEX2 =
        Boolean.getBoolean("farlands.diag.noisex2");

    @org.spongepowered.asm.mixin.Unique
    private long farlands$lastLoggedX = Long.MIN_VALUE;

    public double getBlockXDouble() {
        long wx = this.farlands$cellStartBlockXLong + this.inCellX;
        if (FARLANDS$NOISEX2 && this.farlands$originX == 2147483664L
            && (wx < 2147483664L || wx >= 2147483680L) && wx != this.farlands$lastLoggedX) {
            this.farlands$lastLoggedX = wx;
            System.out.println("[NOISEX2] originX=" + this.farlands$originX
                + " originZ=" + this.farlands$originZ
                + " cellStartXLong=" + this.farlands$cellStartBlockXLong
                + " inCellX=" + this.inCellX + " => wx=" + wx);
            System.out.flush();
        }
        return (double) wx;
    }

    public double getBlockYDouble() {
        return (double) (cellStartBlockY + inCellY);
    }

    public double getBlockZDouble() {
        long wz = this.farlands$cellStartBlockZLong + this.inCellZ;
        if (FARLANDS$NOISEX2 && this.farlands$originX == 2147483664L
            && this.farlands$originZ == 288L && (wz < 288L || wz >= 304L)) {
            System.out.println("[NOISEZ2] originZ=" + this.farlands$originZ
                + " cellStartZLong=" + this.farlands$cellStartBlockZLong
                + " inCellZ=" + this.inCellZ + " => wz=" + wz);
            System.out.flush();
        }
        return (double) wz;
    }
}
