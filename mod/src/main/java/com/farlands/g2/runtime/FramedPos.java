package com.farlands.g2.runtime;

import com.scalarwide.Wide128;

/**
 * Transient compute position: a wide absolute anchor plus a long local offset.
 *
 * <p>This is the "wide identity + long hot path" combination:</p>
 * <ul>
 *   <li>{@link Anchor} holds the absolute X/Z in {@link Wide128} (identity, never
 *       reconstructed from int/long views);</li>
 *   <li>the hot path adds/subtracts on {@code long} locals only;</li>
 *   <li>crossing the window is a single wide add on the anchor (fast carry),
 *       not per-object translation;</li>
 *   <li>{@link #absXWide()} is a boundary operation (TP, storage, cross-anchor
 *       comparison), never a per-tick one.</li>
 * </ul>
 *
 * <p>Invariants that keep this a <em>frame</em> and not an epoch:</p>
 * <ol>
 *   <li>never serialized, never stored in the world, never sent over the
 *       network;</li>
 *   <li>the anchor is derived per call from an absolute position (chunk origin,
 *       player chunk, algorithm frame), never configured or persisted;</li>
 *   <li>observable quantities (distance, attenuation, rendering) are computed
 *       from the absolute value, not from the local one;</li>
 *   <li>two positions with different anchors must be converted to wide before
 *       comparing or subtracting.</li>
 * </ol>
 *
 * <p>See {@code docs/TOMBSTONE.md} (transient frames are not an epoch) and
 * {@code docs/ARCHITECTURE-WIDE.md} for the type contract.</p>
 */
public final class FramedPos {

    /** Absolute anchor in wide coordinates. Shared by a batch of positions. */
    public static final class Anchor {
        private final Wide128 x;
        private final Wide128 z;

        public Anchor(Wide128 x, Wide128 z) {
            this.x = x;
            this.z = z;
        }

        public static Anchor of(long x, long z) {
            return new Anchor(Wide128.ofSigned(x), Wide128.ofSigned(z));
        }

        public Wide128 wideX() {
            return this.x;
        }

        public Wide128 wideZ() {
            return this.z;
        }

        /** Wide carry: move the anchor by a long delta. */
        public Anchor shift(long dx, long dz) {
            if (dx == 0L && dz == 0L) {
                return this;
            }
            return new Anchor(this.x.add(Wide128.ofSigned(dx)), this.z.add(Wide128.ofSigned(dz)));
        }

        /** True when the anchor fits the long domain (the normal, playable case). */
        public boolean fitsLong() {
            return this.x.toBigInteger().bitLength() < 64 && this.z.toBigInteger().bitLength() < 64;
        }
    }

    private final Anchor anchor;
    private long lx;
    private int ly;
    private long lz;

    public FramedPos(Anchor anchor, long lx, int ly, long lz) {
        this.anchor = anchor;
        this.lx = lx;
        this.ly = ly;
        this.lz = lz;
    }

    public Anchor anchor() {
        return this.anchor;
    }

    public long localX() {
        return this.lx;
    }

    public int y() {
        return this.ly;
    }

    public long localZ() {
        return this.lz;
    }

    /** Hot-path add: long only. Callers keep {@code |local|} inside the window. */
    public void add(long dx, int dy, long dz) {
        this.lx += dx;
        this.ly += dy;
        this.lz += dz;
    }

    public boolean outsideWindow(long halfWindow) {
        return Math.abs(this.lx) > halfWindow || Math.abs(this.lz) > halfWindow;
    }

    /** Wide carry: re-anchor and re-derive the locals without touching the world. */
    public FramedPos rebase(Anchor next) {
        Wide128 absX = this.anchor.wideX().add(Wide128.ofSigned(this.lx));
        Wide128 absZ = this.anchor.wideZ().add(Wide128.ofSigned(this.lz));
        return new FramedPos(next,
            absX.sub(next.wideX()).longValue(),
            this.ly,
            absZ.sub(next.wideZ()).longValue());
    }

    /** Boundary operation (TP / storage / cross-anchor compare). */
    public Wide128 absXWide() {
        return this.anchor.wideX().add(Wide128.ofSigned(this.lx));
    }

    public Wide128 absZWide() {
        return this.anchor.wideZ().add(Wide128.ofSigned(this.lz));
    }

    /** Boundary operation; anchors outside the long domain are rejected. */
    public long absXLong() {
        if (!this.anchor.fitsLong()) {
            throw new IllegalStateException("anchor does not fit long; use absXWide()");
        }
        return Math.addExact(this.anchor.wideX().longValue(), this.lx);
    }

    public long absZLong() {
        if (!this.anchor.fitsLong()) {
            throw new IllegalStateException("anchor does not fit long; use absZWide()");
        }
        return Math.addExact(this.anchor.wideZ().longValue(), this.lz);
    }

    @Override
    public String toString() {
        return "FramedPos{local=" + this.lx + "," + this.ly + "," + this.lz
            + ", anchorFitsLong=" + this.anchor.fitsLong() + "}";
    }
}