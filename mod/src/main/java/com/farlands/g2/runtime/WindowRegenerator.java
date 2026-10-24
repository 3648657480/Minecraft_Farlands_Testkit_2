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

    /** Only one Cwg fill at a time: each takes seconds of CPU. */
    private static final java.util.concurrent.atomic.AtomicBoolean FILL_GATE =
        new java.util.concurrent.atomic.AtomicBoolean();

    private WindowRegenerator() {
    }

    public static void regenerate(LevelChunk chunk, int centerSectionY, Consumer<Boolean> done) {
        if (!(chunk.getLevel() instanceof ServerLevel level)) {
            done.accept(false);
            return;
        }
        ChunkPos pos = chunk.getPos();
        YBandOverrides.set(pos.widePos(), centerSectionY);
        if (Boolean.getBoolean("farlands.y.diag")) {
            System.out.println("[REGEN] override pos=" + pos + " wide=" + pos.widePos()
                + " center=" + centerSectionY + " lookup=" + YBandOverrides.get(pos.widePos()));
            System.out.flush();
        }
        ProtoChunk proto = new ProtoChunk(pos, UpgradeData.EMPTY, level,
            level.palettedContainerFactory(), null);
        RandomState randomState = level.getChunkSource().randomState();
        StructureManager structures = level.structureManager();
        ChunkGenerator generator = level.getChunkSource().getGenerator();

        // Optional noise source: the infs Cwg chain reproduces the dome/vertex-region
        // phenomenon at high Y (EXPERIMENTS §77); vanilla's graph cannot (it clamps).
        // The fill is pure maths on a detached ProtoChunk, so it runs on a worker
        // thread: doing it on the server thread froze the server for seconds per
        // chunk (teleporting into a band looked like a one-minute hang).
        if (Boolean.getBoolean("farlands.gen.cwg")) {
            if (!FILL_GATE.compareAndSet(false, true)) {
                done.accept(false); // another fill is running; the cooldown retries
                return;
            }
            final long seed = level.getSeed();
            final var router = randomState.router();
            final ProtoChunk fillProto = proto;
            final int center = centerSectionY;
            final net.minecraft.server.MinecraftServer server = level.getServer();
            net.minecraft.util.Util.backgroundExecutor().execute(() -> {
                long filled = -1L;
                Throwable error = null;
                try {
                    com.farlands.g2.runtime.noise.CwgTerrain terrain =
                        com.farlands.g2.runtime.noise.CwgTerrain.create(seed, router, true);
                    int len = fillProto.getSections().length;
                    int minSy = center - (len - 1) / 2;
                    filled = terrain.fill(fillProto, minSy, minSy + len - 1);
                } catch (Throwable t) {
                    error = t;
                }
                final long filledFinal = filled;
                final Throwable failure = error;
                server.execute(() -> {
                    try {
                        if (failure != null) {
                            throw new RuntimeException(failure);
                        }
                        transplant(chunk, fillProto, center);
                        try {
                            com.farlands.g2.client.ClientBandBridge.push(chunk.getPos(), center,
                                ((WindowedChunk) chunk).farlands$windowMinSectionY(),
                                java.util.Arrays.asList(chunk.getSections()));
                        } catch (Throwable ignored) {
                            // dedicated server: client classes absent
                        }
                        System.out.println("[REGEN] cwg pos=" + pos + " band=" + center
                            + " filled=" + filledFinal);
                        System.out.flush();
                        done.accept(true);
                    } catch (Throwable t) {
                        System.out.println("[REGEN] cwg failed: " + t);
                        System.out.flush();
                        done.accept(false);
                    } finally {
                        FILL_GATE.set(false);
                    }
                });
            });
            return;
        }

        ServerChunkCache cacheSource = (ServerChunkCache) level.getChunkSource();
        final StaticCache2D<GenerationChunkHolder> cache = StaticCache2D.create(pos.x(), pos.z(), 8,
            (cx, cz) -> cacheSource.chunkMap.farlands$getUpdatingChunkW(
                new ChunkPos(cx, cz).widePos()));
        // Each stage needs a region built from ITS step: the step carries the write
        // radius / dependency set the safety checks compare against.
        java.util.function.Function<ChunkStatus, WorldGenRegion> regionFor = status -> {
            ChunkStep step = StageSteps.get(status);
            if (step == null) {
                step = StageSteps.get(ChunkStatus.SURFACE);
            }
            if (step == null) {
                return null;
            }
            if (step.blockStateWriteRadius() < 1) {
                // The captured step may be the load-time variant (write radius -1),
                // which makes the safety checks flag every neighbour read.
                step = new ChunkStep(step.targetStatus(), step.directDependencies(),
                    step.accumulatedDependencies(), 1, step.task());
            }
            return new WorldGenRegion(level, cache, step, proto);
        };

        // Structures first (vanilla order): starts -> references, then biomes/noise.
        try {
            if (level.getServer().getWorldGenSettings().options().generateStructures()) {
                generator.createStructures(level.registryAccess(),
                    level.getChunkSource().getGeneratorState(), structures, proto,
                    level.getStructureManager(), level.dimension());
            }
            level.onStructureStartsAvailable(proto);
            WorldGenRegion refRegion = regionFor.apply(ChunkStatus.STRUCTURE_REFERENCES);
            if (refRegion != null) {
                generator.createReferences(refRegion, structures.forWorldGenRegion(refRegion), proto);
            }
        } catch (Throwable t) {
            System.out.println("[REGEN] structure stage failed: " + t);
            System.out.flush();
        }

        WorldGenRegion biomesRegion = regionFor.apply(ChunkStatus.BIOMES);
        WorldGenRegion noiseRegion = regionFor.apply(ChunkStatus.NOISE);
        final Blender biomesBlender = biomesRegion == null ? Blender.empty() : Blender.of(biomesRegion);
        final Blender noiseBlender = noiseRegion == null ? Blender.empty() : Blender.of(noiseRegion);

        generator.createBiomes(randomState, biomesBlender, structures, proto)
            .thenCompose(biomes -> {
                // Bypassing ChunkStep.apply means the status must be advanced by hand:
                // surface rules / features refuse to run before BIOMES/NOISE.
                proto.setPersistedStatus(ChunkStatus.BIOMES);
                return generator.fillFromNoise(noiseBlender, randomState, structures, biomes);
            })
            .whenComplete((filled, error) -> level.getServer().execute(() -> {
                if (error != null) {
                    YBandOverrides.clear(pos.widePos());
                    System.out.println("[REGEN] pipeline failed: " + error);
                    System.out.flush();
                    done.accept(false);
                    return;
                }
                proto.setPersistedStatus(ChunkStatus.NOISE);
                try {
                    WorldGenRegion surfaceRegion = regionFor.apply(ChunkStatus.SURFACE);
                    if (surfaceRegion != null) {
                        generator.buildSurface(surfaceRegion, structures, randomState, proto);
                    }
                    proto.setPersistedStatus(ChunkStatus.SURFACE);
                    WorldGenRegion carversRegion = regionFor.apply(ChunkStatus.CARVERS);
                    if (carversRegion != null) {
                        generator.applyCarvers(carversRegion, level.getSeed(), randomState,
                            level.getBiomeManager(), structures, proto);
                    }
                    proto.setPersistedStatus(ChunkStatus.CARVERS);
                    WorldGenRegion featuresRegion = regionFor.apply(ChunkStatus.FEATURES);
                    if (featuresRegion != null) {
                        generator.applyBiomeDecoration(featuresRegion, proto, structures);
                    }
                    proto.setPersistedStatus(ChunkStatus.FEATURES);
                } catch (Throwable t) {
                    System.out.println("[REGEN] surface/feature stage failed: " + t);
                    System.out.flush();
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

    public static void transplant(LevelChunk chunk, ChunkAccess proto, int centerSectionY) {
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
        Heightmaps.primeFully(chunk, EnumSet.of(
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
