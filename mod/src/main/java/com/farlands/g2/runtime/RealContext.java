package com.farlands.g2.runtime;

import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * FunctionContext for noise/climate sampling.
 *
 * <p>The long constructor is the authoritative one: block coordinates are kept
 * exactly (no int wrap, no ThreadLocal origin guessing) and the double accessors
 * feed the density/climate functions. The int accessors are views kept for
 * cache indexing. The int constructor preserves the legacy origin-aware unwrap
 * for call sites that still pass wrapped ints.</p>
 */
public final class RealContext implements DensityFunction.FunctionContext, WideContext {

    private final long lx;
    private final long ly;
    private final long lz;
    private final boolean exact;

    public RealContext(int x, int y, int z) {
        this.lx = x;
        this.ly = y;
        this.lz = z;
        this.exact = false;
    }

    public RealContext(long x, long y, long z) {
        this.lx = x;
        this.ly = y;
        this.lz = z;
        this.exact = true;
    }

    public static RealContext of(net.minecraft.core.BlockPos pos) {
        return new RealContext(pos.getLongX(), pos.getLongY(), pos.getLongZ());
    }

    @Override
    public int blockX() {
        return (int) lx;
    }

    @Override
    public int blockY() {
        return (int) ly;
    }

    @Override
    public int blockZ() {
        return (int) lz;
    }

    public double getBlockXDouble() {
        return this.exact ? (double) lx : com.farlands.g2.util.FarProjection.unwrapX((int) lx);
    }

    public double getBlockYDouble() {
        return (double) ly;
    }

    public double getBlockZDouble() {
        return this.exact ? (double) lz : com.farlands.g2.util.FarProjection.unwrapZ((int) lz);
    }
}
