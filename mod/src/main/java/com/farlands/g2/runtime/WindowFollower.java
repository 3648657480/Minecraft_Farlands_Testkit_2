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

    private WindowFollower() {
    }

    public static void tick(MinecraftServer server) {
        if (!Boolean.getBoolean("farlands.y.follow")) {
            return;
        }
        int tick = server.getTickCount();
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                followPlayer(level, player, tick);
            }
        }
    }

    private static void followPlayer(ServerLevel level, ServerPlayer player, int tick) {
        int cx = player.chunkPosition().x();
        int cz = player.chunkPosition().z();
        if (!(level.getChunkSource().getChunkNow(cx, cz) instanceof LevelChunk chunk)) {
            return;
        }
        int playerSection = player.blockPosition().getY() >> 4;
        follow(chunk, playerSection, tick);
    }

    /** Follow one chunk to a target section Y (also used by the testgen probe). */
    public static boolean follow(LevelChunk chunk, int targetSectionY, int tick) {
        if (!(chunk instanceof WindowedChunk windowed)) {
            return false;
        }
        int min = windowed.farlands$windowMinSectionY();
        int max = windowed.farlands$windowMaxSectionY();
        if (targetSectionY >= min + MARGIN && targetSectionY <= max - MARGIN) {
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
        System.out.println("[YFOLLOW] trigger pos=" + pos + " target=" + targetSectionY
            + " window=" + min + ".." + max);
        System.out.flush();
        WindowRegenerator.regenerate(chunk, targetSectionY, ok -> {
            IN_FLIGHT.remove(pos);
            System.out.println("[YFOLLOW] done ok=" + ok + " pos=" + pos
                + " band=" + windowed.farlands$windowMinSectionY()
                + ".." + windowed.farlands$windowMaxSectionY()
                + " sections=" + chunk.getSections().length);
            System.out.flush();
        });
        return true;
    }
}
