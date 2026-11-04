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
    private static boolean ringMode;
    private static WideChunkPos ringCenter;
    private static int ringRadius;
    private static Set<WideChunkPos> ringChunks;
    private static Queue<WideChunkPos> ringInnerQueue;
    private static Queue<WideChunkPos> ringOuterQueue;
    private static final ArrayDeque<WideChunkPos> ringPending = new ArrayDeque<>();
    private static boolean ringWarmup;

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
            java.util.List<Integer> centers = new java.util.ArrayList<>();
            for (int c = first; c <= last; c += 4) {
                centers.add(c);
            }
            if (!centers.contains(last)) {
                centers.add(last);
            }
            for (int dx : centers) {
                for (int dz : centers) {
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

    /** Sliding data ring: keep an 11x11 set and only push new edge chunks. */
    public static void startFollow(ServerLevel serverLevel, WideChunkPos center, int r) {
        follow = true;
        followCenter = center;
        followRadius = r;
        ringMode = true;
        ringCenter = center;
        ringRadius = r;
        level = serverLevel;
        cache = (ServerChunkCache) serverLevel.getChunkSource();
        radius = r;
        pushed = 0;
        current = null;
        currentTicks = 0;
        ringChunks = desiredRing(center, r);
        ringInnerQueue = new ArrayDeque<>();
        ringOuterQueue = new ArrayDeque<>();
        ringPending.clear();
        int innerR = Math.max(0, r - 1);
        for (WideChunkPos pos : ringChunks) {
            if (ringDistance(pos, center) <= innerR) {
                ringInnerQueue.add(pos);
            } else {
                ringOuterQueue.add(pos);
            }
        }
        ringWarmup = true;
        addRingTickets(center, r);
        System.out.println("[FARPUSH] ring start center=" + center
            + " inner=" + ringInnerQueue.size() + " outer=" + ringOuterQueue.size());
        System.out.flush();
    }

    private static long ringDistance(WideChunkPos pos, WideChunkPos center) {
        long dx = pos.x().toBigInteger().subtract(center.x().toBigInteger())
            .abs().longValue();
        long dz = pos.z().toBigInteger().subtract(center.z().toBigInteger())
            .abs().longValue();
        return Math.max(dx, dz);
    }

    private static Set<WideChunkPos> desiredRing(WideChunkPos center, int r) {
        Set<WideChunkPos> set = new HashSet<>();
        BigInteger cx = center.x().toBigInteger();
        BigInteger cz = center.z().toBigInteger();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                set.add(WideChunkPos.of(cx.add(BigInteger.valueOf(dx)),
                    cz.add(BigInteger.valueOf(dz))));
            }
        }
        return set;
    }

    private static void addRingTickets(WideChunkPos center, int r) {
        int off = Math.max(1, r - 2);
        for (int dx = -off; dx <= off; dx += off * 2) {
            for (int dz = -off; dz <= off; dz += off * 2) {
                WideChunkPos t = WideChunkPos.of(
                    center.x().toBigInteger().add(BigInteger.valueOf(dx)),
                    center.z().toBigInteger().add(BigInteger.valueOf(dz)));
                cache.farlands$addTicketWithRadiusW(TicketType.FORCED,
                    new ChunkPos(t), 3);
            }
        }
    }

    private static void ringTick(MinecraftServer server) {
        var players = server.getPlayerList().getPlayers();
        if (players != null && !players.isEmpty()) {
            var player = players.get(0);
            BigInteger cx = BigInteger.valueOf(
                Math.floorDiv((long) Math.floor(player.getX()), 16L));
            BigInteger cz = BigInteger.valueOf(
                Math.floorDiv((long) Math.floor(player.getZ()), 16L));
            WideChunkPos center = WideChunkPos.of(cx, cz);
            if (!center.equals(ringCenter)) {
                Set<WideChunkPos> next = desiredRing(center, ringRadius);
                var additions = new java.util.ArrayList<WideChunkPos>();
                for (WideChunkPos pos : next) {
                    if (!ringChunks.contains(pos)) {
                        additions.add(pos);
                    }
                }
                ringChunks = next;
                ringCenter = center;
                int innerR = Math.max(0, ringRadius - 1);
                for (WideChunkPos pos : additions) {
                    if (ringDistance(pos, center) <= innerR) {
                        ringInnerQueue.add(pos);
                    } else {
                        ringOuterQueue.add(pos);
                    }
                }
                addRingTickets(center, ringRadius);
                System.out.println("[FARPUSH] ring recenter=" + center
                    + " +" + additions.size() + " inner=" + ringInnerQueue.size()
                    + " outer=" + ringOuterQueue.size());
                System.out.flush();
            }
        }
        int perTick = ringWarmup
            ? Math.max(1, Integer.getInteger("farlands.follow.warmupChunksPerTick", 16))
            : Math.max(1, Integer.getInteger("farlands.follow.chunksPerTick", 8));
        for (int n = 0; n < perTick && ringPushOnce(); n++) {
            // keep draining
        }
        if (ringWarmup && ringPending.isEmpty() && ringInnerQueue.isEmpty()
            && ringOuterQueue.isEmpty()) {
            ringWarmup = false;
            System.out.println("[FARPUSH] ring warmup done pushed=" + pushed);
            System.out.flush();
        }
    }

    private static boolean ringPushOnce() {
        for (var it = ringPending.iterator(); it.hasNext();) {
            WideChunkPos candidate = it.next();
            var holder = cache.chunkMap.farlands$getUpdatingChunkW(candidate);
            if (holder == null || !holder.getFullChunkFuture().isDone()) {
                continue;
            }
            it.remove();
            LevelChunk chunk = cache.farlands$getChunkWide(new ChunkPos(candidate));
            if (chunk != null) {
                push(chunk);
                pushed++;
                return true;
            }
        }
        int maxConcurrent = Math.max(8,
            Integer.getInteger("farlands.follow.concurrent", 16));
        while (ringPending.size() < maxConcurrent) {
            WideChunkPos candidate = ringInnerQueue.poll();
            if (candidate == null) {
                candidate = ringOuterQueue.poll();
            }
            if (candidate == null) {
                return false;
            }
            var holder = cache.chunkMap.farlands$getUpdatingChunkW(candidate);
            if (holder == null) {
                ringPending.add(candidate);
                continue;
            }
            if (holder.getFullChunkFuture().isDone()) {
                LevelChunk chunk = cache.farlands$getChunkWide(new ChunkPos(candidate));
                if (chunk != null) {
                    push(chunk);
                    pushed++;
                    return true;
                }
            }
            ringPending.add(candidate);
            if (ringPending.size() >= maxConcurrent) {
                break;
            }
        }
        return false;
    }

    public static boolean idle() {
        if (ringMode) {
            return current == null && ringPending.isEmpty()
                && ringInnerQueue.isEmpty() && ringOuterQueue.isEmpty();
        }
        return batches.isEmpty() && batchChunks.isEmpty() && current == null;
    }

    public static void stop() {
        batches.clear();
        batchChunks.clear();
        current = null;
        ringMode = false;
        ringChunks = null;
        ringInnerQueue = null;
        ringOuterQueue = null;
        ringPending.clear();
        level = null;
        cache = null;
        System.out.println("[FARPUSH] stopped");
        System.out.flush();
    }

    public static void tick(MinecraftServer server) {
        if (level == null || cache == null) {
            return;
        }
        if (ringMode) {
            ringTick(server);
            return;
        }
        if (follow && idle()) {
            return;
        }
        int perTick = Math.max(1, Integer.getInteger("farlands.follow.chunksPerTick", 8));
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
