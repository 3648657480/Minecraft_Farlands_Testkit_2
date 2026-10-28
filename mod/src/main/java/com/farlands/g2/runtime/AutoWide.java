package com.farlands.g2.runtime;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Prototype adaptive-width signed integer: narrow long fast path, fixed-limb
 * ladder (128/256/512/768/1024 bits) and BigInteger beyond.
 *
 * <p>Callers never choose a width: overflow promotes automatically. The wide
 * form is a two's-complement little-endian limb array, normalized so that equal
 * values always share the same representation (stable equals/hash).</p>
 *
 * <p>This is a performance/correctness prototype for "wide identity with a long
 * fast path"; it is not wired into world storage yet.</p>
 */
public final class AutoWide implements Comparable<AutoWide> {

    /** Fixed ladder in limbs (128, 256, 512, 768, 1024 bits). */
    private static final int[] LADDER = {2, 4, 8, 12, 16};
    private static final int MAX_LIMBS = 16;

    private final long small;
    private final long[] limbs;
    private final BigInteger big;

    private AutoWide(long small, long[] limbs, BigInteger big) {
        this.small = small;
        this.limbs = limbs;
        this.big = big;
    }

    public static final AutoWide ZERO = new AutoWide(0L, null, null);
    public static final AutoWide ONE = new AutoWide(1L, null, null);

    public static AutoWide of(long value) {
        return new AutoWide(value, null, null);
    }

    public static AutoWide of(BigInteger value) {
        if (value.bitLength() < 64) {
            return of(value.longValue());
        }
        if (value.bitLength() + 1 > MAX_LIMBS * 64) {
            return new AutoWide(0L, null, value);
        }
        int width = widthFor(value);
        return new AutoWide(0L, trim(limbsOf(value, width)), null);
    }

    public boolean isSmall() {
        return this.big == null && this.limbs == null;
    }

    public long smallValue() {
        if (!isSmall()) {
            throw new IllegalStateException("not narrow");
        }
        return this.small;
    }

    public long longValueExact() {
        if (isSmall()) {
            return this.small;
        }
        if (this.big != null) {
            return this.big.longValueExact();
        }
        if (this.limbs.length > 2) {
            throw new ArithmeticException("does not fit long");
        }
        long lo = this.limbs[0];
        if (this.limbs.length == 2 && this.limbs[1] != (lo < 0L ? -1L : 0L)) {
            throw new ArithmeticException("does not fit long");
        }
        return lo;
    }

    public BigInteger toBigInteger() {
        if (this.big != null) {
            return this.big;
        }
        if (this.limbs == null) {
            return BigInteger.valueOf(this.small);
        }
        byte[] bytes = new byte[this.limbs.length * 8];
        for (int i = 0; i < this.limbs.length; i++) {
            long limb = this.limbs[this.limbs.length - 1 - i];
            int base = i * 8;
            for (int b = 0; b < 8; b++) {
                bytes[base + (7 - b)] = (byte) (limb >>> (8 * b));
            }
        }
        return new BigInteger(bytes);
    }

    public AutoWide add(AutoWide other) {
        if (isSmall() && other.isSmall()) {
            try {
                return of(Math.addExact(this.small, other.small));
            } catch (ArithmeticException overflow) {
                return wideAdd(this, other);
            }
        }
        return wideAdd(this, other);
    }

    public AutoWide subtract(AutoWide other) {
        return add(other.negate());
    }

    public AutoWide negate() {
        if (isSmall()) {
            return this.small == Long.MIN_VALUE
                ? of(BigInteger.valueOf(this.small).negate())
                : of(-this.small);
        }
        if (this.big != null) {
            return of(this.big.negate());
        }
        long[] r = new long[this.limbs.length];
        long carry = 1L;
        for (int i = 0; i < r.length; i++) {
            long v = ~this.limbs[i];
            long sum = v + carry;
            carry = carry != 0L && sum == 0L ? 1L : 0L;
            r[i] = sum;
        }
        return new AutoWide(0L, trim(r), null);
    }

    @Override
    public int compareTo(AutoWide other) {
        if (isSmall() && other.isSmall()) {
            return Long.compare(this.small, other.small);
        }
        if (this.big != null || other.big != null) {
            return toBigInteger().compareTo(other.toBigInteger());
        }
        long[] x = limbsOf(this);
        long[] y = limbsOf(other);
        int signX = x[x.length - 1] < 0L ? -1 : (isZero(x) ? 0 : 1);
        int signY = y[y.length - 1] < 0L ? -1 : (isZero(y) ? 0 : 1);
        if (signX != signY) {
            return Integer.compare(signX, signY);
        }
        int n = Math.max(x.length, y.length);
        for (int i = n - 1; i >= 0; i--) {
            long a = i < x.length ? x[i] : (signX < 0 ? -1L : 0L);
            long b = i < y.length ? y[i] : (signY < 0 ? -1L : 0L);
            int cmp = Long.compareUnsigned(a, b);
            if (cmp != 0) {
                return signX < 0 ? -cmp : cmp;
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AutoWide other)) {
            return false;
        }
        if (isSmall() || other.isSmall()) {
            return isSmall() && other.isSmall() && this.small == other.small;
        }
        if (this.big != null || other.big != null) {
            return toBigInteger().equals(other.toBigInteger());
        }
        return Arrays.equals(this.limbs, other.limbs);
    }

    @Override
    public int hashCode() {
        if (isSmall()) {
            return Long.hashCode(this.small);
        }
        if (this.big != null) {
            return this.big.hashCode();
        }
        return Arrays.hashCode(this.limbs);
    }

    @Override
    public String toString() {
        if (isSmall()) {
            return Long.toString(this.small);
        }
        return toBigInteger().toString();
    }

    public int bitWidth() {
        if (isSmall()) {
            return 64;
        }
        if (this.big != null) {
            return Math.max(1025, this.big.bitLength() + 1);
        }
        return this.limbs.length * 64;
    }

    // ---------------------------------------------------------------- internals

    private static AutoWide wideAdd(AutoWide a, AutoWide b) {
        if (a.big != null || b.big != null) {
            return of(a.toBigInteger().add(b.toBigInteger()));
        }
        long[] x = limbsOf(a);
        long[] y = limbsOf(b);
        int n = Math.max(x.length, y.length) + 1;
        long[] r = new long[n];
        long carry = 0L;
        for (int i = 0; i < n; i++) {
            long xi = i < x.length ? x[i] : (x[x.length - 1] < 0L ? -1L : 0L);
            long yi = i < y.length ? y[i] : (y[y.length - 1] < 0L ? -1L : 0L);
            long sum = xi + yi;
            long c1 = Long.compareUnsigned(sum, xi) < 0 ? 1L : 0L;
            long sum2 = sum + carry;
            long c2 = carry != 0L && sum2 == 0L ? 1L : 0L;
            r[i] = sum2;
            carry = c1 | c2;
        }
        return narrowOrKeep(r);
    }

    private static AutoWide narrowOrKeep(long[] raw) {
        long[] t = trim(raw);
        if (t.length == 1) {
            return of(t[0]);
        }
        if (t.length == 2 && t[1] == (t[0] < 0L ? -1L : 0L)) {
            return of(t[0]);
        }
        if (t.length > MAX_LIMBS) {
            return of(toBig(t));
        }
        return new AutoWide(0L, t, null);
    }

    private static BigInteger toBig(long[] limbs) {
        byte[] bytes = new byte[limbs.length * 8];
        for (int i = 0; i < limbs.length; i++) {
            long limb = limbs[limbs.length - 1 - i];
            int base = i * 8;
            for (int b = 0; b < 8; b++) {
                bytes[base + (7 - b)] = (byte) (limb >>> (8 * b));
            }
        }
        return new BigInteger(bytes);
    }

    private static long[] limbsOf(AutoWide value) {
        if (value.limbs != null) {
            return value.limbs;
        }
        return new long[]{value.small};
    }

    private static boolean isZero(long[] limbs) {
        for (long limb : limbs) {
            if (limb != 0L) {
                return false;
            }
        }
        return true;
    }

    private static int widthFor(BigInteger value) {
        int bits = value.bitLength() + 1;
        for (int limbs : LADDER) {
            if (bits <= limbs * 64) {
                return limbs;
            }
        }
        return MAX_LIMBS;
    }

    private static long[] limbsOf(BigInteger value, int limbs) {
        long[] out = new long[limbs];
        BigInteger mask = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
        BigInteger tmp = value;
        for (int i = 0; i < limbs; i++) {
            out[i] = tmp.and(mask).longValue();
            tmp = tmp.shiftRight(64);
        }
        return out;
    }

    private static long[] trim(long[] limbs) {
        int end = limbs.length;
        while (end > 1) {
            long top = limbs[end - 1];
            long next = end >= 2 ? limbs[end - 2] : 0L;
            if ((top == 0L && next >= 0L) || (top == -1L && next < 0L)) {
                end--;
            } else {
                break;
            }
        }
        return end == limbs.length ? limbs : Arrays.copyOf(limbs, end);
    }
}
