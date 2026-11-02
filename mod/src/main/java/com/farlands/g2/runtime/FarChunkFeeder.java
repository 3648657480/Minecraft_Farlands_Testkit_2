package com.farlands.g2.runtime;

import com.scalarwide.mc.WideChunkPos;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Queue;
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
        if (current == null) {
            if (batchChunks.isEmpty()) {
                if (batches.isEmpty()) {
                    System.out.println("[FARPUSH] done pushed=" + pushed
                        + " radius=" + radius + " batches=" + batchCount);
                    System.out.flush();
                    level = null;
                    cache = null;
                    return;
                }
                WideChunkPos batchCenter = batches.poll();
                batchIndex++;
                batchChunks = new ArrayDeque<>();
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        batchChunks.add(WideChunkPos.of(
                            batchCenter.x().toBigInteger().add(BigInteger.valueOf(dx)),
                            batchCenter.z().toBigInteger().add(BigInteger.valueOf(dz))));
                    }
                }
                cache.farlands$addTicketWithRadiusW(TicketType.FORCED,
                    new ChunkPos(batchCenter), 2);
                System.out.println("[FARPUSH] batch=" + batchIndex + "/" + batchCount
                    + " center=" + batchCenter + " chunks=" + batchChunks.size());
                System.out.flush();
            }
            current = batchChunks.poll();
            if (current == null) {
                return;
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
            return;
        }

        LevelChunk chunk = cache.farlands$getChunkWide(pos);
        if (chunk == null) {
            if (currentTicks > 1200) {
                System.out.println("[FARPUSH] chunk-null timeout " + current);
                System.out.flush();
                current = null;
            }
            return;
        }
        push(chunk);
        pushed++;
        current = null;
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
