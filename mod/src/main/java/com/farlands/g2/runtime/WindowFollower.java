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
                int radius = Integer.getInteger("farlands.y.followRadius", 0);
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
                            follow(neighbour, ps, tick);
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
            boolean inside = playerSection >= min + 2 && playerSection <= max - 2;
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

    /** Follow one chunk to a target section Y (also used by the testgen probe). */
    public static boolean follow(LevelChunk chunk, int targetSectionY, int tick) {
        if (!(chunk instanceof WindowedChunk windowed)) {
            return false;
        }
        int min = windowed.farlands$windowMinSectionY();
        int max = windowed.farlands$windowMaxSectionY();
        int len = max - min + 1;
        int center = min + (len - 1) / 2;
        int target = farlands$clampCenter(targetSectionY, len);
        // At the top/bottom of the playable range the window cannot centre on the
        // player; without this the follower would regenerate forever.
        if (target == center) {
            return false;
        }
        if (target >= min + MARGIN && target <= max - MARGIN) {
            return false;
        }
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
