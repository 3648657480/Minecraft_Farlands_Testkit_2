package com.farlands.g2.mixin;

import com.farlands.g2.runtime.NoiseChunkDiagAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Diagnostic accessors for the fill probes (no behaviour change). */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkDiagAccessMixin implements NoiseChunkDiagAccess {

    @Shadow private int inCellX;
    @Shadow private int inCellY;
    @Shadow private int inCellZ;
    @Shadow private int cellStartBlockX;
    @Shadow private int cellStartBlockY;
    @Shadow private int cellStartBlockZ;
    @Shadow private int cellWidth;
    @Shadow private int cellHeight;
    @Shadow private int cellNoiseMinY;
    @Shadow private boolean fillingCell;
    @Shadow private int firstCellX;
    @Shadow private int firstCellZ;
    @Shadow private long farlands$cellStartBlockXLong;
    @Shadow private long farlands$cellStartBlockZLong;
    @Shadow private long farlands$originX;

    public int farlands$inCellX() {
        return this.inCellX;
    }

    public int farlands$inCellY() {
        return this.inCellY;
    }

    public int farlands$inCellZ() {
        return this.inCellZ;
    }

    public int farlands$cellStartBlockX() {
        return this.cellStartBlockX;
    }

    public int farlands$cellStartBlockY() {
        return this.cellStartBlockY;
    }

    public int farlands$cellStartBlockZ() {
        return this.cellStartBlockZ;
    }

    public int farlands$cellWidth() {
        return this.cellWidth;
    }

    public int farlands$cellHeight() {
        return this.cellHeight;
    }

    public int farlands$cellNoiseMinY() {
        return this.cellNoiseMinY;
    }

    public boolean farlands$fillingCell() {
        return this.fillingCell;
    }

    public int farlands$firstCellX() {
        return this.firstCellX;
    }

    public int farlands$firstCellZ() {
        return this.firstCellZ;
    }

    public long farlands$cellStartBlockXLong() {
        return this.farlands$cellStartBlockXLong;
    }

    public long farlands$cellStartBlockZLong() {
        return this.farlands$cellStartBlockZLong;
    }

    public long farlands$originX() {
        return this.farlands$originX;
    }
}
