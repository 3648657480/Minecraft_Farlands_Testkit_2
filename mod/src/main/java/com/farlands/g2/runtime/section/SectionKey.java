package com.farlands.g2.runtime.section;

import com.scalarwide.mc.WideSectionKey2560;
import java.math.BigInteger;

/**
 * Render node key carrying the 2560-bit identity.
 *
 * <p>The {@link WideSectionKey2560} is the sole identity; {@code bx/by/bz} are
 * exact BigInteger coordinates, and {@code lx/ly/lz}/{@code x/y/z} are explicit
 * lossy views for legacy render maths (origin/AABB). Equality, hashing and order
 * use the 2560-bit key, so coordinates beyond long can never alias a normal one.
 * The long views are deliberately not used as identity.</p>
 */
public final class SectionKey implements Comparable<SectionKey> {

    /** Exact 2560-bit identity. */
    public final WideSectionKey2560 wide;
    /** Exact section coordinates. */
    public final BigInteger bx;
    public final BigInteger by;
    public final BigInteger bz;
    /** Explicit int/long views; local render maths only. */
    public final int x;
    public final int y;
    public final int z;
    public final long lx;
    public final long ly;
    public final long lz;

    private SectionKey(BigInteger x, BigInteger y, BigInteger z) {
        this.bx = x;
        this.by = y;
        this.bz = z;
        this.wide = WideSectionKey2560.of(x, y, z);
        this.x = x.intValue();
        this.y = y.intValue();
        this.z = z.intValue();
        this.lx = x.longValue();
        this.ly = y.longValue();
        this.lz = z.longValue();
    }

    public static SectionKey of(int x, int y, int z) {
        return new SectionKey(BigInteger.valueOf(x), BigInteger.valueOf(y),
            BigInteger.valueOf(z));
    }

    public static SectionKey of(long x, long y, long z) {
        return new SectionKey(BigInteger.valueOf(x), BigInteger.valueOf(y),
            BigInteger.valueOf(z));
    }

    public static SectionKey of(BigInteger x, BigInteger y, BigInteger z) {
        return new SectionKey(x, y, z);
    }

    public static SectionKey of(WideSectionKey2560 wide) {
        return new SectionKey(wide.x().toBigInteger(), wide.y().toBigInteger(),
            wide.z().toBigInteger());
    }

    public SectionKey offset(int dx, int dy, int dz) {
        return new SectionKey(bx.add(BigInteger.valueOf(dx)), by.add(BigInteger.valueOf(dy)),
            bz.add(BigInteger.valueOf(dz)));
    }

    /** True when all exact coordinates fit in a signed long (view is lossless). */
    public boolean fitsLong() {
        return bx.bitLength() < 64 && by.bitLength() < 64 && bz.bitLength() < 64;
    }

    /** Centre block of this section (lossy view beyond long; render maths only). */
    public net.minecraft.core.BlockPos centerBlock() {
        return new net.minecraft.core.BlockPos((lx << 4) + 8, (ly << 4) + 8, (lz << 4) + 8);
    }

    /** Total order: x, then y, then z (exact). */
    @Override
    public int compareTo(SectionKey other) {
        int c = this.bx.compareTo(other.bx);
        if (c != 0) {
            return c;
        }
        c = this.by.compareTo(other.by);
        if (c != 0) {
            return c;
        }
        return this.bz.compareTo(other.bz);
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
        return "SectionKey[" + bx + ", " + by + ", " + bz + "]";
    }
}
