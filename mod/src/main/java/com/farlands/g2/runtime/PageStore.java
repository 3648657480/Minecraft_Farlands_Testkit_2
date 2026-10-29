package com.farlands.g2.runtime;

import com.scalarwide.mc.WideChunkPos;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * P2 of {@code docs/SEGMENT-PAGING.md}: prepared secondary pages.
 *
 * <p>A page is one vanilla column height (24 sections) aligned to
 * {@code minY = pageY * 24}. Secondary pages are generated ahead of the player
 * near a page boundary and kept here (detached, not part of the live chunk)
 * until the crossing happens; the crossing then only transplants the prepared
 * sections instead of generating them.</p>
 */
public final class PageStore {

    /** A prepared page: its aligned minimum section Y and its 24 sections. */
    public record Prepared(int pageMin, LevelChunkSection[] sections, long filled) {
    }

    /** Wide chunk identity + the page's actual (clamped) start section. */
    public record Key(WideChunkPos pos, int pageMin) {
    }

    private static final Map<Key, Prepared> PAGES = new ConcurrentHashMap<>();
    private static final Set<Key> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final java.util.concurrent.ConcurrentLinkedQueue<Key> ORDER = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final int MAX_PAGES = Integer.getInteger("farlands.y.pageCache", 8);

    private PageStore() {
    }

    /** Reserves a prefetch slot; false when already prepared or in flight. */
    public static boolean begin(Key key) {
        if (PAGES.containsKey(key)) {
            return false;
        }
        return IN_FLIGHT.add(key);
    }

    public static void complete(Key key, Prepared prepared) {
        PAGES.put(key, prepared);
        ORDER.add(key);
        while (PAGES.size() > MAX_PAGES) {
            Key oldest = ORDER.poll();
            if (oldest == null) break;
            PAGES.remove(oldest);
        }
        IN_FLIGHT.remove(key);
    }

    public static void fail(Key key) {
        IN_FLIGHT.remove(key);
    }

    public static Prepared take(Key key) {
        return PAGES.remove(key);
    }

    public static boolean has(Key key) {
        return PAGES.containsKey(key);
    }

    public static int size() {
        return PAGES.size();
    }

    public static void clear() {
        PAGES.clear();
        IN_FLIGHT.clear();
    }
}
