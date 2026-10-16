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
            com.farlands.g2.util.FarProjection.setEpoch(px, pz);
            int localX = (int) (px - com.farlands.g2.util.FarProjection.epochBlockX());
            int localZ = (int) (pz - com.farlands.g2.util.FarProjection.epochBlockZ());
            level.setRespawnData(new net.minecraft.world.level.storage.LevelData.RespawnData(
                net.minecraft.core.GlobalPos.of(
                    net.minecraft.world.level.Level.OVERWORLD,
                    new net.minecraft.core.BlockPos(localX, py, localZ)), 0.0f, 0.0f));
            System.out.println("[FarLands-Test] spawn set: real=(" + px + "," + py + "," + pz
                + ") epoch=(" + com.farlands.g2.util.FarProjection.epochBlockX() + ","
                + com.farlands.g2.util.FarProjection.epochBlockZ() + ") local=(" + localX + "," + localZ + ")");
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
                        net.minecraft.server.level.TicketType.FORCED, widePos, 2 + Math.max(0, n - 1));
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
                                    new net.minecraft.world.level.ChunkPos(cxL + dx, czL + dz).packWide()) != null) {
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
                    StringBuilder edgeMiss = new StringBuilder();
                    for (int dx3 = -44; dx3 <= 44; dx3++) {
                        for (int dz3 = -44; dz3 <= 44; dz3++) {
                            int rr = Math.max(Math.abs(dx3), Math.abs(dz3));
                            if (rr < 41 || rr > 44) {
                                continue;
                            }
                            if (cm.farlands$getUpdatingChunkW(
                                new net.minecraft.world.level.ChunkPos(cxL + dx3, czL + dz3).packWide()) == null) {
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
                        wideHolder = cm.farlands$getUpdatingChunkW(widePos.packWide());
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
                                new net.minecraft.world.level.ChunkPos(cxL + dx, czL + dz).packWide());
                            nb.append(dx).append(',').append(dz).append('=')
                                .append(nh == null ? "null" : String.valueOf(nh.getTicketLevel())).append(' ');
                        }
                    }
                    System.out.println("[FarLands-Test] wide nb " + nb);
                    StringBuilder miss = new StringBuilder();
                    for (int dx = -3; dx <= 3; dx++) {
                        for (int dz = -3; dz <= 3; dz++) {
                            if (cm.farlands$getUpdatingChunkW(
                                new net.minecraft.world.level.ChunkPos(cxL + dx, czL + dz).packWide()) == null) {
                                miss.append(dx).append(',').append(dz).append(' ');
                            }
                        }
                    }
                    System.out.println("[FarLands-Test] wide miss(7x7): " + miss);
                    System.out.flush();
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
}
