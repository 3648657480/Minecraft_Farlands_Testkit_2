package com.farlands.g2.runtime.section;

/** Patched into RenderSection: per-section wide origin, not an epoch. */
public interface WideOriginHolder {

    void g2SetWideOrigin(long x, long y, long z);

    boolean g2HasWideOrigin();

    long g2WideX();

    long g2WideY();

    long g2WideZ();
}
