package com.farlands.g2.runtime;

import com.scalarwide.mc.WideSectionKey2560;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Y-B/B-step: regenerate one chunk's vertical window <b>in place</b>.
 *
 * <p>Instead of unloading the chunk (ticket/tracker churn, EXPERIMENTS §60), a
 * detached {@link ProtoChunk} is created at the target band and the vanilla
 * generation stages are run for it: biomes → noise → surface → carvers →
 * features. Its sections are then transplanted into the live chunk's wide-keyed
 * section map, the window is rebuilt and the runtime heightmaps re-primed.</p>
 *
 * <p>The steps run off the main thread where vanilla does, so this is
 * asynchronous; the callback runs on the server thread after the transplant.
 * Surface/carvers/features need a {@link WorldGenRegion}, whose {@link ChunkStep}
 * is captured from a normal generation run ({@link StageSteps}).</p>
 */
public final class WindowRegenerator {

    private WindowRegenerator() {
    }

    public static void regenerate(LevelChunk chunk, int centerSectionY, Consumer<Boolean> done) {
        if (!(chunk.getLevel() instanceof ServerLevel level)) {
            done.accept(false);
            return;
        }
        ChunkPos pos = chunk.getPos();
        YBandOverrides.set(pos.widePos(), centerSectionY);
        ProtoChunk proto = new ProtoChunk(pos, UpgradeData.EMPTY, level,
            level.palettedContainerFactory(), null);
        RandomState randomState = level.getChunkSource().randomState();
        StructureManager structures = level.structureManager();
        ChunkGenerator generator = level.getChunkSource().getGenerator();

        WorldGenRegion region = null;
        StaticCache2D<GenerationChunkHolder> cache = null;
        ChunkStep surfaceStep = StageSteps.get(ChunkStatus.SURFACE);
        if (surfaceStep != null) {
            ServerChunkCache cacheSource = (ServerChunkCache) level.getChunkSource();
            cache = StaticCache2D.create(pos.x(), pos.z(), 8,
                (cx, cz) -> cacheSource.chunkMap.farlands$getUpdatingChunkW(
                    new ChunkPos(cx, cz).widePos()));
            region = new WorldGenRegion(level, cache, surfaceStep, proto);
        }
        final WorldGenRegion genRegion = region;
        Blender blender = genRegion == null ? Blender.empty() : Blender.of(genRegion);

        // Structures first (vanilla order): starts -> references, then biomes/noise.
        ChunkStep startsStep = StageSteps.get(ChunkStatus.STRUCTURE_STARTS);
        ChunkStep referencesStep = StageSteps.get(ChunkStatus.STRUCTURE_REFERENCES);
        if (startsStep != null && cache != null) {
            try {
                if (level.getServer().getWorldGenSettings().options().generateStructures()) {
                    generator.createStructures(level.registryAccess(),
                        level.getChunkSource().getGeneratorState(), structures, proto,
                        level.getStructureManager(), level.dimension());
                }
                level.onStructureStartsAvailable(proto);
                if (referencesStep != null) {
                    WorldGenRegion refRegion = new WorldGenRegion(level, cache, referencesStep, proto);
                    generator.createReferences(refRegion,
                        structures.forWorldGenRegion(refRegion), proto);
                }
            } catch (Throwable t) {
                System.out.println("[REGEN] structure stage failed: " + t);
                System.out.flush();
            }
        }

        generator.createBiomes(randomState, blender, structures, proto)
            .thenCompose(biomes -> generator.fillFromNoise(blender, randomState, structures, biomes))
            .whenComplete((filled, error) -> level.getServer().execute(() -> {
                if (error != null) {
                    YBandOverrides.clear(pos.widePos());
                    System.out.println("[REGEN] pipeline failed: " + error);
                    System.out.flush();
                    done.accept(false);
                    return;
                }
                if (genRegion != null) {
                    try {
                        generator.buildSurface(genRegion, structures, randomState, proto);
                        generator.applyCarvers(genRegion, level.getSeed(), randomState,
                            level.getBiomeManager(), structures, proto);
                        generator.applyBiomeDecoration(genRegion, proto, structures);
                    } catch (Throwable t) {
                        System.out.println("[REGEN] surface/feature stage failed: " + t);
                        System.out.flush();
                    }
                }
                transplant(chunk, proto, centerSectionY);
                // Single-player: push the regenerated window to the client chunk.
                try {
                    com.farlands.g2.client.ClientBandBridge.push(chunk.getPos(), centerSectionY,
                        ((WindowedChunk) chunk).farlands$windowMinSectionY(),
                        java.util.Arrays.asList(chunk.getSections()));
                } catch (Throwable ignored) {
                    // dedicated server: client classes absent
                }
                done.accept(true);
            }));
    }

    private static void transplant(LevelChunk chunk, ChunkAccess proto, int centerSectionY) {
        WindowedChunk windowed = (WindowedChunk) chunk;
        int len = chunk.getSections().length;
        int half = (len - 1) / 2;
        int minSy = centerSectionY - half;
        Map<WideSectionKey2560, LevelChunkSection> all = windowed.farlands$allSections();
        LevelChunkSection[] protoSections = proto.getSections();
        for (int i = 0; i < len && i < protoSections.length; i++) {
            all.put(windowed.farlands$key(minSy + i), protoSections[i]);
        }
        windowed.farlands$moveWindowTo(centerSectionY);
        Heightmap.primeHeightmaps(chunk, EnumSet.of(
            Heightmap.Types.WORLD_SURFACE,
            Heightmap.Types.MOTION_BLOCKING,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Heightmap.Types.OCEAN_FLOOR));
        // Vanilla runs this when the chunk becomes ticking (fluid ticks, neighbour
        // shape updates); without it the regenerated content differs from vanilla.
        if (chunk.getLevel() instanceof ServerLevel level) {
            chunk.postProcessGeneration(level);
        }
    }
}
