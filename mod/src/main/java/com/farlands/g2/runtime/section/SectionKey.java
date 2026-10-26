package com.farlands.g2.runtime.section;

import com.scalarwide.mc.WideSectionKey2560;

/**
 * B1 of {@code docs/archive/RENDER-WIDE-KEYS.md}: a render node key that carries
 * the 2560-bit identity plus long/int views and a total order.
 *
 * <p>The wide key is the identity; {@code lx/ly/lz} are exact long views used by
 * render origin/AABB maths; {@code x/y/z} are int views kept only for legacy
 * call sites. Equality/hash/order use the wide key, so a Y value beyond int can
 * never alias a normal one.</p>
 */
public final class SectionKey implements Comparable<SectionKey> {

    public final int x;
    public final int y;
    public final int z;
    public final long lx;
    public final long ly;
    public final long lz;
    public final WideSectionKey2560 wide;

    private SectionKey(long x, long y, long z) {
        this.lx = x;
        this.ly = y;
        this.lz = z;
        this.x = (int) x;
        this.y = (int) y;
        this.z = (int) z;
        this.wide = WideSectionKey2560.of(x, y, z);
    }

    public static SectionKey of(int x, int y, int z) {
        return new SectionKey(x, y, z);
    }

    public static SectionKey of(long x, long y, long z) {
        return new SectionKey(x, y, z);
    }

    public static SectionKey of(WideSectionKey2560 wide) {
        return new SectionKey(wide.x().toBigInteger().longValueExact(),
            wide.y().toBigInteger().longValueExact(),
            wide.z().toBigInteger().longValueExact());
    }

    public SectionKey offset(int dx, int dy, int dz) {
        return new SectionKey(lx + dx, ly + dy, lz + dz);
    }

    /** Centre block of this section (for distance/centre lookups). */
    public net.minecraft.core.BlockPos centerBlock() {
        return new net.minecraft.core.BlockPos((lx << 4) + 8, (ly << 4) + 8, (lz << 4) + 8);
    }

    /** Total order: x, then y, then z (stable and coordinate-monotonic). */
    @Override
    public int compareTo(SectionKey other) {
        int c = Long.compare(this.lx, other.lx);
        if (c != 0) {
            return c;
        }
        c = Long.compare(this.ly, other.ly);
        if (c != 0) {
            return c;
        }
        return Long.compare(this.lz, other.lz);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SectionKey key && key.wide.equals(this.wide);
    }

    @Override
    public int hashCode() {
        return this.wide.hashCode();
    }

    @Override
    public String toString() {
        return "SectionKey[" + lx + ", " + ly + ", " + lz + "]";
    }
}
