package com.farlands.g2.runtime;

/**
 * One-shot diagnostic ({@code -Dfarlands.diag.flatcachelong}): prints the wide
 * quart base that {@code NoiseChunk.FlatCache} now derives from the chunk
 * origin. Near the 2^31 seam the base must be the true positive quart, not the
 * wrapped negative one.
 */
public final class FarFlatCacheDiag {

    private static volatile int logged;

    private FarFlatCacheDiag() {
    }

    public static void log(long originX, long originZ, long baseQuartX, long baseQuartZ) {
        if (!Boolean.getBoolean("farlands.diag.flatcachelong")) {
            return;
        }
        boolean nearSeam = Math.abs(originX - 2147483648L) < 64L;
        if (logged >= 30 || (logged >= 2 && !nearSeam)) {
            return;
        }
        logged++;
        System.out.println("[FLATCACHELONG] originX=" + originX + " originZ=" + originZ
            + " baseQuartX=" + baseQuartX + " baseQuartZ=" + baseQuartZ + " nearSeam=" + nearSeam);
        System.out.flush();
    }
}
