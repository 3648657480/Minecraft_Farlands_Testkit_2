package com.farlands.harness;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * F0-2 measurement harness (vanilla side).
 *
 * <p>This is a trimmed copy of the FarLands mod's testgen hook: it only forces
 * generation of a fixed chunk set, settles, saves and halts. It contains NO
 * worldgen mixins and no far-coordinate logic; the game jar it runs on is
 * unpatched. It exists so both sides of the comparison use the same forcing
 * mechanism and the same tick timing.</p>
 */
@Mixin(MinecraftServer.class)
public abstract class TestGenMixin {

    @Unique
    private static boolean harness$testgenDone = false;

    @Unique
    private static int harness$saveCountdown = -1;

    @Unique
    private static ServerLevel harness$scanLevel = null;

    @Unique
    private static int harness$scanCx;

    @Unique
    private static int harness$scanCz;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void harness$testgen(BooleanSupplier hasTime, CallbackInfo ci) {
        MinecraftServer self = (MinecraftServer) (Object) this;
        if (!harness$testgenDone && self.getTickCount() >= Integer.getInteger("farlands.testgen.delay", 0)) {
            harness$testgenDone = true;
            runTestGen(self);
        }
        if (harness$saveCountdown > 0) {
            harness$saveCountdown--;
            if (harness$saveCountdown == 0) {
                if (harness$scanLevel != null) {
                    harness$scanHolders(harness$scanLevel, harness$scanCx, harness$scanCz);
                }
                self.saveAllChunks(true, true, true);
                System.out.println("[FarLands-Test] DONE saved");
                System.out.flush();
                self.halt(false);
            }
        }
    }

    @Unique
    private static ServerLevel harness$testLevel(MinecraftServer self) {
        String dim = System.getProperty("farlands.testgen.dim", "overworld");
        return switch (dim) {
            case "the_end", "end", "ender" -> self.getLevel(net.minecraft.world.level.Level.END);
            case "the_nether", "nether" -> self.getLevel(net.minecraft.world.level.Level.NETHER);
            default -> self.overworld();
        };
    }

    @Unique
    private static void runTestGen(MinecraftServer self) {
        String spec = System.getProperty("farlands.testgen");
        if (spec == null || spec.isEmpty()) return;
        ServerLevel level = harness$testLevel(self);
        System.out.println("[FarLands-Test] dimension=" + level.dimension().identifier());
        System.out.flush();
        try {
            // Same determinism control as the subject harness: random ticks use
            // the level RNG and grow plants, varying content between runs.
            level.getGameRules().set(
                net.minecraft.world.level.gamerules.GameRules.RANDOM_TICK_SPEED, 0, self);
            System.out.println("[FarLands-Test] random_tick_speed=0 (determinism)");
            System.out.flush();
            for (String entry : spec.split(";")) {
                if (entry.trim().isEmpty()) continue;
                String[] parts = entry.trim().split(",");
                int cx = Integer.parseInt(parts[0].trim());
                int cz = Integer.parseInt(parts[1].trim());
                int n = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 1;
                long t0 = System.currentTimeMillis();
                ChunkAccess chunk = null;
                if (Boolean.getBoolean("farlands.testgen.ticket")) {
                    int radius = 3 + Math.max(0, n - 1);
                    net.minecraft.server.level.ServerChunkCache sc =
                        (net.minecraft.server.level.ServerChunkCache) level.getChunkSource();
                    sc.addTicketAndLoadWithRadius(
                        net.minecraft.server.level.TicketType.FORCED,
                        new net.minecraft.world.level.ChunkPos(cx, cz), radius);
                    harness$scanLevel = level;
                    harness$scanCx = cx;
                    harness$scanCz = cz;
                    chunk = level.getChunk(cx, cz);
                } else {
                    for (int dx = 0; dx < n; dx++) {
                        for (int dz = 0; dz < n; dz++) {
                            chunk = level.getChunk(cx + dx, cz + dz);
                        }
                    }
                }
                long genMs = System.currentTimeMillis() - t0;
                int top = chunk != null ? chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8) : -999;
                String wgHash = chunk != null ? wgHash(chunk) : "none";
                System.out.println("[FarLands-Test] gen OK region (" + cx + "," + cz + ")+" + n + "x" + n
                    + " topY=" + top + " timeMs=" + genMs + " wgHash=" + wgHash);
                System.out.flush();
            }
            if (System.getProperty("farlands.testgen.stop") != null) {
                harness$saveCountdown = Integer.getInteger("farlands.testgen.settle", 200);
                System.out.println("[FarLands-Test] settling " + harness$saveCountdown + " ticks before save");
                System.out.flush();
            }
        } catch (Throwable t) {
            System.out.println("[FarLands-Test] gen FAILED: " + t);
            t.printStackTrace(System.out);
            System.out.flush();
        }
    }

    @Unique
    private static void harness$scanHolders(ServerLevel level, int cx, int cz) {
        try {
            net.minecraft.server.level.ServerChunkCache sc =
                (net.minecraft.server.level.ServerChunkCache) level.getChunkSource();
            net.minecraft.server.level.ChunkMap cm = sc.chunkMap;
            java.lang.reflect.Field f = net.minecraft.server.level.ChunkMap.class
                .getDeclaredField("updatingChunkMap");
            f.setAccessible(true);
            Object raw = f.get(cm);
            if (!(raw instanceof it.unimi.dsi.fastutil.longs.Long2ObjectMap<?> map)) {
                System.out.println("[harness] scan: unexpected map " + raw);
                return;
            }
            int maxRing = -1;
            int holders = 0;
            Integer cellLevel = null;
            for (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<?> e : map.long2ObjectEntrySet()) {
                net.minecraft.world.level.ChunkPos p = net.minecraft.world.level.ChunkPos.unpack(e.getLongKey());
                int dx = p.x() - cx;
                int dz = p.z() - cz;
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                if (ring > maxRing) {
                    maxRing = ring;
                }
                holders++;
                if (dx == 16 && dz == -13
                    && e.getValue() instanceof net.minecraft.server.level.GenerationChunkHolder h) {
                    cellLevel = h.getTicketLevel();
                }
            }
            System.out.println("[harness] scan centre=(" + cx + "," + cz + ") holders=" + holders
                + " maxRing=" + maxRing + " cell(16,-13)=" + (cellLevel == null ? "none" : cellLevel));
            System.out.flush();
        } catch (Throwable t) {
            System.out.println("[harness] scan FAILED: " + t);
            t.printStackTrace(System.out);
            System.out.flush();
        }
    }

    /** Same deterministic fingerprint as the subject harness (see mod testgen). */
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
