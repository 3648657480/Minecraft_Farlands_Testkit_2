package com.farlands.g2.runtime;

import java.util.Set;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Full heightmap re-derivation for a chunk whose window moved.
 *
 * <p>Vanilla {@link Heightmap#primeHeightmaps} only writes a column when it finds
 * an opaque block; for an all-air column the previous value survives. After a
 * band move that leaves a stale band-relative value (observed: {@code topY} =
 * newMinY + 126 from the old band's surface), so height queries, spawn search
 * and mob logic see a surface that is not there. Reset the raw arrays first so
 * every column is recomputed, including empty ones.</p>
 */
public final class Heightmaps {

    private Heightmaps() {
    }

    public static void primeFully(ChunkAccess chunk, Set<Heightmap.Types> types) {
        for (Heightmap.Types type : types) {
            Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(type);
            // getRawData() is the packed LongArray; setRawData requires the same
            // length, so reset with an equally sized zero array.
            int length = heightmap.getRawData().length;
            heightmap.setRawData(chunk, type, new long[length]);
        }
        Heightmap.primeHeightmaps(chunk, types);
    }
}
