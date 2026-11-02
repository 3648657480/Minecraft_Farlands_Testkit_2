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
 * Vanilla-style incremental far chunk feeder: one chunk per server tick.
 * Generation stays on the vanilla worker pool; this class never joins futures
 * or spins. Tickets cover only the chunk currently being processed.
 */
public final class FarChunkFeeder {

    private static ServerLevel level;
    private static ServerChunkCache cache;
    private static Queue<WideChunkPos> queue = new ArrayDeque<>();
    private static WideChunkPos current;
    private static int currentTicks;
    private static int pushed;
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
        queue = new ArrayDeque<>();
        // centre first, then rings; keeps the first visible data closest.
        for (int ring = 0; ring <= r; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    queue.add(WideChunkPos.of(
                        center.x().toBigInteger().add(BigInteger.valueOf(dx)),
                        center.z().toBigInteger().add(BigInteger.valueOf(dz))));
                }
            }
        }
        System.out.println("[FARPUSH] queue=" + queue.size() + " radius=" + r);
        System.out.flush();
    }

    public static boolean idle() {
        return queue.isEmpty() && current == null;
    }

    /** Called before stopServer: the current ticket must not keep save futures alive. */
    public static void stop() {
        if (current != null && cache != null) {
            cache.farlands$removeTicketWithRadiusW(TicketType.FORCED,
                new ChunkPos(current), 0);
        }
        queue.clear();
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
            current = queue.poll();
            if (current == null) {
                System.out.println("[FARPUSH] done pushed=" + pushed + " radius=" + radius);
                System.out.flush();
                level = null;
                cache = null;
                return;
            }
            ChunkPos pos = new ChunkPos(current);
            cache.farlands$addTicketWithRadiusW(TicketType.FORCED, pos, 0);
            currentTicks = 0;
        }

        currentTicks++;
        ChunkPos pos = new ChunkPos(current);
        var holder = cache.chunkMap.farlands$getUpdatingChunkW(current);
        if (holder == null || !holder.getFullChunkFuture().isDone()) {
            if (currentTicks > 1200) {
                System.out.println("[FARPUSH] timeout " + current);
                System.out.flush();
                cache.farlands$removeTicketWithRadiusW(TicketType.FORCED, pos, 0);
                current = null;
            }
            return;
        }

        LevelChunk chunk = cache.farlands$getChunkWide(pos);
        if (chunk == null) {
            if (currentTicks > 1200) {
                System.out.println("[FARPUSH] chunk-null timeout " + current);
                System.out.flush();
                cache.farlands$removeTicketWithRadiusW(TicketType.FORCED, pos, 0);
                current = null;
            }
            return;
        }
        push(chunk);
        pushed++;
        cache.farlands$removeTicketWithRadiusW(TicketType.FORCED, pos, 0);
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
