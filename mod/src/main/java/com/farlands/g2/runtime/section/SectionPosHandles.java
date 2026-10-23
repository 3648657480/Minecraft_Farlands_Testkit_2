package com.farlands.g2.runtime.section;

import com.scalarwide.mc.WideSectionKey2560;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * P0 of {@code docs/RENDER-WIDE-KEYS.md}: the {@code long} view of a section is an
 * <b>interned handle</b>, not a packed coordinate.
 *
 * <p>The authoritative identity stays {@link WideSectionKey2560} (2560-bit); the
 * table maps it to a monotonically allocated handle. Unlike infs-farlands' 64-bit
 * hash + conflict check, handles are collision-free by construction and fully
 * invertible.</p>
 *
 * <p>Lookup is keyed by the exact {@code (x, y, z)} section coordinates (ints), so
 * the hot path does not allocate a wide key; the wide key is created once per
 * entry and kept for identity/debug. Handles are process-local: never persisted,
 * never sent over the network.</p>
 *
 * <p>Gated by {@code -Dfarlands.render.wideKeys=true}; when disabled every method
 * returns "no handle" and {@code SectionPos} keeps its packed 29/29/6 layout.</p>
 */
public final class SectionPosHandles {

    /** First handle. Far from the packed layouts' value range to reduce ambiguity. */
    private static final long BASE = 0x4000_0000_0000_0000L;

    private static final AtomicLong NEXT = new AtomicLong(BASE);
    private static final Map<Key, Entry> BY_KEY = new ConcurrentHashMap<>();
    private static final Map<Long, Entry> BY_HANDLE = new ConcurrentHashMap<>();

    private SectionPosHandles() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean("farlands.render.wideKeys");
    }

    /** Interns a section; returns 0 when the feature is off. */
    public static long handle(int x, int y, int z) {
        if (!enabled()) {
            return 0L;
        }
        Key key = new Key(x, y, z);
        Entry existing = BY_KEY.get(key);
        if (existing != null) {
            return existing.handle;
        }
        long h = NEXT.getAndIncrement();
        Entry created = new Entry(h, x, y, z, WideSectionKey2560.of(x, y, z));
        Entry prev = BY_KEY.putIfAbsent(key, created);
        if (prev != null) {
            return prev.handle;
        }
        BY_HANDLE.put(h, created);
        return h;
    }

    /** Resolves a handle; null for longs that were not issued by this process. */
    public static Entry entry(long handle) {
        return handle == 0L ? null : BY_HANDLE.get(handle);
    }

    /** Handle equivalent of a section; 0 when off. */
    public static long handleOf(int x, int y, int z) {
        return handle(x, y, z);
    }

    public static void clear() {
        BY_KEY.clear();
        BY_HANDLE.clear();
    }

    public static int size() {
        return BY_HANDLE.size();
    }

    /** One section's identity + int views. */
    public static final class Entry {
        public final long handle;
        public final int x;
        public final int y;
        public final int z;
        public final WideSectionKey2560 wide;

        Entry(long handle, int x, int y, int z, WideSectionKey2560 wide) {
            this.handle = handle;
            this.x = x;
            this.y = y;
            this.z = z;
            this.wide = wide;
        }
    }

    private record Key(int x, int y, int z) {
    }
}
