package com.farlands.g2.runtime;

import com.scalarwide.mc.WideChunkPos;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Far chunks are processed in 5x5 batches. Each batch holds one radius-2 ticket
 * (needed by worldgen neighbours); the 25 chunks are serialised one per tick.
 * Tickets are kept so DistanceManager never loses the holder under a player
 * loading ticket.
 */
public final class FarChunkFeeder {

    private static ServerLevel level;
    private static ServerChunkCache cache;
    private static Queue<WideChunkPos> batches = new ArrayDeque<>();
    private static Queue<WideChunkPos> batchChunks = new ArrayDeque<>();
    private static WideChunkPos current;
    private static int currentTicks;
    private static int pushed;
    private static int batchIndex;
    private static int batchCount;
    private static int radius;
    private static Set<WideChunkPos> seen;
    private static boolean follow;
    private static WideChunkPos followCenter;
    private static int followRadius;

    private FarChunkFeeder() {
    }

    public static void start(ServerLevel serverLevel, WideChunkPos center, int r) {
        level = serverLevel;
        cache = (ServerChunkCache) serverLevel.getChunkSource();
        radius = r;
        pushed = 0;
        current = null;
        currentTicks = 0;
        batchIndex = 0;
        if (!follow || seen == null) {
            seen = new HashSet<>();
        } else {
            BigInteger cx = center.x().toBigInteger();
            BigInteger cz = center.z().toBigInteger();
            seen.removeIf(pos -> Math.max(
                Math.abs(pos.x().toBigInteger().subtract(cx).longValue()),
                Math.abs(pos.z().toBigInteger().subtract(cz).longValue())) > r);
        }
        batches = new ArrayDeque<>();
        batchChunks = new ArrayDeque<>();

        if (r <= 2) {
            batches.add(center);
        } else {
            int first = -r + 2;
            int last = r - 2;
            for (int dx = first; dx <= last; dx += 4) {
                for (int dz = first; dz <= last; dz += 4) {
                    batches.add(WideChunkPos.of(
                        center.x().toBigInteger().add(BigInteger.valueOf(dx)),
                        center.z().toBigInteger().add(BigInteger.valueOf(dz))));
                }
            }
        }
        batchCount = batches.size();
        System.out.println("[FARPUSH] radius=" + r + " batches=" + batchCount);
        System.out.flush();
    }

    /** Enable sliding-window mode: after each window is pushed, re-centre on the player. */
    public static void startFollow(ServerLevel serverLevel, WideChunkPos center, int r) {
        follow = true;
        followCenter = center;
        followRadius = r;
        start(serverLevel, center, r);
    }

    private static void followTick(MinecraftServer server) {
        if (!follow || !idle()) {
            return;
        }
        var players = server.getPlayerList().getPlayers();
        if (players == null || players.isEmpty()) {
            return;
        }
        var player = players.get(0);
        BigInteger cx = BigInteger.valueOf(
            Math.floorDiv((long) Math.floor(player.getX()), 16L));
        BigInteger cz = BigInteger.valueOf(
            Math.floorDiv((long) Math.floor(player.getZ()), 16L));
        WideChunkPos center = WideChunkPos.of(cx, cz);
        if (center.equals(followCenter)) {
            return;
        }
        long dx = center.x().toBigInteger()
            .subtract(followCenter.x().toBigInteger()).abs().longValue();
        long dz = center.z().toBigInteger()
            .subtract(followCenter.z().toBigInteger()).abs().longValue();
        int threshold = Integer.getInteger("farlands.follow.threshold", 3);
        if (Math.max(dx, dz) < threshold) {
            return;
        }
        followCenter = center;
        start(level, center, followRadius);
    }

    public static boolean idle() {
        return batches.isEmpty() && batchChunks.isEmpty() && current == null;
    }

    public static void stop() {
        batches.clear();
        batchChunks.clear();
        current = null;
        level = null;
        cache = null;
        System.out.println("[FARPUSH] stopped");
        System.out.flush();
    }

    public static void tick(MinecraftServer server) {
        if (level == null || cache == null) {
            return;
        }
        followTick(server);
        if (follow && idle()) {
            return;
        }
        int perTick = Math.max(1, Integer.getInteger("farlands.follow.chunksPerTick", 4));
        for (int n = 0; n < perTick; n++) {
            if (!tickOnce()) {
                return;
            }
        }
    }

    private static boolean tickOnce() {
        if (level == null || cache == null) {
            return false;
        }
        if (current == null) {
            if (batchChunks.isEmpty()) {
                if (batches.isEmpty()) {
                    System.out.println("[FARPUSH] done pushed=" + pushed
                        + " radius=" + radius + " batches=" + batchCount);
                    System.out.flush();
                    level = null;
                    cache = null;
                    return false;
                }
                WideChunkPos batchCenter = batches.poll();
                batchIndex++;
                batchChunks = new ArrayDeque<>();
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        WideChunkPos pos = WideChunkPos.of(
                            batchCenter.x().toBigInteger().add(BigInteger.valueOf(dx)),
                            batchCenter.z().toBigInteger().add(BigInteger.valueOf(dz)));
                        if (seen.add(pos)) {
                            batchChunks.add(pos);
                        }
                    }
                }
                cache.farlands$addTicketWithRadiusW(TicketType.FORCED,
                    new ChunkPos(batchCenter), 3);
                System.out.println("[FARPUSH] batch=" + batchIndex + "/" + batchCount
                    + " center=" + batchCenter + " chunks=" + batchChunks.size());
                System.out.flush();
            }
            current = batchChunks.poll();
            if (current == null) {
                return false;
            }
            currentTicks = 0;
        }

        currentTicks++;
        ChunkPos pos = new ChunkPos(current);
        var holder = cache.chunkMap.farlands$getUpdatingChunkW(current);
        if (holder == null || !holder.getFullChunkFuture().isDone()) {
            if (currentTicks > 1200) {
                System.out.println("[FARPUSH] timeout " + current);
                System.out.flush();
                current = null;
            }
            return false;
        }

        LevelChunk chunk = cache.farlands$getChunkWide(pos);
        if (chunk == null) {
            if (currentTicks > 1200) {
                System.out.println("[FARPUSH] chunk-null timeout " + current);
                System.out.flush();
                current = null;
            }
            return false;
        }
        push(chunk);
        pushed++;
        current = null;
        return true;
    }

    private static void push(LevelChunk chunk) {
        if (!(chunk instanceof WindowedChunk windowed)) {
            return;
        }
        int minSy = windowed.farlands$windowMinSectionY();
        var sections = chunk.getSections();
        var buffer = new net.minecraft.network.FriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer());
        buffer.writeVarInt(sections.length);
        buffer.writeVarInt(minSy);
        for (var section : sections) {
            section.write(buffer);
        }
        byte[] data = io.netty.buffer.ByteBufUtil.getBytes(buffer);
        WideChunkPacketStore.put(new WideChunkPacketStore.Packet(
            chunk.getPos().widePos(), minSy, sections.length, data));
        System.out.println("[WIDEPUSH] pos=" + chunk.getPos().widePos()
            + " minSy=" + minSy + " sections=" + sections.length
            + " bytes=" + data.length);
        System.out.flush();
    }
}
