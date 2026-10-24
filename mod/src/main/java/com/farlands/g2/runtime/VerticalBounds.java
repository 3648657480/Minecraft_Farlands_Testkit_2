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
     * Playable block range: the full int domain. Section 2^27-1 starts at
     * 2^31-16, and its last block is 2^31-1, so {@code section << 4} stays inside
     * int for every playable block (EXPERIMENTS §95).
     */
    public static final int MAX_PLAYABLE_BLOCK = Integer.MAX_VALUE;
    public static final int MIN_PLAYABLE_BLOCK = Integer.MIN_VALUE;

    /** Last representable section on the positive axis: 2^27-1. */
    public static final int MAX_SECTION = 134_217_727;
    public static final int MIN_SECTION = ~MAX_SECTION;

    /** Sections whose blocks all stay inside the playable block range. */
    public static final int MAX_PLAYABLE_SECTION = MAX_SECTION;
    public static final int MIN_PLAYABLE_SECTION = MIN_SECTION;

    private VerticalBounds() {
    }

    public static boolean inBuildHeight(int blockY) {
        return blockY >= MIN_PLAYABLE_BLOCK && blockY <= MAX_PLAYABLE_BLOCK;
    }

    public static boolean inSection(int sectionY) {
        return sectionY >= MIN_SECTION && sectionY <= MAX_SECTION;
    }
}
