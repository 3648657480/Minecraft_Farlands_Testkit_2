package com.farlands.g2.runtime;

/**
 * Exact long origin for the surface-rules evaluation of one
 * {@code SurfaceSystem.topMaterial} call. Like the aquifer grid scope, the
 * lifetime is a single method call (no nested chunk generation), so it is safe
 * to keep on the calling thread; it exists because the surface-rule noise
 * conditions only receive wrapped int doubles.
 */
public final class SurfaceWide {

    private static final ThreadLocal<long[]> CURRENT = new ThreadLocal<>();

    private SurfaceWide() {
    }

    /** @param xLong real block X of the column; @param xInt its int view */
    public static void begin(long xLong, int xInt, long zLong, int zInt) {
        CURRENT.set(new long[]{xLong, xInt, zLong, zInt});
    }

    public static void end() {
        CURRENT.remove();
    }

    /** Recover the real coordinate of a surface sample from its wrapped int value. */
    public static double unwrapX(double x) {
        long[] c = CURRENT.get();
        if (c == null) {
            return x;
        }
        return (double) (c[0] + ((long) (int) x - c[1]));
    }

    public static double unwrapZ(double z) {
        long[] c = CURRENT.get();
        if (c == null) {
            return z;
        }
        return (double) (c[2] + ((long) (int) z - c[3]));
    }
}
