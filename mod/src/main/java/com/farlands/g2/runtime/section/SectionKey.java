package com.farlands.g2.runtime.section;

import com.scalarwide.mc.WideSectionKey2560;

/**
 * B1 of {@code docs/RENDER-WIDE-KEYS.md}: a render node key that carries the
 * 2560-bit identity plus int views and a total order.
 *
 * <p>The wide key is the identity; {@code (x, y, z)} are the int views used for
 * ordering, neighbour arithmetic and array maths. On normal coordinates the
 * order matches the classic packed layout (x, then y, then z as primary/secondary
 * /tertiary is a free choice as long as it is total and consistent).</p>
 */
public final class SectionKey implements Comparable<SectionKey> {

    public final int x;
    public final int y;
    public final int z;
    public final WideSectionKey2560 wide;

    private SectionKey(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.wide = WideSectionKey2560.of(x, y, z);
    }

    public static SectionKey of(int x, int y, int z) {
        return new SectionKey(x, y, z);
    }

    public static SectionKey of(WideSectionKey2560 wide) {
        // Wide coordinates may exceed int; this int view is for the render band only.
        return new SectionKey(wide.x().toBigInteger().intValue(),
            wide.y().toBigInteger().intValue(), wide.z().toBigInteger().intValue());
    }

    public SectionKey offset(int dx, int dy, int dz) {
        return new SectionKey(x + dx, y + dy, z + dz);
    }

    /** Total order: x, then y, then z (stable and coordinate-monotonic). */
    @Override
    public int compareTo(SectionKey other) {
        int c = Integer.compare(this.x, other.x);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(this.y, other.y);
        if (c != 0) {
            return c;
        }
        return Integer.compare(this.z, other.z);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SectionKey key && key.x == x && key.y == y && key.z == z;
    }

    @Override
    public int hashCode() {
        int h = x;
        h = 31 * h + y;
        h = 31 * h + z;
        return h;
    }

    @Override
    public String toString() {
        return "SectionKey[" + x + ", " + y + ", " + z + "]";
    }
}
