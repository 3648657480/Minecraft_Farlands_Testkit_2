package com.farlands.g2.client;

import com.scalarwide.mc.WideChunkPos;
import com.scalarwide.mc.WideSectionKey2560;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** Decoded far chunks kept on the client, keyed by wide ChunkPos. */
public final class WideClientChunkStore {

    public record Data(WideChunkPos pos, int minSectionY, LevelChunkSection[] sections) {
    }

    private static final Map<WideChunkPos, Data> BY_CHUNK = new ConcurrentHashMap<>();
    private static volatile Data current;

    private WideClientChunkStore() {
    }

    public static void set(Data data) {
        BY_CHUNK.put(data.pos(), data);
        current = data;
    }

    public static Data current() {
        return current;
    }

    public static Collection<Data> all() {
        return BY_CHUNK.values();
    }

    /** Section for an exact 2560 key, or null. */
    public static LevelChunkSection section(WideSectionKey2560 key) {
        if (key == null) {
            return null;
        }
        BigInteger chunkX = floorDiv16(key.x().toBigInteger());
        BigInteger chunkZ = floorDiv16(key.z().toBigInteger());
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

    private static BigInteger floorDiv16(BigInteger v) {
        return v.signum() < 0 ? v.subtract(BigInteger.valueOf(15)).divide(BigInteger.valueOf(16))
            : v.divide(BigInteger.valueOf(16));
    }

    public static void clear() {
        BY_CHUNK.clear();
        current = null;
    }
}
