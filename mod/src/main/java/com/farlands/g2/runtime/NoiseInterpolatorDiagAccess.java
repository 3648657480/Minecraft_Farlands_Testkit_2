package com.farlands.g2.runtime;

/** Diagnostic accessors implemented by a mixin on {@code NoiseChunk$NoiseInterpolator}. */
public interface NoiseInterpolatorDiagAccess {

    int farlands$slice0Hash();

    int farlands$slice1Hash();

    String farlands$fillerType();

    double farlands$slice0(int z, int y);

    double farlands$slice1(int z, int y);

    double farlands$rawAt(long x, int y, long z);
}
