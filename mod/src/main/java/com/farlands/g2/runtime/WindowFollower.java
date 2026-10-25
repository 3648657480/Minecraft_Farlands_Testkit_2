package com.farlands.g2.runtime;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Y-D: keeps a chunk's vertical window following the player.
 *
 * <p>Enabled by {@code -Dfarlands.y.follow=true}. When the player's section Y
 * leaves the comfortable band around the current window, the chunk is
 * regenerated in place at the new band ({@link WindowRegenerator}).</p>
 *
 * <p>Throttled per chunk (one regeneration attempt per {@link #COOLDOWN_TICKS})
 * and de-duplicated while a regeneration is in flight.</p>
 */
public final class WindowFollower {

    /** Sections kept as margin inside the window before a move is triggered. */
    private static final int MARGIN = 4;
    private static final int COOLDOWN_TICKS = 40;

    private static final Map<ChunkPos, Integer> NEXT_ATTEMPT = new ConcurrentHashMap<>();
    private static final Set<ChunkPos> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Set<java.util.UUID> HOLDING = ConcurrentHashMap.newKeySet();
    private static boolean overridesLoaded;

    private WindowFollower() {
    }

    public static void tick(MinecraftServer server) {
        if (!Boolean.getBoolean("farlands.y.follow")) {
            return;
        }
        if (!overridesLoaded) {
            overridesLoaded = true;
            com.farlands.g2.runtime.PageStore.clear();
            YBandOverrides.load(server.getWorldPath(
                net.minecraft.world.level.storage.LevelResource.ROOT).resolve("farlands_bands.txt"));
        }
        int tick = server.getTickCount();
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                followPlayer(level, player, tick);
                // Neighbouring chunks need the same band: the client marks a section
                // dirty only when all four neighbours exist at that window, so a lone
                // banded column never compiles on re-entry.
                // Ticked chunks are bounded by the simulation distance; visible ones
                // by the view distance — take the larger of the two.
                int view = server.getPlayerList().getViewDistance();
                int sim = server.getPlayerList().getSimulationDistance();
                int cap = Integer.getInteger("farlands.y.followRadiusMax", 16);
                int radius = Math.min(Integer.getInteger("farlands.y.followRadius",
                    Math.max(view, sim)), cap);
                int budget = Integer.getInteger("farlands.y.followBudget", 4);
                int issued = 0;
                int pcx = player.chunkPosition().x();
                int pcz = player.chunkPosition().z();
                int ps = player.blockPosition().getY() >> 4;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        if (level.getChunkSource().getChunkNow(pcx + dx, pcz + dz)
                            instanceof LevelChunk neighbour) {
                            if (issued >= budget) {
                                break;
                            }
                            if (follow(neighbour, ps, tick)) {
                                issued++;
                            }
                        }
                    }
                }
            }
        }
    }

    private static int farlands$clampCenter(int target, int len) {
        int half = (len - 1) / 2;
        int min = target - half;
        int max = min + len - 1;
        int maxPlay = VerticalBounds.MAX_PLAYABLE_SECTION;
        int minPlay = VerticalBounds.MIN_PLAYABLE_SECTION;
        if (max > maxPlay) {
            int d = max - maxPlay;
            min -= d;
            max -= d;
        }
        if (min < minPlay) {
            int d = minPlay - min;
            min += d;
            max += d;
        }
        return min + half;
    }

    private static void followPlayer(ServerLevel level, ServerPlayer player, int tick) {
        int cx = player.chunkPosition().x();
        int cz = player.chunkPosition().z();
        if (!(level.getChunkSource().getChunkNow(cx, cz) instanceof LevelChunk chunk)) {
            farlands$hold(player, true);
            return;
        }
        int playerSection = player.blockPosition().getY() >> 4;
        if (chunk instanceof WindowedChunk windowed) {
            int min = windowed.farlands$windowMinSectionY();
            int max = windowed.farlands$windowMaxSectionY();
            // Pages are 384 blocks tall: anywhere inside the page is fine, only a
            // page change (or an in-flight regeneration) needs the hold.
            boolean inside = playerSection >= min && playerSection <= max;
            // A falling player outruns the seconds-long regeneration; hold them while
            // the window is being moved so the band can catch up instead of chasing.
            farlands$hold(player, !inside || IN_FLIGHT.contains(chunk.getPos()));
        }
        follow(chunk, playerSection, tick);
    }

    private static void farlands$hold(ServerPlayer player, boolean hold) {
        if (hold) {
            if (HOLDING.add(player.getUUID())) {
                player.setNoGravity(true);
            }
        } else if (HOLDING.remove(player.getUUID())) {
            player.setNoGravity(false);
        }
    }

    /** P2: generate the adjacent page when the player approaches a page edge. */
    private static void maybePrefetch(LevelChunk chunk, int playerSection, int min, int max,
        int len, int pageY) {
        if (!Boolean.getBoolean("farlands.y.window")) {
            return;
        }
        if (Integer.getInteger("farlands.y.prefetchPages", 1) <= 0) {
            return;
        }
        // Dimension-band chunks are not pages; only far pages prefetch.
        if (chunk.getLevel().getMinSectionY() == min) {
            return;
        }
        int margin = Integer.getInteger("farlands.y.prefetchMargin", 4);
        int depth = Math.min(Integer.getInteger("farlands.y.viewPages", 4), 16);
        int centerOffset = (len - 1) / 2;
        // Travel direction: prefetch depth pages towards the edge the player is
        // approaching (P3 viewPages budget).
        int direction = (playerSection - min < max - playerSection) ? -1 : 1;
        if (playerSection - min < margin || max - playerSection < margin) {
            for (int d = 1; d <= depth; d++) {
                WindowRegenerator.prefetch(chunk, (pageY + direction * d) * len + centerOffset);
            }
        }
    }

    /** Follow one chunk to a target section Y (also used by the testgen probe). */
    public static boolean follow(LevelChunk chunk, int targetSectionY, int tick) {
        if (!(chunk instanceof WindowedChunk windowed)) {
            return false;
        }
        // Normal Y: the dimension band IS the page. If the chunk is still on a far
        // page (returning from high Y), move it back and re-prime the heightmaps.
        if (targetSectionY >= chunk.getLevel().getMinSectionY()
            && targetSectionY <= chunk.getLevel().getMaxSectionY()) {
            int dimMin = chunk.getLevel().getMinSectionY();
            if (windowed.farlands$windowMinSectionY() == dimMin) {
                return false;
            }
            windowed.farlands$moveWindowToStart(dimMin);
            com.farlands.g2.runtime.Heightmaps.primeFully(chunk, java.util.EnumSet.of(
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR));
            try {
                com.farlands.g2.client.ClientBandBridge.push(chunk.getPos(),
                    dimMin + (windowed.farlands$windowMaxSectionY() - dimMin) / 2, dimMin,
                    java.util.Arrays.asList(chunk.getSections()));
            } catch (Throwable ignored) {
                // dedicated server: client classes absent
            }
            System.out.println("[YFOLLOW] back to dimension band pos=" + chunk.getPos()
                + " band=" + dimMin + ".." + windowed.farlands$windowMaxSectionY());
            System.out.flush();
            return true;
        }
        int min = windowed.farlands$windowMinSectionY();
        int max = windowed.farlands$windowMaxSectionY();
        int len = max - min + 1;
        int pageY = Math.floorDiv(targetSectionY, len);
        int pageMin = com.farlands.g2.runtime.VerticalBounds.pageStart(targetSectionY, len);
        if (pageMin == min) {
            maybePrefetch(chunk, targetSectionY, min, max, len, pageY);
            return false; // already on that page; moving inside a page is free
        }
        // P2: crossing a page boundary — use a prepared page when available.
        if (WindowRegenerator.applyPrepared(chunk, targetSectionY)) {
            return true;
        }
        int target = pageMin + (len - 1) / 2; // page centre for the regenerate call
        ChunkPos pos = chunk.getPos();
        Integer next = NEXT_ATTEMPT.get(pos);
        if (next != null && tick < next) {
            return false;
        }
        if (!IN_FLIGHT.add(pos)) {
            return false;
        }
        NEXT_ATTEMPT.put(pos, tick + COOLDOWN_TICKS);
        System.out.println("[YFOLLOW] trigger pos=" + pos + " target=" + target
            + " window=" + min + ".." + max);
        System.out.flush();
        WindowRegenerator.regenerate(chunk, target, ok -> {
            IN_FLIGHT.remove(pos);
            YBandOverrides.save(chunk.getLevel().getServer().getWorldPath(
                net.minecraft.world.level.storage.LevelResource.ROOT).resolve("farlands_bands.txt"));
            System.out.println("[YFOLLOW] done ok=" + ok + " pos=" + pos
                + " band=" + windowed.farlands$windowMinSectionY()
                + ".." + windowed.farlands$windowMaxSectionY()
                + " sections=" + chunk.getSections().length);
            System.out.flush();
        });
        return true;
    }
}
