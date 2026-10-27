package com.farlands.g2.runtime;

/** Diagnostic accessors implemented by a mixin on {@code NoiseChunk}. */
public interface NoiseChunkDiagAccess {

    int farlands$inCellX();

    int farlands$inCellY();

    int farlands$inCellZ();

    int farlands$cellStartBlockX();

    int farlands$cellStartBlockY();

    int farlands$cellStartBlockZ();

    int farlands$cellWidth();

    int farlands$cellHeight();

    int farlands$cellNoiseMinY();

    boolean farlands$fillingCell();

    int farlands$firstCellX();

    int farlands$firstCellZ();

    long farlands$cellStartBlockXLong();

    long farlands$cellStartBlockZLong();

    long farlands$originX();
}
