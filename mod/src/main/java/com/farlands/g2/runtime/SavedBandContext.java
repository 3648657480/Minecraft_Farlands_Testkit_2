package com.farlands.g2.runtime;

/**
 * Thread-local "saved window band" for the chunk currently being read from disk.
 *
 * <p>Saved chunks carry {@code yPos} (their min section). If a chunk was saved
 * with a moved window, loading it at the property/dimension band would drop
 * every saved section (they fall outside the window) and the chunk would appear
 * empty. {@code SerializableChunkData} publishes its {@code minSectionY} here
 * before building the chunk, and the chunk constructor prefers it.</p>
 */
public final class SavedBandContext {

    private static final ThreadLocal<Integer> BAND = new ThreadLocal<>();

    private SavedBandContext() {
    }

    public static void set(int minSectionY) {
        BAND.set(minSectionY);
    }

    public static Integer get() {
        return BAND.get();
    }

    public static void clear() {
        BAND.remove();
    }
}
