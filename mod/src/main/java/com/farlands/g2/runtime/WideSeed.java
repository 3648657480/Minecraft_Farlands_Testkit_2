package com.farlands.g2.runtime;

/**
 * Positional seed for the worldgen random factories.
 *
 * <p>Vanilla hashes the int block coordinates ({@code x * 3129871 ^ z *
 * 116129781 ^ y}, squared and mixed). At 2^31 the int view wraps, so the far
 * positive and far negative halves share seeds. This helper keeps the exact
 * int arithmetic inside the int range (bit-identical fingerprints there) and
 * uses the long coordinates outside it.</p>
 */
public final class WideSeed {

    private WideSeed() {
    }

    public static long at(long x, int y, long z) {
        if (x == (long) (int) x && z == (long) (int) z) {
            int xi = (int) x;
            int zi = (int) z;
            long seed = xi * 3129871 ^ zi * 116129781L ^ y;
            seed = seed * seed * 42317861L + seed * 11L;
            return seed >> 16;
        }
        long seed = x * 3129871L ^ z * 116129781L ^ y;
        seed = seed * seed * 42317861L + seed * 11L;
        return seed >> 16;
    }
}
