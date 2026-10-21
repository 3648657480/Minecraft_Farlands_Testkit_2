package com.farlands.g2.mixin;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/** Rebuilds the surface biome window from the wide centre plus local offsets. */
@Mixin(NoiseBasedChunkGenerator.class)
public class SurfaceBiomeWindowMixin {

    private static final boolean TRACE = Boolean.getBoolean("farlands.surface.trace");

    /**
     * @author G2
     * @reason The vanilla window uses center.x()/z() +- radius in int space; at
     *         the 2^31 chunk boundary the bounds and the region lookups disagree,
     *         producing a near-unbounded repeat. Local offsets around the wide
     *         centre are exact and reduce to the vanilla window in the int domain.
     */
    @Overwrite
    private static Set<Holder<Biome>> collectPossibleBiomes(WorldGenRegion region, int radius) {
        Set<Holder<Biome>> out = new ReferenceOpenHashSet<>();
        ChunkPos center = region.getCenter();
        long cx = center.xLong();
        long cz = center.zLong();
        int calls = 0;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                ChunkAccess chunk = region.getChunk((int) (cx + dx), (int) (cz + dz));
                chunk.collectBiomesInPalette(out);
                calls++;
            }
        }
        if (TRACE) {
            System.out.println("[SURF] window centerLong=" + cx + "," + cz + " radius=" + radius
                + " cells=" + calls + " biomes=" + out.size());
            System.out.flush();
        }
        return out;
    }
}
