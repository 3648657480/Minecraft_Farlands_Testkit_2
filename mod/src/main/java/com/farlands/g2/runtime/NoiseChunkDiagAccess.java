package com.farlands.g2.runtime;

/** Diagnostic accessors implemented by a mixin on {@code NoiseChunk}. */
public interface NoiseChunkDiagAccess {

    int farlands$inCellX();

    int farlands$inCellY();

    int farlands$inCellZ();

    int farlands$cellStartBlockY();

    int farlands$cellWidth();

    int farlands$cellHeight();

    long farlands$cellStartBlockXLong();

    long farlands$originX();
}
