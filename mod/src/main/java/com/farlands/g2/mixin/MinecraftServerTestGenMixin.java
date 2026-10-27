package com.farlands.g2.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.farlands.g2.util.FarConfig;

import java.util.function.BooleanSupplier;

/**
 * Headless test hook: when the {@code farlands.testgen} system property is
 * set (format {@code chunkX,chunkZ}), forces a FULL generation of that
 * chunk once the server ticks and prints the result. Lets the E line
 * iterate on the generation domain without a client or a player.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerTestGenMixin {

    @Unique
    private static boolean farlands$testgenDone = false;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void farlands$testgen(BooleanSupplier hasTime, CallbackInfo ci) {
        MinecraftServer self = (MinecraftServer) (Object) this;
        if (!farlands$testgenDone && self.getTickCount() >= Integer.getInteger("farlands.testgen.delay", 0)) {
            farlands$testgenDone = true;
            runTestGen(self);
        }
        if (!farlands$spawnDone) {
            farlands$spawnDone = true;
            runSpawnSet(self);
        }
        if (!farlands$spawnSearchDone) {
            farlands$spawnSearchDone = true;
            runSpawnSearch(self);
        }
        if (farlands$saveCountdown > 0) {
            farlands$saveCountdown--;
            if (farlands$saveCountdown == 0) {
                self.saveAllChunks(true, true, true);
                System.out.println("[FarLands-Test] DONE saved");
                System.out.flush();
                self.halt(false);
            }
        }
    }

    /**
     * Settle window before save: chunk status "full" does not mean the
     * post-processing / lighting pipeline has finished, so saving right after
     * generation makes byte comparisons nondeterministic. Wait this many ticks
     * first (default 200 = 10 s).
     */
    @Unique
    private static int farlands$saveCountdown = -1;

    @Unique
    private static boolean farlands$spawnSearchDone = false;

    /**
     * Headless replication of the client's prepare_spawn path: calls
     * PlayerSpawnFinder.findSpawn with the world respawn, which schedules
     * per-chunk SPAWN_SEARCH tickets and forces generation. Crashes here
     * reproduce the integrated-server world-entry failure without a client.
     */
    @Unique
    private static void runSpawnSearch(MinecraftServer self) {
        if (!FarConfig.testspawn()) return;
        try {
            net.minecraft.server.level.ServerLevel level = self.overworld();
            net.minecraft.core.BlockPos pos = self.getWorldData().overworldData().getRespawnData().pos();
            System.out.println("[FarLands-Test] spawn search start respawn=(" + pos.getX()
                + "," + pos.getY() + "," + pos.getZ() + ")");
            System.out.flush();
            java.util.concurrent.CompletableFuture<net.minecraft.world.phys.Vec3> f =
                net.minecraft.server.level.PlayerSpawnFinder.findSpawn(level, pos);
            f.whenComplete((v, t) -> {
                if (t != null) {
                    System.out.println("[FarLands-Test] spawn search FAILED: " + t);
                    t.printStackTrace(System.out);
                } else {
                    System.out.println("[FarLands-Test] spawn search OK -> " + v);
                    net.minecraft.world.level.ChunkPos spawnChunk =
                        net.minecraft.world.level.ChunkPos.containing(net.minecraft.core.BlockPos.containing(v));
                    System.out.println("[FarLands-Test] spawn ticket radius 3 (PrepareSpawnTask path) chunk="
                        + spawnChunk.x() + "," + spawnChunk.z());
                    level.getChunkSource().addTicketAndLoadWithRadius(
                        net.minecraft.server.level.TicketType.PLAYER_SPAWN, spawnChunk, 3)
                        .whenComplete((ignored2, t2) -> {
                            if (t2 != null) {
                                System.out.println("[FarLands-Test] spawn ticket FAILED: " + t2);
                                t2.printStackTrace(System.out);
                            } else {
                                System.out.println("[FarLands-Test] spawn ticket OK (radius 3 path)");
                            }
                            System.out.flush();
                        });
                }
                System.out.flush();
            });
        } catch (Throwable t) {
            System.out.println("[FarLands-Test] spawn search setup FAILED: " + t);
            t.printStackTrace(System.out);
            System.out.flush();
        }
    }

    @Unique
    private static boolean farlands$spawnDone = false;

    @Unique
    private static void runSpawnSet(MinecraftServer self) {
        String spec = FarConfig.spawnset();
        if (spec == null || spec.isEmpty()) return;
        ServerLevel level = self.overworld();
        try {
            String[] parts = spec.split(",");
            double px = Double.parseDouble(parts[0].trim());
            int py = Integer.parseInt(parts[1].trim());
            double pz = Double.parseDouble(parts[2].trim());
            long localX = (long) Math.floor(px);
            long localZ = (long) Math.floor(pz);
            level.setRespawnData(new net.minecraft.world.level.storage.LevelData.RespawnData(
                net.minecraft.core.GlobalPos.of(
                    net.minecraft.world.level.Level.OVERWORLD,
                    new net.minecraft.core.BlockPos(localX, py, localZ)), 0.0f, 0.0f));
            System.out.println("[FarLands-Test] spawn set: real=(" + px + "," + py + "," + pz
                + ") wide=(" + localX + "," + localZ + ")");
        } catch (Throwable t) {
            System.out.println("[FarLands-Test] spawn set FAILED: " + t);
        }
        System.out.flush();
    }

    @Unique
    private static ServerLevel farlands$testLevel(MinecraftServer self) {
        String dim = System.getProperty("farlands.testgen.dim", "overworld");
        return switch (dim) {
            case "the_end", "end", "ender" -> self.getLevel(net.minecraft.world.level.Level.END);
            case "the_nether", "nether" -> self.getLevel(net.minecraft.world.level.Level.NETHER);
            default -> self.overworld();
        };
    }

    @Unique
    private static void runTestGen(MinecraftServer self) {
        String spec = FarConfig.testgen();
        System.out.println("[FarLands-Test] spec=" + spec + " cwd=" + System.getProperty("user.dir"));
        System.out.flush();
        if (spec == null || spec.isEmpty()) return;
        ServerLevel level = farlands$testLevel(self);
        System.out.println("[FarLands-Test] dimension=" + level.dimension().identifier());
        System.out.flush();
        try {
            // Determinism: random ticks use the level RNG (run-order dependent)
            // and grow kelp/vines, making chunk content vary between runs.
            level.getGameRules().set(
                net.minecraft.world.level.gamerules.GameRules.RANDOM_TICK_SPEED, 0, self);
            System.out.println("[FarLands-Test] random_tick_speed=0 (determinism)");
            System.out.flush();
            for (String entry : spec.split(";")) {
                if (entry.trim().isEmpty()) continue;
                String[] parts = entry.trim().split(",");
                long cxL = Long.parseLong(parts[0].trim());
                long czL = Long.parseLong(parts[1].trim());
                int n = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 1;
                long t0 = System.currentTimeMillis();
                ChunkAccess chunk = null;
                boolean fitsInt = cxL >= Integer.MIN_VALUE && cxL <= Integer.MAX_VALUE
                    && czL >= Integer.MIN_VALUE && czL <= Integer.MAX_VALUE;
                boolean forceWide = Boolean.getBoolean("farlands.testgen.widepath");
                if (fitsInt && !forceWide) {
                    int cx = (int) cxL;
                    int cz = (int) czL;
                    for (int dx = 0; dx < n; dx++) {
                        for (int dz = 0; dz < n; dz++) {
                            chunk = level.getChunk(cx + dx, cz + dz);
                        }
                    }
                } else {
                    // Wide path (chunk 2^49 and beyond): wide ticket -> wide holder
                    // tracking (DistanceManager/ChunkMap wide chain) -> managedBlock
                    // wait for the full chunk.
                    net.minecraft.server.level.ServerChunkCache sc =
                        (net.minecraft.server.level.ServerChunkCache) level.getChunkSource();
                    net.minecraft.world.level.ChunkPos widePos =
                        new net.minecraft.world.level.ChunkPos(cxL, czL);
                    sc.farlands$addTicketWithRadiusW(
                        net.minecraft.server.level.TicketType.FORCED, widePos, 3 + Math.max(0, n - 1));
                    net.minecraft.server.level.ChunkMap cm = sc.chunkMap;
                    try {
                        cm.getDistanceManager().runAllUpdates(cm);
                    } catch (Throwable t) {
                        System.out.println("[FarLands-Test] first runAllUpdates threw: " + t);
                    }
                    StringBuilder missRings = new StringBuilder();
                    for (int r = 0; r <= 45; r++) {
                        boolean anyMissing = false;
                        boolean anyPresent = false;
                        for (int dx = -r; dx <= r; dx++) {
                            for (int dz = -r; dz <= r; dz++) {
                                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                                    continue;
                                }
                                if (cm.farlands$getUpdatingChunkW(
                                    new net.minecraft.world.level.ChunkPos(cxL + dx, czL + dz).widePos()) != null) {
                                    anyPresent = true;
                                } else {
                                    anyMissing = true;
                                }
                            }
                        }
                        if (anyMissing) {
                            missRings.append(r).append(anyPresent ? "*" : "").append(' ');
                        }
                    }
                    System.out.println("[FarLands-Test] wide miss rings(<=45): " + missRings);
                    StringBuilder ringScan = new StringBuilder();
                    for (int rs = 13; rs <= 17; rs++) {
                        int present = 0;
                        int total = 0;
                        for (int dxs = -rs; dxs <= rs; dxs++) {
                            for (int dzs = -rs; dzs <= rs; dzs++) {
                                if (Math.max(Math.abs(dxs), Math.abs(dzs)) != rs) {
                                    continue;
                                }
                                total++;
                                if (cm.farlands$getUpdatingChunkW(
                                    new net.minecraft.world.level.ChunkPos(cxL + dxs, czL + dzs).widePos()) != null) {
                                    present++;
                                }
                            }
                        }
                        ringScan.append("r").append(rs).append("=").append(present).append("/").append(total).append(' ');
                    }
                    net.minecraft.server.level.ChunkHolder scanCell = cm.farlands$getUpdatingChunkW(
                        new net.minecraft.world.level.ChunkPos(cxL + 16, czL - 13).widePos());
                    System.out.println("[FarLands-Test] scan " + ringScan
                        + "cell(16,-13)=" + (scanCell == null ? "none" : scanCell.getTicketLevel())
                        + " MAX_LEVEL=" + net.minecraft.server.level.ChunkLevel.MAX_LEVEL);
                    StringBuilder edgeMiss = new StringBuilder();
                    for (int dx3 = -44; dx3 <= 44; dx3++) {
                        for (int dz3 = -44; dz3 <= 44; dz3++) {
                            int rr = Math.max(Math.abs(dx3), Math.abs(dz3));
                            if (rr < 41 || rr > 44) {
                                continue;
                            }
                            if (cm.farlands$getUpdatingChunkW(
                                new net.minecraft.world.level.ChunkPos(cxL + dx3, czL + dz3).widePos()) == null) {
                                edgeMiss.append(dx3).append(',').append(dz3).append(' ');
                            }
                        }
                    }
                    System.out.println("[FarLands-Test] edge miss(41..44): " + edgeMiss);
                    System.out.flush();
                    net.minecraft.server.level.ChunkHolder wideHolder = null;
                    for (int spin = 0; spin < 200; spin++) {
                        cm.getDistanceManager().runAllUpdates(cm);
                        cm.runGenerationTasks();
                        wideHolder = cm.farlands$getUpdatingChunkW(widePos.widePos());
                        if (wideHolder != null) {
                            break;
                        }
                        Thread.onSpinWait();
                    }
                    System.out.println("[FarLands-Test] wide diag holder=" + (wideHolder != null)
                        + " futureDone=" + (wideHolder != null && wideHolder.getFullChunkFuture().isDone()));
                    StringBuilder nb = new StringBuilder();
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dz == 0) {
                                continue;
                            }
                            net.minecraft.server.level.ChunkHolder nh = cm.farlands$getUpdatingChunkW(
                                new net.minecraft.world.level.ChunkPos(cxL + dx, czL + dz).widePos());
                            nb.append(dx).append(',').append(dz).append('=')
                                .append(nh == null ? "null" : String.valueOf(nh.getTicketLevel())).append(' ');
                        }
                    }
                    System.out.println("[FarLands-Test] wide nb " + nb);
                    StringBuilder miss = new StringBuilder();
                    for (int dx = -3; dx <= 3; dx++) {
                        for (int dz = -3; dz <= 3; dz++) {
                            if (cm.farlands$getUpdatingChunkW(
                                new net.minecraft.world.level.ChunkPos(cxL + dx, czL + dz).widePos()) == null) {
                                miss.append(dx).append(',').append(dz).append(' ');
                            }
                        }
                    }
                    System.out.println("[FarLands-Test] wide miss(7x7): " + miss);
                    System.out.flush();
                    com.farlands.g2.runtime.WideGenerationProbe.schedule(cm, widePos);
                    chunk = sc.farlands$getChunkWide(widePos);
                    if (chunk == null) {
                        net.minecraft.server.level.ChunkResult<?> res = wideHolder == null ? null
                            : (net.minecraft.server.level.ChunkResult<?>) wideHolder.getFullChunkFuture().getNow(null);
                        System.out.println("[FarLands-Test] wide diag result=" + res
                            + (res == null ? "" : (" success=" + res.isSuccess() + " err=" + res.getError())));
                        System.out.println("[FarLands-Test] gen FAILED: wide chunk null (" + cxL + "," + czL + ")");
                        System.out.flush();
                        continue;
                    }
                }
                long genMs = System.currentTimeMillis() - t0;
                int top = chunk != null ? chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8) : -999;
                String wgHash = chunk != null ? wgHash(chunk) : "none";
                int nonEmpty = 0;
                if (chunk != null) {
                    for (int i = 0; i < chunk.getSections().length; i++) {
                        if (chunk.getSections()[i] != null && !chunk.getSections()[i].hasOnlyAir()) {
                            nonEmpty++;
                        }
                    }
                }
                StringBuilder sb = new StringBuilder();
                sb.append("[FarLands-Test] gen OK region (").append(cxL).append(",").append(czL)
                    .append(")+").append(n).append("x").append(n).append(" last=(").append(cxL + n - 1)
                    .append(",").append(czL + n - 1).append(") topY=").append(top).append(" timeMs=").append(genMs)
                    .append(" wgHash=").append(wgHash)
                    .append(" biome=").append(chunk.getNoiseBiome(8, 60, 8).unwrapKey().map(Object::toString).orElse("?"))
                    .append(" b(8,60,8)=").append(chunk.getBlockState(new net.minecraft.core.BlockPos(cxL * 16 + 8, 60, czL * 16 + 8)))
                    .append(" topX8=").append(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8))
                    .append(" topX9=").append(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 9, 8))
                    .append(" topX12=").append(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 12, 8))
                    .append(" topZ8=").append(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 9))
                    .append(" sections=").append(nonEmpty)
                    .append(" surfWG=").append(chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 8, 8))
                    .append(" floorWG=").append(chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, 8, 8))
                    .append(" b(8,64,8)=").append(chunk.getBlockState(
                        new net.minecraft.core.BlockPos(cxL * 16 + 8, 64, czL * 16 + 8)))
                    .append(" b(8,100,8)=").append(chunk.getBlockState(
                        new net.minecraft.core.BlockPos(cxL * 16 + 8, 100, czL * 16 + 8)));
                System.out.println(sb);
                net.minecraft.core.SectionPos sp = net.minecraft.core.SectionPos.of(chunk.getPos(), 0);
                long sx = sp.wideSection().x().limb(0);
                long sz = sp.wideSection().z().limb(0);
                System.out.println("[FarLands-Test] section2560 x=" + sx + " z=" + sz
                    + " expect=" + cxL + "," + czL + " ok=" + (sx == cxL && sz == czL));
                // Structure probe: scan the generated n x n area for non-empty starts.
                if (Boolean.getBoolean("farlands.testgen.structscan")) {
                    int hits = 0;
                    for (int dx = 0; dx < n; dx++) {
                        for (int dz = 0; dz < n; dz++) {
                            net.minecraft.world.level.chunk.ChunkAccess sc =
                                level.getChunk((int) (cxL + dx), (int) (czL + dz));
                            int count = sc.getAllStarts().size();
                            if (count > 0) {
                                hits++;
                                System.out.println("[STRUCT] chunk=" + (cxL + dx) + "," + (czL + dz)
                                    + " starts=" + count);
                            }
                        }
                    }
                    System.out.println("[STRUCT] hits=" + hits + " area=" + n + "x" + n);
                    System.out.flush();
                }
                // Y profile probe: regenerate the same chunk at several bands and
                // Simple in-game noise sampler: finalDensity at arbitrary block Y.
                String densitySpec = System.getProperty("farlands.testgen.density");
                if (densitySpec != null && !densitySpec.isEmpty()) {
                    farlands$densityProbe(level, (int) (cxL * 16 + 8), (int) (czL * 16 + 8), densitySpec);
                }
                // report terrain top / non-empty sections per band.
                String profile = System.getProperty("farlands.testgen.profile");
                if (profile != null && !profile.isEmpty()
                    && chunk instanceof net.minecraft.world.level.chunk.LevelChunk profileChunk) {
                    int[] bands = java.util.Arrays.stream(profile.split(","))
                        .mapToInt(s -> Integer.parseInt(s.trim())).toArray();
                    farlands$profile(profileChunk, bands, 0);
                }
                // Cwg (infs) density probe: fill a chunk window with the Cwg noise chain.
                String cwgSy = System.getProperty("farlands.testgen.cwg");
                if (cwgSy != null && !cwgSy.isEmpty()
                    && chunk instanceof net.minecraft.world.level.chunk.LevelChunk cwgChunk) {
                    farlands$cwgFill(level, cwgChunk, Integer.parseInt(cwgSy.trim()));
                }
                // Y-D/B probe: regenerate the loaded chunk's window in place.
                if (Boolean.getBoolean("farlands.testgen.regen")
                    && chunk instanceof net.minecraft.world.level.chunk.LevelChunk levelChunk) {
                    int regenSy = Integer.getInteger("farlands.testgen.regen.sy", 1000);
                    long beforeDigest = farlands$digest(levelChunk);
                    System.out.println("[REGEN] before minY=" + chunk.getMinY() + " target=" + regenSy
                        + " starts=" + levelChunk.getAllStarts().size() + " digest=" + beforeDigest);
                    com.farlands.g2.runtime.WindowRegenerator.regenerate(levelChunk, regenSy, ok -> {
                        net.minecraft.world.level.ChunkPos rp = levelChunk.getPos();
                        net.minecraft.core.BlockPos probe60 = new net.minecraft.core.BlockPos(
                            rp.getMinBlockX() + 8, 60, rp.getMinBlockZ() + 8);
                        net.minecraft.core.BlockPos probe49 = new net.minecraft.core.BlockPos(
                            rp.getMinBlockX() + 8, 49, rp.getMinBlockZ() + 8);
                        System.out.println("[REGEN] done ok=" + ok + " minY=" + levelChunk.getMinY()
                            + " topY=" + levelChunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8)
                            + " b49=" + levelChunk.getBlockState(probe49)
                            + " b60=" + levelChunk.getBlockState(probe60)
                            + " col=" + farlands$column(levelChunk)
                            + " raw0=" + farlands$rawHeight(levelChunk)
                            + " solidSec=" + farlands$solidSections(levelChunk)
                            + " starts=" + levelChunk.getAllStarts().size()
                            + " digest=" + farlands$digest(levelChunk)
                            + " equal=" + (farlands$digest(levelChunk) == beforeDigest)
                            + " sections=" + levelChunk.getSections().length);
                        System.out.flush();
                    });
                    System.out.flush();
                }
                // Cwg map dump: X-Z slices of the density at a band (PGM for inspection).
                String cwgMap = System.getProperty("farlands.testgen.cwgmap");
                if (cwgMap != null && !cwgMap.isEmpty()) {
                    farlands$cwgMap(level, Integer.parseInt(cwgMap.trim()));
                }
                // P2 probe: prefetch + apply a page in one go (sync test path).
                String pageTest = System.getProperty("farlands.testgen.pageprefetch");
                if (pageTest != null && !pageTest.isEmpty()
                    && chunk instanceof net.minecraft.world.level.chunk.LevelChunk pageChunk) {
                    int pageSy = Integer.parseInt(pageTest.trim());
                    long pt0 = System.nanoTime();
                    com.farlands.g2.runtime.WindowRegenerator.prefetch(pageChunk, pageSy);
                    boolean applied = com.farlands.g2.runtime.WindowRegenerator.applyPrepared(pageChunk, pageSy);
                    System.out.println("[PAGETEST] sy=" + pageSy + " applied=" + applied
                        + " band=" + pageChunk.getMinY() / 16 + " ms="
                        + (System.nanoTime() - pt0) / 1_000_000L);
                    System.out.flush();
                }
                // Columnar fast-path comparison (full fill vs per-column fill).
                String colcmp = System.getProperty("farlands.testgen.colcmp");
                if (colcmp != null && !colcmp.isEmpty()
                    && chunk instanceof net.minecraft.world.level.chunk.LevelChunk cmpChunk) {
                    int cmpSy = Integer.parseInt(colcmp.trim());
                    int cmpLen = cmpChunk.getSections().length;
                    int pageMin = com.farlands.g2.runtime.VerticalBounds.pageStart(cmpSy, cmpLen);
                    var cmpRouter = level.getChunkSource().randomState().router();
                    var cmpTerrain = com.farlands.g2.runtime.noise.CwgTerrain.create(
                        level.getSeed(), cmpRouter, true);
                    Integer prevBand = com.farlands.g2.runtime.SavedBandContext.get();
                    com.farlands.g2.runtime.SavedBandContext.set(pageMin);
                    var protoA = new net.minecraft.world.level.chunk.ProtoChunk(cmpChunk.getPos(),
                        net.minecraft.world.level.chunk.UpgradeData.EMPTY, level,
                        level.palettedContainerFactory(), null);
                    var protoB = new net.minecraft.world.level.chunk.ProtoChunk(cmpChunk.getPos(),
                        net.minecraft.world.level.chunk.UpgradeData.EMPTY, level,
                        level.palettedContainerFactory(), null);
                    if (prevBand != null) {
                        com.farlands.g2.runtime.SavedBandContext.set(prevBand);
                    } else {
                        com.farlands.g2.runtime.SavedBandContext.clear();
                    }
                    long filledA = cmpTerrain.fill(protoA, pageMin, pageMin + cmpLen - 1);
                    long filledB = cmpTerrain.fillColumnar(protoB, pageMin, pageMin + cmpLen - 1);
                    com.farlands.g2.runtime.SavedBandContext.set(pageMin);
                    var protoC = new net.minecraft.world.level.chunk.ProtoChunk(cmpChunk.getPos(),
                        net.minecraft.world.level.chunk.UpgradeData.EMPTY, level,
                        level.palettedContainerFactory(), null);
                    long filledC = cmpTerrain.fillColumnarSection(protoC, pageMin, pageMin + cmpLen - 1);
                    if (prevBand != null) {
                        com.farlands.g2.runtime.SavedBandContext.set(prevBand);
                    } else {
                        com.farlands.g2.runtime.SavedBandContext.clear();
                    }
                    long hashC = farlands$digestProto(protoC);
                    long hashA = farlands$digestProto(protoA);
                    long hashB = farlands$digestProto(protoB);
                    System.out.println("[COLCMP] sy=" + cmpSy + " pageMin=" + pageMin
                        + " filledA=" + filledA + " filledB=" + filledB
                        + " digestA=" + Long.toHexString(hashA) + " digestB=" + Long.toHexString(hashB)
                        + " match=" + (hashA == hashB)
                        + " sectionMatch=" + (hashA == hashC) + " digestC=" + Long.toHexString(hashC));
                    System.out.flush();
                }
                // Route D0 probe: absolute-Y block write/read through the data layer.
                String d0y = System.getProperty("farlands.testgen.absy");
                if (d0y != null && !d0y.isEmpty()) {
                    long y = Long.parseLong(d0y.trim());
                    var p0 = new net.minecraft.core.BlockPos(8L, y, 8L);
                    var stone0 = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
                    boolean set0 = level.setBlock(p0, stone0, 3);
                    var read0 = level.getBlockState(p0);
                    var chunk0 = level.getChunk(0, 0);
                    int size0 = chunk0 instanceof com.farlands.g2.runtime.WindowedChunk w0
                        ? w0.farlands$allSections().size() : -1;
                    System.out.println("[D0] y=" + y + " getLongY=" + p0.getLongY()
                        + " set=" + set0 + " read=" + read0 + " ok=" + read0.equals(stone0)
                        + " absoluteSections=" + size0);
                    System.out.flush();
                }
                // X/Z seam continuity probe: adjacent columns across block 2^31.
                String seam = System.getProperty("farlands.testgen.seam");
                if (seam != null && !seam.isEmpty()) {
                    int zc = 1;
                    try {
                        zc = Math.max(1, Integer.parseInt(seam.trim()));
                    } catch (NumberFormatException ignored) {
                        // default 1 chunk
                    }
                    int maxDelta = 0;
                    int maxFloor = 0;
                    int maxDz = -1;
                    int biomeMismatch = 0;
                    int samples = 0;
                    int maxLeftIntra = 0;
                    int maxRightIntra = 0;
                    int crossOver2 = 0;
                    StringBuilder pairs = new StringBuilder();
                    for (int cz = 0; cz < zc; cz++) {
                        var left = level.getChunk(134217727, cz);
                        var right = level.getChunk(134217728, cz);
                        for (int zz = 0; zz < 16; zz++) {
                            int h0 = left.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 15, zz);
                            int h1 = right.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 0, zz);
                            int delta = Math.abs(h1 - h0);
                            int f0 = left.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, 15, zz);
                            int f1 = right.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, 0, zz);
                            maxFloor = Math.max(maxFloor, Math.abs(f1 - f0));
                            if (Math.abs(f1 - f0) > 2) {
                                crossOver2++;
                            }
                            for (int lx = 1; lx < 16; lx++) {
                                maxLeftIntra = Math.max(maxLeftIntra, Math.abs(
                                    left.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, lx, zz)
                                        - left.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, lx - 1, zz)));
                                maxRightIntra = Math.max(maxRightIntra, Math.abs(
                                    right.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, lx, zz)
                                        - right.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, lx - 1, zz)));
                            }
                            if (delta > maxDelta) {
                                maxDelta = delta;
                                maxDz = cz * 16 + zz;
                            }
                            var b0 = left.getNoiseBiome(3, 16, zz >> 2);
                            var b1 = right.getNoiseBiome(0, 16, zz >> 2);
                            if (!b0.equals(b1)) {
                                biomeMismatch++;
                            }
                            samples++;
                            if (cz == 0) {
                                pairs.append(h0).append("/").append(h1).append(" ");
                            }
                        }
                    }
                    System.out.println("[SEAM] zc=" + zc + " maxDelta=" + maxDelta + " atZ=" + maxDz
                        + " biomeMismatch=" + biomeMismatch + "/" + samples
                        + " maxFloorDelta=" + maxFloor + " firstChunkPairs=" + pairs);
                    System.out.println("[SEAMINTRA] zc=" + zc + " maxLeftIntra=" + maxLeftIntra
                        + " maxRightIntra=" + maxRightIntra + " crossOver2=" + crossOver2 + "/" + samples);
                    System.out.flush();
                    if (Boolean.getBoolean("farlands.diag.seamsurface")) {
                        var router = level.getChunkSource().randomState().router();
                        var seLeft = level.getChunk(134217727, 0);
                        var seRight = level.getChunk(134217728, 0);
                        long[] xs = {2147483632L, 2147483640L, 2147483644L, 2147483646L,
                            2147483647L, 2147483648L, 2147483652L, 2147483664L};
                        int[] zs = {0, 1, 2, 4};
                        for (long sfsX : xs) {
                            StringBuilder sfsRow = new StringBuilder();
                            for (int sfsZ : zs) {
                                int sfsTop = -999;
                                for (int sfsY = 70; sfsY <= 160; sfsY++) {
                                    if (router.finalDensity().compute(
                                        new com.farlands.g2.runtime.RealContext(sfsX, sfsY, sfsZ)) > 0.0) {
                                        sfsTop = sfsY;
                                    }
                                }
                                int sfsGen = -1;
                                if (sfsX < 2147483648L) {
                                    sfsGen = seLeft.getHeight(
                                        net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG,
                                        (int) (sfsX - 2147483632L), sfsZ);
                                } else {
                                    sfsGen = seRight.getHeight(
                                        net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG,
                                        (int) (sfsX - 2147483648L), sfsZ);
                                }
                                sfsRow.append("z").append(sfsZ).append(":field=").append(sfsTop)
                                    .append("/gen=").append(sfsGen).append(' ');
                            }
                            System.out.println("[SEAMSURF] x=" + sfsX + " " + sfsRow);
                        }
                        System.out.flush();
                    }
                    int ctrlMismatch = 0;
                    for (int cz = 0; cz < zc; cz++) {
                        var ctlLeft = level.getChunk(1000, cz);
                        var ctlRight = level.getChunk(1001, cz);
                        for (int zz = 0; zz < 16; zz++) {
                            if (!ctlLeft.getNoiseBiome(3, 16, zz >> 2)
                                .equals(ctlRight.getNoiseBiome(0, 16, zz >> 2))) {
                                ctrlMismatch++;
                            }
                        }
                    }
                    System.out.println("[SEAM-CTRL] biomeMismatch=" + ctrlMismatch + "/" + samples);
                    System.out.flush();
                }
                String bb = System.getProperty("farlands.testgen.biomebounds");
                if (bb != null && !bb.isEmpty()) {
                    int bn = Integer.parseInt(bb.trim());
                    StringBuilder counts = new StringBuilder();
                    for (int i = 0; i < bn; i++) {
                        var l = level.getChunk(1000 + i, 0);
                        var r = level.getChunk(1001 + i, 0);
                        int mm = 0;
                        for (int zz = 0; zz < 16; zz++) {
                            if (!l.getNoiseBiome(3, 16, zz >> 2).equals(r.getNoiseBiome(0, 16, zz >> 2))) {
                                mm++;
                            }
                        }
                        counts.append(mm).append(" ");
                    }
                    System.out.println("[BIOMEBOUNDS] n=" + bn + " perBoundary=" + counts);
                    System.out.flush();
                }
                if (Boolean.getBoolean("farlands.diag.narrow.selftest")) {
                    net.minecraft.core.SectionPos.asLong(600000000, 0, 0);
                    System.out.println("[NARROW] selftest done");
                }
                String pair = System.getProperty("farlands.testgen.seampair");
                if (pair != null && !pair.isEmpty() && chunk != null) {
                    int base = Integer.parseInt(pair.trim());
                    var west = level.getChunk(base - 1, 0);
                    var mid = level.getChunk(base, 0);
                    var east = level.getChunk(base + 1, 0);
                    int da = 0;
                    int db = 0;
                    for (int zz = 0; zz < 16; zz++) {
                        da = Math.max(da, Math.abs(mid.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 0, zz) - west.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 15, zz)));
                        db = Math.max(db, Math.abs(east.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 0, zz) - mid.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 15, zz)));
                    }
                    var cW = level.getChunk(base - 1, 0);
                    var cM = level.getChunk(base, 0);
                    var cNeg = level.getChunk(-base, 0);
                    String bW = cW.getNoiseBiome(3, 25, 0).unwrapKey().map(Object::toString).orElse("?");
                    String bM = cM.getNoiseBiome(0, 25, 0).unwrapKey().map(Object::toString).orElse("?");
                    String bNeg = cNeg.getNoiseBiome(0, 25, 0).unwrapKey().map(Object::toString).orElse("?");
                    System.out.println("[SEAMBIOME] west=" + bW + " mid=" + bM + " negMirror=" + bNeg);
                    System.out.println("[SEAMPAIR] base=" + base + " westMid=" + da + " midEast=" + db
                        + " surfaces=" + west.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 8, 8)
                        + "/" + mid.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 8, 8)
                        + "/" + east.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 8, 8));
                    System.out.flush();
                }
                // Y-D follow trigger: exercise the same code path players use.
                String followSy = System.getProperty("farlands.testgen.followsy");
                if (followSy != null && !followSy.isEmpty()
                    && chunk instanceof net.minecraft.world.level.chunk.LevelChunk followChunk) {
                    int targetSy = Integer.parseInt(followSy);
                    boolean triggered = com.farlands.g2.runtime.WindowFollower.follow(
                        followChunk, targetSy, 100);
                    System.out.println("[FOLLOW] target=" + targetSy + " triggered=" + triggered
                        + " minY=" + followChunk.getMinY());
                    System.out.flush();
                }
                // P3 tick probe: schedule a block tick at the centre of the far
                // chunk. If LevelTicks resolves the position through the int
                // view, it logs "Trying to schedule tick in not loaded position".
                if (Boolean.getBoolean("farlands.testgen.scheduletick") && chunk != null) {
                    net.minecraft.core.BlockPos tickPos =
                        new net.minecraft.core.BlockPos(cxL * 16 + 8, 60, czL * 16 + 8);
                    var tickState = level.getBlockState(tickPos);
                    level.scheduleTick(tickPos, tickState.getBlock(), 1);
                    System.out.println("[FarLands-Test] scheduleTick wide="
                        + tickPos.getLongX() + "," + tickPos.getLongY() + "," + tickPos.getLongZ()
                        + " intView=" + tickPos.getX() + " block=" + tickState.getBlock());
                }
                // Y-C test: move the chunk's vertical window at runtime and prove
                // (a) reads outside the window are air, (b) moving back re-attaches
                // the original sections from the wide-keyed map.
                if (Boolean.getBoolean("farlands.testgen.windowmove")
                    && chunk instanceof com.farlands.g2.runtime.WindowedChunk wc) {
                    int moveSy = Integer.getInteger("farlands.testgen.windowmove.sy", 1000);
                    net.minecraft.core.BlockPos probe =
                        new net.minecraft.core.BlockPos(cxL * 16 + 8, 60, czL * 16 + 8);
                    net.minecraft.core.BlockPos probeNew =
                        new net.minecraft.core.BlockPos(cxL * 16 + 8, moveSy * 16 + 8, czL * 16 + 8);
                    String before = chunk.getBlockState(probe).toString();
                    int oldCenter = wc.farlands$windowMinSectionY() + (chunk.getSections().length - 1) / 2;
                    wc.farlands$moveWindowTo(moveSy);
                    String atOld = chunk.getBlockState(probe).toString();
                    String atNew = chunk.getBlockState(probeNew).toString();
                    wc.farlands$moveWindowTo(oldCenter);
                    String back = chunk.getBlockState(probe).toString();
                    System.out.println("[WMOVE] oldCenter=" + oldCenter + " moveTo=" + moveSy
                        + " before=" + before + " atOld=" + atOld + " atNew=" + atNew
                        + " back=" + back + " minY=" + chunk.getMinY());
                }
                System.out.flush();
            }
            if (FarConfig.testgenStop()) {
                farlands$saveCountdown = FarConfig.testgenSettle();
                System.out.println("[FarLands-Test] settling " + farlands$saveCountdown + " ticks before save");
                System.out.flush();
            }
        } catch (Throwable t) {
            System.out.println("[FarLands-Test] gen FAILED: " + t);
            t.printStackTrace(System.out);
            System.out.flush();
        }
    }

    /**
     * Deterministic generation-time fingerprint: WORLD_SURFACE_WG and
     * OCEAN_FLOOR_WG heightmaps (computed before features/post-processing)
     * plus the quart-resolution biome grid. Unlike saved chunk NBT this does
     * not depend on ticking, lighting or save timing, so it is a valid
     * cross-run comparison instrument.
     */
    @Unique
    private static String wgHash(ChunkAccess chunk) {
        try {
            java.security.MessageDigest surfMd = java.security.MessageDigest.getInstance("SHA-256");
            java.security.MessageDigest floorMd = java.security.MessageDigest.getInstance("SHA-256");
            java.io.ByteArrayOutputStream surfBos = new java.io.ByteArrayOutputStream();
            java.io.ByteArrayOutputStream floorBos = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream surfDos = new java.io.DataOutputStream(surfBos);
            java.io.DataOutputStream floorDos = new java.io.DataOutputStream(floorBos);
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    surfDos.writeInt(chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lx, lz));
                    floorDos.writeInt(chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, lx, lz));
                }
            }
            surfDos.flush();
            floorDos.flush();

            java.security.MessageDigest biomeMd = java.security.MessageDigest.getInstance("SHA-256");
            java.io.ByteArrayOutputStream biomeBos = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream biomeDos = new java.io.DataOutputStream(biomeBos);
            for (int qx = 0; qx < 4; qx++) {
                for (int qz = 0; qz < 4; qz++) {
                    biomeDos.writeUTF(chunk.getNoiseBiome(qx, 16, qz).unwrapKey().map(Object::toString).orElse("?"));
                }
            }
            biomeDos.flush();

            return "surf=" + hex(surfMd.digest(surfBos.toByteArray())).substring(0, 12)
                + " floor=" + hex(floorMd.digest(floorBos.toByteArray())).substring(0, 12)
                + " biome=" + hex(biomeMd.digest(biomeBos.toByteArray())).substring(0, 12);
        } catch (Exception e) {
            return "err:" + e.getClass().getSimpleName();
        }
    }

    @Unique
    private static String hex(byte[] hash) {
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    /** Simple in-game density sampler (see farlands.testgen.density). */
    @Unique
    private static void farlands$densityProbe(net.minecraft.server.level.ServerLevel level,
        int x, int z, String spec) {
        var router = level.getChunkSource().randomState().router();
        int[] ys;
        if (spec.contains(":")) {
            String[] parts = spec.split(":");
            int y0 = Integer.parseInt(parts[0].trim());
            int y1 = Integer.parseInt(parts[1].trim());
            int step = parts.length > 2 ? Math.max(1, Integer.parseInt(parts[2].trim())) : 1;
            int count = Math.max(0, (y1 - y0) / step + 1);
            ys = new int[Math.min(count, 4096)];
            for (int i = 0; i < ys.length; i++) {
                ys[i] = y0 + i * step;
            }
        } else {
            ys = java.util.Arrays.stream(spec.split(","))
                .mapToInt(s -> Integer.parseInt(s.trim())).toArray();
        }
        for (int y : ys) {
            final int by = y;
            net.minecraft.world.level.levelgen.DensityFunction.FunctionContext ctx =
                new net.minecraft.world.level.levelgen.DensityFunction.FunctionContext() {
                    @Override public int blockX() { return x; }
                    @Override public int blockY() { return by; }
                    @Override public int blockZ() { return z; }
                };
            double finalDensity = router.finalDensity().compute(ctx);
            double prelim = router.preliminarySurfaceLevel().compute(ctx);
            System.out.println("[DENSITY] x=" + x + " y=" + y + " z=" + z
                + " finalDensity=" + finalDensity + " prelimSurface=" + prelim);
        }
        System.out.flush();
    }

    /** Fill the chunk window with the infs Cwg noise chain at the given section band. */
    @Unique
    private static void farlands$cwgFill(net.minecraft.server.level.ServerLevel level,
        net.minecraft.world.level.chunk.LevelChunk chunk, int centerSy) {
        long t0 = System.nanoTime();
        var router = level.getChunkSource().randomState().router();
        com.farlands.g2.runtime.noise.CwgTerrain terrain =
            com.farlands.g2.runtime.noise.CwgTerrain.create(level.getSeed(), router, true);
        com.farlands.g2.runtime.YBandOverrides.set(chunk.getPos().widePos(), centerSy);
        net.minecraft.world.level.chunk.ProtoChunk proto = new net.minecraft.world.level.chunk.ProtoChunk(
            chunk.getPos(), net.minecraft.world.level.chunk.UpgradeData.EMPTY, level,
            level.palettedContainerFactory(), null);
        int len = proto.getSections().length;
        int minSy = com.farlands.g2.runtime.VerticalBounds.pageStart(centerSy, len);
        long filled = terrain.fill(proto, minSy, minSy + len - 1);
        com.farlands.g2.runtime.WindowRegenerator.transplant(chunk, proto, centerSy);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        int solid = farlands$solidSections(chunk);
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
        System.out.println("[CWG] sy=" + centerSy + " minY=" + chunk.getMinY()
            + " filled=" + filled + " solidSec=" + solid + " topY=" + top
            + " col=" + farlands$column(chunk) + " ms=" + ms);
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int dy : new int[] {32, 160, 288, 352}) {
            StringBuilder rows = new StringBuilder();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    var state = chunk.getBlockState(new net.minecraft.core.BlockPos(
                        baseX + x, chunk.getMinY() + dy, baseZ + z));
                    rows.append(state.isAir() ? '.' : '#');
                }
                rows.append('/');
            }
            System.out.println("[CWG-SLICE] dy=" + dy + " " + rows);
        }
        System.out.flush();
    }

    /** Vertical profile: regenerate the chunk at each section band in turn. */
    @Unique
    private static void farlands$profile(net.minecraft.world.level.chunk.LevelChunk chunk,
        int[] bands, int index) {
        if (index >= bands.length) {
            return;
        }
        int sectionY = bands[index];
        com.farlands.g2.runtime.WindowRegenerator.regenerate(chunk, sectionY, ok -> {
            int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
            int solid = 0;
            for (net.minecraft.world.level.chunk.LevelChunkSection section : chunk.getSections()) {
                if (section != null && !section.hasOnlyAir()) {
                    solid++;
                }
            }
            long minBlockY = (long) chunk.getMinY();
            System.out.println("[PROFILE] sy=" + sectionY + " ok=" + ok
                + " minBlockY=" + minBlockY + " topY=" + top
                + " solidSections=" + solid);
            System.out.flush();
            farlands$profile(chunk, bands, index + 1);
        });
    }

    /** First available WORLD_SURFACE height at local (8,8) (absolute Y). */
    @Unique
    private static int farlands$rawHeight(net.minecraft.world.level.chunk.LevelChunk chunk) {
        for (var entry : chunk.getHeightmaps()) {
            if (entry.getKey() == Heightmap.Types.WORLD_SURFACE) {
                return entry.getValue().getFirstAvailable(8, 8);
            }
        }
        return -1;
    }

    /** Writes X-Z density slices of the Cwg chain at a band as binary PGM. */
    @Unique
    private static void farlands$cwgMap(net.minecraft.server.level.ServerLevel level, int sectionY) {
        var terrain = com.farlands.g2.runtime.noise.CwgTerrain.create(level.getSeed(),
            level.getChunkSource().randomState().router(), true);
        int baseY = (sectionY - 11) * 16;
        int size = 256;
        for (int dy : new int[] {125, 60, 10}) {
            String name = "cwg-map-" + sectionY + "-dy" + dy + ".pgm";
            try (var out = new java.io.BufferedOutputStream(
                new java.io.FileOutputStream(name))) {
                out.write(("P5\n" + size + " " + size + "\n255\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                for (int z = 0; z < size; z++) {
                    for (int x = 0; x < size; x++) {
                        double d = terrain.density(x, baseY + dy, z);
                        out.write(d > 0.0 ? 0 : 255);
                    }
                }
            } catch (Exception e) {
                System.out.println("[CWGMAP] failed " + name + ": " + e);
            }
            System.out.println("[CWGMAP] wrote " + name + " baseY=" + baseY + " dy=" + dy);
        }
        System.out.flush();
    }

    /** Number of non-empty sections in the chunk window. */
    @Unique
    private static int farlands$solidSections(net.minecraft.world.level.chunk.LevelChunk chunk) {
        int solid = 0;
        for (net.minecraft.world.level.chunk.LevelChunkSection section : chunk.getSections()) {
            if (section != null && !section.hasOnlyAir()) {
                solid++;
            }
        }
        return solid;
    }

    /** Relative column sample: blocks at minY+{126,125,124,120,60,10} (replica vs plane). */
    @Unique
    private static String farlands$column(net.minecraft.world.level.chunk.LevelChunk chunk) {
        int baseX = chunk.getPos().getMinBlockX() + 8;
        int baseZ = chunk.getPos().getMinBlockZ() + 8;
        int minY = chunk.getMinY();
        StringBuilder sb = new StringBuilder();
        for (int dy : new int[] {126, 125, 124, 120, 60, 10}) {
            sb.append(dy).append(':')
                .append(chunk.getBlockState(new net.minecraft.core.BlockPos(baseX, minY + dy, baseZ))
                    .getBlock())
                .append(' ');
        }
        return sb.toString().trim();
    }

    /** Sampled block-state digest of a chunk's window (for regeneration comparisons). */
    @Unique
    private static long farlands$digestProto(net.minecraft.world.level.chunk.ChunkAccess chunk) {
        long h = 1125899906842597L;
        var sections = chunk.getSections();
        int baseSection = chunk.getMinSectionY();
        for (int i = 0; i < sections.length; i++) {
            var section = sections[i];
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        h = h * 31L + (section.getBlockState(x, y, z).isAir() ? 0L : 1L);
                    }
                }
            }
        }
        return h;
    }

    private static long farlands$digest(net.minecraft.world.level.chunk.LevelChunk chunk) {
        long h = 1125899906842597L;
        int minY = chunk.getMinY();
        int maxY = chunk.getMaxY();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int y = minY; y <= maxY; y += 3) {
            for (int x = 0; x < 16; x += 4) {
                for (int z = 0; z < 16; z += 4) {
                    h = 31L * h + chunk.getBlockState(
                        new net.minecraft.core.BlockPos(baseX + x, y, baseZ + z)).toString().hashCode();
                }
            }
        }
        return h;
    }
}
