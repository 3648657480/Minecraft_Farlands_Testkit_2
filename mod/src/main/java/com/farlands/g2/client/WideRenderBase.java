package com.farlands.g2.client;

import java.math.BigInteger;
import net.minecraft.core.SectionPos;

/**
 * Fixed base + local render frame ({@code docs/RENDER-CONTRACT.md} §3.2).
 *
 * <p>The renderer's rotating grid is int-only. At a far camera the int view of
 * the camera section is not the world section, so the grid is fed a
 * <b>local</b> section instead: {@code local = worldSection - base}. The base is
 * the nearest multiple of 2^29 sections (the width of the X/Z fields of a packed
 * section node), so the local section stays within +-2^28 and may be negative;
 * it only changes on a re-base, which is a full rebuild.</p>
 *
 * <p>Alignment matters: with {@code base ≡ 0 (mod 2^29)} the packed node of
 * {@code base + local} still decodes to {@code local}, so every existing
 * int-node path keeps its current meaning while keys carry the true world
 * identity through {@code SectionKey = base + node}.</p>
 *
 * <p>The base is established by {@link #ensure} from the client tick and is only
 * used by the {@code LevelRenderer} / {@code LevelExtractor} redirects. Until a
 * base exists (or in normal worlds) every caller falls back to vanilla
 * coordinates.</p>
 */
public final class WideRenderBase {

    /**
     * Far threshold: 2^24 blocks. Vanilla packs a section coordinate into 22 bits
     * (SectionPos.asLong masks X/Z with 0x3FFFFF), so anything past section 2^21
     * (block 2^25 = 33.5M) aliases to a different section and the renderer reads
     * the wrong chunks. The local frame must therefore engage before that.
     */
    private static final double FAR = 1.6777216E7;
    /**
     * X/Z field width of a packed section node (22 bits). The local section must
     * fit the signed 22-bit field, so ALIGN = 2^22 and |local| <= 2^21.
     */
    private static final long ALIGN = 1L << 22;
    /** Half window: the local section must stay inside this. */
    private static final long HALF = ALIGN >> 1;

    private static boolean established;
    private static long baseX;
    private static long baseZ;
    private static int logs;

    private WideRenderBase() {
    }

    public static boolean established() {
        return established;
    }

    public static BigInteger baseX() {
        return BigInteger.valueOf(baseX);
    }

    public static BigInteger baseZ() {
        return BigInteger.valueOf(baseZ);
    }

    public static long baseXLong() {
        return baseX;
    }

    public static long baseZLong() {
        return baseZ;
    }

    public static void clear() {
        established = false;
        baseX = 0L;
        baseZ = 0L;
    }

    public static boolean isFar(double x, double z) {
        return Math.abs(x) > FAR || Math.abs(z) > FAR;
    }

    private static long section(double block) {
        return Math.floorDiv((long) Math.floor(block), 16L);
    }

    /** Nearest multiple of ALIGN, also for negative sections. */
    private static long align(long section) {
        return Math.floorDiv(section + HALF, ALIGN) * ALIGN;
    }

    /**
     * Establish or re-base the frame. Must run before the render pass of the
     * same tick so keys and camera section agree.
     *
     * <p>The base is the <b>nearest</b> aligned multiple, so the local section is
     * always within +-2^28 (about 4.3 billion blocks) and may be negative:
     * negative x/z round-trip through the packed node because the decoders
     * sign-extend.</p>
     *
     * @return 0 = unchanged, 1 = established, 2 = re-based
     */
    public static int ensure(double blockX, double blockZ) {
        if (!isFar(blockX, blockZ)) {
            return 0;
        }
        long cx = section(blockX);
        long cz = section(blockZ);
        if (!established) {
            baseX = align(cx);
            baseZ = align(cz);
            established = true;
            return 1;
        }
        if (Math.abs(cx - baseX) <= HALF && Math.abs(cz - baseZ) <= HALF) {
            return 0;
        }
        baseX = align(cx);
        baseZ = align(cz);
        return 2;
    }

    /**
     * Local camera section for the rotating grid, or {@code null} when the
     * caller must use vanilla coordinates (normal world, no base yet, or the
     * camera left the aligned window).
     */
    public static SectionPos localSectionOf(double blockX, double blockY, double blockZ) {
        boolean log = Boolean.getBoolean("farlands.trace.lsec") && logs < 12;
        if (!established || !isFar(blockX, blockZ)) {
            if (log) {
                logs++;
                System.out.println("[LSEC] null established=" + established + " far="
                    + isFar(blockX, blockZ) + " cam=" + blockX + "," + blockZ
                    + " base=" + baseX + "," + baseZ);
                System.out.flush();
            }
            return null;
        }
        long lx = section(blockX) - baseX;
        long lz = section(blockZ) - baseZ;
        if (Math.abs(lx) > HALF || Math.abs(lz) > HALF) {
            if (log) {
                logs++;
                System.out.println("[LSEC] out-of-window lx=" + lx + " lz=" + lz
                    + " half=" + HALF + " sec=" + section(blockX) + ","
                    + section(blockZ) + " base=" + baseX + "," + baseZ);
                System.out.flush();
            }
            return null;
        }
        int ly = (int) Math.floorDiv((long) Math.floor(blockY), 16L);
        if (log) {
            logs++;
            System.out.println("[LSEC] ok local=" + lx + "," + ly + "," + lz
                + " sec=" + section(blockX) + "," + section(blockZ)
                + " base=" + baseX + "," + baseZ);
            System.out.flush();
        }
        return SectionPos.of((int) lx, ly, (int) lz);
    }

    /** One-line diagnostic for the tick log. */
    public static String describe(double blockX, double blockZ) {
        return "base=" + baseX + "," + baseZ
            + " local=" + (section(blockX) - baseX) + "," + (section(blockZ) - baseZ)
            + " established=" + established;
    }

    static int nextLogSlot() {
        return logs++;
    }
}
