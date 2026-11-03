package com.farlands.g2.client;

import com.scalarwide.mc.WideChunkPos;
import com.scalarwide.mc.WideSectionKey2560;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** Decoded far chunks kept on the client, keyed by wide ChunkPos. */
public final class WideClientChunkStore {

    public record Data(WideChunkPos pos, int minSectionY, LevelChunkSection[] sections) {
    }

    private static final Map<WideChunkPos, Data> BY_CHUNK = new ConcurrentHashMap<>();
    private static volatile Data current;
    private static volatile long version;

    private WideClientChunkStore() {
    }

    public static void set(Data data) {
        Data old = BY_CHUNK.put(data.pos(), data);
        current = data;
        if (old == null) {
            version++;
        }
    }

    public static long version() {
        return version;
    }

    public static Data current() {
        return current;
    }

    public static Collection<Data> all() {
        return BY_CHUNK.values();
    }

    /** Keep only the sliding 9x9 window around {@code center}. */
    public static List<Data> retainAround(WideChunkPos center, int radius) {
        BigInteger cx = center.x().toBigInteger();
        BigInteger cz = center.z().toBigInteger();
        List<Data> removed = new ArrayList<>();
        BY_CHUNK.entrySet().removeIf(entry -> {
            WideChunkPos pos = entry.getKey();
            long dx = pos.x().toBigInteger().subtract(cx).abs().longValue();
            long dz = pos.z().toBigInteger().subtract(cz).abs().longValue();
            if (Math.max(dx, dz) <= radius) {
                return false;
            }
            removed.add(entry.getValue());
            version++;
            return true;
        });
        if (current != null && !BY_CHUNK.containsKey(current.pos())) {
            current = BY_CHUNK.isEmpty() ? null : BY_CHUNK.values().iterator().next();
        }
        return removed;
    }

    /** Section for an exact 2560 key, or null. */
    public static LevelChunkSection section(WideSectionKey2560 key) {
        if (key == null) {
            return null;
        }
        BigInteger chunkX = key.x().toBigInteger();
        BigInteger chunkZ = key.z().toBigInteger();
        Data data = BY_CHUNK.get(WideChunkPos.of(chunkX, chunkZ));
        if (data == null) {
            return null;
        }
        long sectionY;
        try {
            sectionY = key.y().toBigInteger().longValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
        int index = (int) (sectionY - data.minSectionY());
        if (index < 0 || index >= data.sections().length) {
            return null;
        }
        return data.sections()[index];
    }

    public static void clear() {
        BY_CHUNK.clear();
        current = null;
    }
}
