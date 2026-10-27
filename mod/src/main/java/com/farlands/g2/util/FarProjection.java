package com.farlands.g2.util;

/**
 * Coordinate projection for the wide engine: a generation origin (wide chunk
 * origin) plus the local int domain, expressed as double (sampling).
 *
 * <p>The E-line epoch is gone: there is no offset applied to the int domain.
 * The only projection left is origin-aware unwrapping for generation threads,
 * which is an identity for normal coordinates.</p>
 *
 * <p>Do not reintroduce an epoch / local-domain origin here: it was removed on
 * 2026-10-22. See {@code docs/TOMBSTONE.md}.</p>
 */
public final class FarProjection {

    /** Generation origin for the current chunk (set by the NoiseChunk patch). */
    private static final ThreadLocal<Long> ORIGIN_X = new ThreadLocal<>();
    private static final ThreadLocal<Long> ORIGIN_Z = new ThreadLocal<>();

    private FarProjection() {
    }

    /** Called by the patched NoiseChunk.forChunk before generation starts. */
    public static void setGenerationOrigin(long originX, long originZ) {
        ORIGIN_X.set(originX);
        ORIGIN_Z.set(originZ);
    }

    /**
     * Seed offset for integer-domain carvers/features at a far generation
     * origin: 0 near the origin (vanilla behavior bit-identical), otherwise a
     * deterministic mix of the origin chunk coordinates so far-domain carvers
     * and features do not repeat with the local int window.
     */
    public static long generationSeedOffset() {
        Long ox = ORIGIN_X.get();
        Long oz = ORIGIN_Z.get();
        if (ox != null && oz != null
                && (ox < -100_000_000L || ox > 100_000_000L
                    || oz < -100_000_000L || oz > 100_000_000L)) {
            return (ox >> 4) * 341873128712L + (oz >> 4) * 132897987541L;
        }
        return 0L;
    }

    /**
     * G2 real-coordinate engine: the wide/real terrain engine is always live.
     * Kept as a named gate for the B-line real-coordinate-ization (surface
     * rules, geode/end-island density, far-random, collision domain, render cam).
     */
    public static boolean isFarEngine() {
        return true;
    }

    /**
     * Origin-aware unwrap: when a generation origin is set, interpret the
     * wrapped value as the coordinate nearest to the origin; otherwise return
     * the signed value unchanged.
     */
    public static double unwrapX(int v) {
        Long origin = ORIGIN_X.get();
        farlands$logUnwrap(0, v, origin);
        if (origin != null && (origin < -100_000_000L || origin > 100_000_000L)) {
            return applySamplePolicy((double) (origin + (v - (int) (long) origin)), 0);
        }
        return (double) v;
    }

    public static double unwrapZ(int v) {
        Long origin = ORIGIN_Z.get();
        farlands$logUnwrap(1, v, origin);
        if (origin != null && (origin < -100_000_000L || origin > 100_000_000L)) {
            return applySamplePolicy((double) (origin + (v - (int) (long) origin)), 1);
        }
        return (double) v;
    }

    /** Diagnostic for -Dfarlands.diag.unwraplog: catches far unwraps with no generation origin. */
    private static final java.util.concurrent.atomic.AtomicInteger farlands$unwrapLogs =
        Boolean.getBoolean("farlands.diag.unwraplog")
            ? new java.util.concurrent.atomic.AtomicInteger() : null;

    private static void farlands$logUnwrap(int axis, int v, Long origin) {
        if (farlands$unwrapLogs == null || v > -1_000_000_000) {
            return;
        }
        if (farlands$unwrapLogs.incrementAndGet() > 60) {
            return;
        }
        System.out.println("[UNWRAP] axis=" + (axis == 1 ? "Z" : "X") + " v=" + v
            + " origin=" + origin
            + " => " + (origin == null ? (double) v
                : (double) (origin + (v - (int) (long) origin))));
        System.out.flush();
    }

    /**
     * Worldgen sampling policy (EXPERIMENTAL, user-configurable) plus the
     * pro experimental transform.
     *
     * <p>The pro transform runs first and defaults to a strict no-op:</p>
     * <ul>
     *   <li>{@code pro_sample_scale} multiplies the horizontal coordinate</li>
     *   <li>{@code pro_sample_offset_x/z} is added afterwards</li>
     * </ul>
     *
     * <p>Beyond {@code worldgen_far_threshold} (default 2^53) the real sample
     * coordinate is transformed according to {@code worldgen_sample_mode}:</p>
     * <ul>
     *   <li>{@code raw} - untouched (native phenomena; default)</li>
     *   <li>{@code clamp} - clamped to +/-{@code worldgen_sample_clamp}</li>
     *   <li>{@code quantize} - snapped to the 2^53 grid</li>
     * </ul>
     *
     * @param axis 0 = X, 1 = Z (pro offsets differ per axis)
     */
    private static double applySamplePolicy(double real, int axis) {
        if (!isFarEngine()) {
            return real;
        }
        double scale = com.farlands.g2.util.FarConfig.proSampleScale();
        if (scale != 1.0) {
            real *= scale;
        }
        double proOff = axis == 1
            ? com.farlands.g2.util.FarConfig.proSampleOffsetZ()
            : com.farlands.g2.util.FarConfig.proSampleOffsetX();
        if (proOff != 0.0) {
            real += proOff;
        }
        long threshold = com.farlands.g2.util.FarConfig.worldgenFarThreshold();
        if (threshold > 0 && Math.abs(real) < (double) threshold) {
            return real;
        }
        String mode = com.farlands.g2.util.FarConfig.worldgenSampleMode();
        double out;
        if ("clamp".equals(mode)) {
            double c = com.farlands.g2.util.FarConfig.worldgenSampleClamp();
            out = Math.max(-c, Math.min(c, real));
        } else if ("quantize".equals(mode)) {
            double grid = 9007199254740992.0; // 2^53
            out = Math.rint(real / grid) * grid;
        } else {
            out = real;
        }
        if (com.farlands.g2.util.FarConfig.debug() >= 3 && farlands$allowSampleLog()) {
            System.out.println("[FarLands-G2] sample axis=" + (axis == 1 ? "Z" : "X")
                + " mode=" + mode + " real=" + real + " out=" + out);
        }
        return out;
    }

    // debug>=3 per-sample log: hard-capped so a stray debug=3 can never
    // explode the log or stall the world.
    private static final java.util.concurrent.atomic.AtomicInteger farlands$sampleLogs =
        new java.util.concurrent.atomic.AtomicInteger();
    private static final int farlands$SAMPLE_LOG_CAP = 2000;
    private static volatile boolean farlands$sampleLogCapped = false;

    private static boolean farlands$allowSampleLog() {
        if (farlands$sampleLogCapped) {
            return false;
        }
        if (farlands$sampleLogs.incrementAndGet() <= farlands$SAMPLE_LOG_CAP) {
            return true;
        }
        farlands$sampleLogCapped = true;
        System.out.println("[FarLands-G2] per-sample log capped at "
            + farlands$SAMPLE_LOG_CAP + " lines; further samples suppressed");
        System.out.flush();
        return false;
    }

    /** Collision-domain X for the patched collision iterators. */
    public static double collisionX(int v) {
        return (double) v;
    }

    /** Collision-domain Z; see {@link #collisionX(int)}. */
    public static double collisionZ(int v) {
        return (double) v;
    }

    /** F3 display: real X (identity; the engine runs at real coordinates). */
    public static double displayX(double local) {
        return local;
    }

    /** F3 display: real Z (identity). */
    public static double displayZ(double local) {
        return local;
    }

    /** Chunk-coordinate normalization (identity at the 2^31 milestone). */
    public static int chunkNorm(int chunk) {
        return chunk;
    }
}
