package com.farlands.g2.runtime;

import com.scalarwide.mc.WideChunkPos;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-chunk vertical window override (Y-D groundwork).
 *
 * <p>Keyed by the wide chunk identity; the value is the desired window centre in
 * absolute section Y. When a chunk is (re)created its window is placed here
 * first, then the global {@code -Dfarlands.y.center} test property, then the
 * dimension band.</p>
 */
public final class YBandOverrides {

    private static final Map<WideChunkPos, Integer> OVERRIDES = new ConcurrentHashMap<>();

    private YBandOverrides() {
    }

    public static void set(WideChunkPos pos, int centerSectionY) {
        OVERRIDES.put(pos, centerSectionY);
    }

    public static Integer get(WideChunkPos pos) {
        return OVERRIDES.get(pos);
    }

    public static void clear(WideChunkPos pos) {
        OVERRIDES.remove(pos);
    }
}
