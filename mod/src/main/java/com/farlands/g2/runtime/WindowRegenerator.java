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

    /**
     * Cwg fills run off-thread; allow a few at once so the view can be banded in
     * reasonable time, but never so many that the machine saturates. Fair mode so
     * a stream of prefetches cannot starve page-crossing regenerations forever.
     */
    private static final int FILL_CONCURRENCY =
        Math.max(1, Integer.getInteger("farlands.gen.cwgConcurrency", 3));
    private static final java.util.concurrent.Semaphore FILL_SLOTS =
        new java.util.concurrent.Semaphore(FILL_CONCURRENCY, true);

    private WindowRegenerator() {
    }

    /** Restores the override that was present before a failed attempt. */
    private static void restoreOverride(ChunkPos pos, Integer previous) {
        if (previous == null) {
            YBandOverrides.clear(pos.widePos());
        } else {
            YBandOverrides.set(pos.widePos(), previous);
        }
    }

    public static void regenerate(LevelChunk chunk, int centerSectionY, Consumer<Boolean> done) {
        if (!(chunk.getLevel() instanceof ServerLevel level)) {
            done.accept(false);
            return;
        }
        ChunkPos pos = chunk.getPos();
        Integer previousOverride = YBandOverrides.get(pos.widePos());
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
            if (!FILL_SLOTS.tryAcquire()) {
                restoreOverride(pos, previousOverride);
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
                    // Page-aligned: minY = pageY * len (SEGMENT-PAGING §1).
                    int minSy = com.farlands.g2.runtime.VerticalBounds.pageStart(center, len);
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
                        int probeSy = ((WindowedChunk) chunk).farlands$windowMinSectionY()
                            + chunk.getSections().length / 2;
                        var probePos = new net.minecraft.core.BlockPos(pos.getMinBlockX() + 8,
                            probeSy * 16 + 8, pos.getMinBlockZ() + 8);
                        System.out.println("[REGEN] serverReadback pos=" + pos + " sy=" + probeSy
                            + " block=" + chunk.getBlockState(probePos));
                        System.out.flush();
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
                        restoreOverride(pos, previousOverride);
                        System.out.println("[REGEN] cwg failed: " + t);
                        System.out.flush();
                        done.accept(false);
                    } finally {
                        FILL_SLOTS.release();
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
                    restoreOverride(pos, previousOverride);
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
                // If the transplant throws, the callback MUST still run and the
                // override MUST be rolled back; otherwise IN_FLIGHT stays set and
                // the chunk's band claims a page it never received.
                try {
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
                } catch (Throwable t) {
                    restoreOverride(pos, previousOverride);
                    System.out.println("[REGEN] transplant failed: " + t);
                    System.out.flush();
                    done.accept(false);
                }
            }));
    }

    /**
     * P2: generate the page containing {@code targetSectionY} into {@link PageStore}
     * without touching the live chunk, so a page crossing becomes a transplant.
     */
    public static void prefetch(LevelChunk chunk, int targetSectionY) {
        if (!(chunk instanceof WindowedChunk)) {
            return;
        }
        ServerLevel level = (ServerLevel) chunk.getLevel();
        int len = chunk.getSections().length;
        int pageMin = com.farlands.g2.runtime.VerticalBounds.pageStart(targetSectionY, len);
        // The top page is clamped: every page index above it maps to the same
        // pageMin. Prefetching those fills the same page four times and (in the
        // last session) consumed all fill slots, starving page-crossing regens.
        if (chunk instanceof WindowedChunk wc
            && wc.farlands$windowMinSectionY() == pageMin) {
            return;
        }
        PageStore.Key key = new PageStore.Key(chunk.getPos().widePos(), pageMin);
        if (!PageStore.begin(key)) {
            return;
        }
        // Leave at least one fill slot for page-crossing regeneration; prefetch is
        // speculative and must never occupy the whole pool.
        if (FILL_SLOTS.availablePermits() <= 1 || !FILL_SLOTS.tryAcquire()) {
            PageStore.fail(key);
            return;
        }
        final long seed = level.getSeed();
        final var router = level.getChunkSource().randomState().router();
        // The detached ProtoChunk must cover the page, otherwise its section array
        // is the dimension band and the fill silently skips every block (filled=0).
        // The chunk's own YBandOverrides wins over SavedBandContext in the ctor, so
        // temporarily point the override at the target page; restore it right after
        // construction (the live chunk keeps its current band).
        Integer previousOverride = YBandOverrides.get(chunk.getPos().widePos());
        Integer previousBand = SavedBandContext.get();
        SavedBandContext.set(pageMin);
        YBandOverrides.set(chunk.getPos().widePos(), pageMin);
        ProtoChunk created = null;
        try {
            created = new ProtoChunk(chunk.getPos(), UpgradeData.EMPTY, level,
                level.palettedContainerFactory(), null);
        } catch (Throwable t) {
            PageStore.fail(key);
            FILL_SLOTS.release();
            System.out.println("[PREFETCH] proto create failed: " + t);
            System.out.flush();
            return;
        } finally {
            if (previousBand != null) {
                SavedBandContext.set(previousBand);
            } else {
                SavedBandContext.clear();
            }
            if (previousOverride != null) {
                YBandOverrides.set(chunk.getPos().widePos(), previousOverride);
            } else {
                YBandOverrides.clear(chunk.getPos().widePos());
            }
        }
        final ProtoChunk proto = created;
        if (proto == null || proto.getMinSectionY() != pageMin) {
            PageStore.fail(key);
            FILL_SLOTS.release();
            System.out.println("[PREFETCH] abort: protoMin="
                + (proto == null ? "null" : proto.getMinSectionY()) + " pageMin=" + pageMin);
            System.out.flush();
            return;
        }
        final net.minecraft.server.MinecraftServer server = level.getServer();
        if (Boolean.getBoolean("farlands.y.prefetchSync")) {
            // Test path: fill on the calling (server) thread, then complete.
            try {
                com.farlands.g2.runtime.noise.CwgTerrain terrain =
                    com.farlands.g2.runtime.noise.CwgTerrain.create(seed, router, true);
                long filled = terrain.fill(proto, pageMin, pageMin + len - 1);
                PageStore.complete(key, new PageStore.Prepared(pageMin, proto.getSections(), filled));
                System.out.println("[PREFETCH] sync ready pageMin=" + pageMin + " filled=" + filled);
                System.out.flush();
            } catch (Throwable t) {
                PageStore.fail(key);
            } finally {
                FILL_SLOTS.release();
            }
            return;
        }
        net.minecraft.util.Util.backgroundExecutor().execute(() -> {
            long filled = -1L;
            Throwable error = null;
            try {
                com.farlands.g2.runtime.noise.CwgTerrain terrain =
                    com.farlands.g2.runtime.noise.CwgTerrain.create(seed, router, true);
                filled = terrain.fill(proto, pageMin, pageMin + len - 1);
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
                    PageStore.complete(key, new PageStore.Prepared(pageMin, proto.getSections(), filledFinal));
                    System.out.println("[PREFETCH] ready pos=" + chunk.getPos() + " pageMin=" + pageMin
                        + " filled=" + filledFinal);
                    System.out.flush();
                } catch (Throwable t) {
                    PageStore.fail(key);
                } finally {
                    FILL_SLOTS.release();
                }
            });
        });
    }

    /** P2: transplant a prepared page into the live chunk; false when none is ready. */
    public static boolean applyPrepared(LevelChunk chunk, int targetSectionY) {
        if (!(chunk instanceof WindowedChunk windowed)) {
            return false;
        }
        int len = chunk.getSections().length;
        int pageMin = com.farlands.g2.runtime.VerticalBounds.pageStart(targetSectionY, len);
        PageStore.Prepared prepared = PageStore.take(
            new PageStore.Key(chunk.getPos().widePos(), pageMin));
        if (prepared == null) {
            return false;
        }
        int min = prepared.pageMin();
        Map<WideSectionKey2560, LevelChunkSection> all = windowed.farlands$allSections();
        LevelChunkSection[] sections = prepared.sections();
        for (int i = 0; i < len && i < sections.length; i++) {
            all.put(windowed.farlands$key(min + i), sections[i]);
        }
        int center = min + (len - 1) / 2;
        windowed.farlands$moveWindowToStart(min);
        Heightmaps.primeFully(chunk, EnumSet.of(
            Heightmap.Types.WORLD_SURFACE,
            Heightmap.Types.MOTION_BLOCKING,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Heightmap.Types.OCEAN_FLOOR));
        if (chunk.getLevel() instanceof ServerLevel level) {
            // The fast path bypasses regenerate(), so it must still record the new
            // band: otherwise the chunk loads at the old band after a restart and
            // its sections fall outside the window (empty chunk).
            YBandOverrides.set(chunk.getPos().widePos(), center);
            YBandOverrides.save(level.getServer().getWorldPath(
                net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("farlands_bands.txt"));
            chunk.postProcessGeneration(level);
        }
        try {
            com.farlands.g2.client.ClientBandBridge.push(chunk.getPos(), center,
                windowed.farlands$windowMinSectionY(),
                java.util.Arrays.asList(chunk.getSections()));
        } catch (Throwable ignored) {
            // dedicated server: client classes absent
        }
        System.out.println("[PREFETCH] applied pos=" + chunk.getPos() + " band=" + min
            + ".." + (min + len - 1) + " filled=" + prepared.filled());
        System.out.flush();
        return true;
    }

    public static void transplant(LevelChunk chunk, ChunkAccess proto, int centerSectionY) {
        WindowedChunk windowed = (WindowedChunk) chunk;
        int len = chunk.getSections().length;
        // Page-aligned: minY = pageY * len (SEGMENT-PAGING §1).
        int minSy = com.farlands.g2.runtime.VerticalBounds.pageStart(centerSectionY, len);
        Map<WideSectionKey2560, LevelChunkSection> all = windowed.farlands$allSections();
        LevelChunkSection[] protoSections = proto.getSections();
        for (int i = 0; i < len && i < protoSections.length; i++) {
            all.put(windowed.farlands$key(minSy + i), protoSections[i]);
        }
        windowed.farlands$moveWindowToStart(minSy);
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
