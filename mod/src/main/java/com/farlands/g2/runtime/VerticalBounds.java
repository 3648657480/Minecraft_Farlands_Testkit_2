package com.farlands.g2.runtime;

/**
 * Playable vertical bounds for the Y-window work.
 *
 * <p>Vanilla derives the build height from {@code DimensionType.minY/height}, whose
 * {@code height} is an {@code int} and therefore cannot span the full playable
 * {@code ±(2^31-17)} block range (a 2^32 span does not fit in an int). These bounds
 * are therefore expressed independently of the dimension type, and only the
 * windowed chunk accessors / section arrays stay small.</p>
 *
 * <p>Section numbers are {@code ±(2^27-2)}: {@code section << 4} stays inside int,
 * so the int view remains a lossless bridge for the ±2^31 block target. Wider Y
 * identities are carried by {@code WideSectionCoord512} (see SectionPos).</p>
 */
public final class VerticalBounds {

    /**
     * Playable block range for the global height predicate. Kept at 2^31-17:
     * widening it to the full int domain made {@code isOutsideBuildHeight} return
     * false for every Y, and generation/entity code then took blocking
     * {@code getChunk} paths it normally skips (spawn freeze, EXPERIMENTS §97).
     * The window clamp uses {@link #MAX_PLAYABLE_SECTION} separately.
     */
    public static final int MAX_PLAYABLE_BLOCK = Integer.MAX_VALUE - 16;
    public static final int MIN_PLAYABLE_BLOCK = ~MAX_PLAYABLE_BLOCK;

    /** Last representable section on the positive axis: 2^27-1. */
    public static final int MAX_SECTION = 134_217_727;
    public static final int MIN_SECTION = ~MAX_SECTION;

    /** Sections whose blocks all stay inside the playable block range. */
    public static final int MAX_PLAYABLE_SECTION = MAX_SECTION - 1;
    public static final int MIN_PLAYABLE_SECTION = ~MAX_PLAYABLE_SECTION;

    private VerticalBounds() {
    }

    /**
     * Page start for a 24-section page, clamped so no section leaves the
     * playable int domain (a page crossing 2^31 overflows QuartPos/BlockPos).
     */
    public static int pageMin(int pageY, int len) {
        int maxPageY = Math.floorDiv(MAX_PLAYABLE_SECTION - len + 1, len);
        int minPageY = -Math.floorDiv(-MIN_PLAYABLE_SECTION, len);
        int clamped = Math.max(minPageY, Math.min(pageY, maxPageY));
        return clamped * len;
    }

    public static boolean inBuildHeight(int blockY) {
        return blockY >= MIN_PLAYABLE_BLOCK && blockY <= MAX_PLAYABLE_BLOCK;
    }

    public static boolean inSection(int sectionY) {
        return sectionY >= MIN_SECTION && sectionY <= MAX_SECTION;
    }
}
