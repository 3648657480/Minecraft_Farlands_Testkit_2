package com.farlands.g2.client;

import com.farlands.g2.runtime.WindowedChunk;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Single-player band bridge: after the server regenerates a chunk's window, push
 * the new sections to the corresponding client chunk.
 *
 * <p>This is a same-JVM, integrated-server shortcut (no protocol change): sections
 * are serialised on the server thread and applied on the client thread, so no
 * live section object is shared across threads. Dedicated servers skip it (the
 * client classes are absent; the caller catches the linkage error).</p>
 *
 * <p>After a teleport the client chunk can still be missing when the server
 * finishes regenerating, so the push is queued and retried from the client tick
 * until the chunk exists (bounded).</p>
 */
public final class ClientBandBridge {

    private static final int MAX_ATTEMPTS = 400;
    private static final Map<Long, Pending> PENDING = new ConcurrentHashMap<>();

    private record Pending(ChunkPos pos, int centerSectionY, int minSectionY, byte[] data, int attempts) {
    }

    private ClientBandBridge() {
    }

    public static void push(ChunkPos pos, int centerSectionY, int minSectionY,
        List<LevelChunkSection> sections) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || !mc.hasSingleplayerServer()) {
            System.out.println("[YBRIDGE] skip push: client=" + (mc != null)
                + " level=" + (mc != null && mc.level != null)
                + " singleplayer=" + (mc != null && mc.hasSingleplayerServer()));
            System.out.flush();
            return; // integrated server only: multiplayer is unsupported
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(sections.size());
        for (int i = 0; i < sections.size(); i++) {
            buffer.writeVarInt(minSectionY + i);
            sections.get(i).write(buffer);
        }
        byte[] data = ByteBufUtil.getBytes(buffer);
        PENDING.put(key(pos), new Pending(pos, centerSectionY, minSectionY, data, 0));
        System.out.println("[YBRIDGE] push pos=" + pos + " band=" + minSectionY + " sections=" + sections.size());
        System.out.flush();
        mc.execute(ClientBandBridge::retry);
    }

    /** Called from the client tick: apply every queued push whose chunk now exists. */
    public static void retry() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || PENDING.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, Pending> entry : List.copyOf(PENDING.entrySet())) {
            Pending pending = entry.getValue();
            if (apply(mc, pending)) {
                PENDING.remove(entry.getKey());
            } else {
                int attempts = pending.attempts() + 1;
                if (attempts > MAX_ATTEMPTS) {
                    PENDING.remove(entry.getKey());
                    System.out.println("[YBRIDGE] give up pos=" + pending.pos());
                    System.out.flush();
                } else {
                    PENDING.put(entry.getKey(), new Pending(pending.pos(),
                        pending.centerSectionY(), pending.minSectionY(), pending.data(), attempts));
                }
            }
        }
    }

    private static boolean apply(Minecraft mc, Pending pending) {
        ChunkPos pos = pending.pos();
        var chunk = mc.level.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false);
        if (!(chunk instanceof LevelChunk levelChunk) || !(chunk instanceof WindowedChunk windowed)) {
            if (pending.attempts() == 0) {
                System.out.println("[YBRIDGE] waiting pos=" + pos + " chunk=" + (chunk == null ? "null"
                    : chunk.getClass().getSimpleName()));
                System.out.flush();
            }
            return false;
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(pending.data()));
        int count = buffer.readVarInt();
        for (int i = 0; i < count; i++) {
            int sectionY = buffer.readVarInt();
            LevelChunkSection section = new LevelChunkSection(mc.level.palettedContainerFactory());
            section.read(buffer);
            windowed.farlands$allSections().put(windowed.farlands$key(sectionY), section);
        }
        windowed.farlands$moveWindowToStart(pending.minSectionY());
        int minSy = windowed.farlands$windowMinSectionY();
        var lightEngine = mc.level.getLightEngine();
        var skyLight = new net.minecraft.world.level.chunk.DataLayer(15);
        var blockLight = new net.minecraft.world.level.chunk.DataLayer(0);
        for (int i = 0; i < levelChunk.getSections().length; i++) {
            int sy = minSy + i;
            mc.level.setSectionDirtyWithNeighbors(pos.x(), sy, pos.z());
            // The bridge bypasses the chunk packet, so no light data arrives with the
            // sections; give them full sky light (and no block light) or they render black.
            var sectionPos = net.minecraft.core.SectionPos.of(pos.x(), sy, pos.z());
            lightEngine.queueSectionData(net.minecraft.world.level.LightLayer.SKY, sectionPos, skyLight);
            lightEngine.queueSectionData(net.minecraft.world.level.LightLayer.BLOCK, sectionPos, blockLight);
        }
        int nonAir = 0;
        for (var s : levelChunk.getSections()) {
            if (s != null && !s.hasOnlyAir()) nonAir++;
        }
        int midSy = (int) windowed.farlands$windowMinSectionY() + levelChunk.getSections().length / 2;
        var midPos = new net.minecraft.core.BlockPos(pos.x() * 16 + 8, midSy * 16 + 8, pos.z() * 16 + 8);
        int idx = midSy - (int) windowed.farlands$windowMinSectionY();
        var section = idx >= 0 && idx < levelChunk.getSections().length
            ? levelChunk.getSections()[idx] : null;
        System.out.println("[YBRIDGE] applied pos=" + pos + " band=" + minSy
            + ".." + windowed.farlands$windowMaxSectionY() + " nonAirSections=" + nonAir
            + " readback=" + levelChunk.getBlockState(midPos)
            + " secMid=" + (section == null ? "null" : section.getBlockState(8, 8, 8)));
        System.out.flush();
        return true;
    }

    private static long key(ChunkPos pos) {
        return ((long) pos.x() << 32) ^ (pos.z() & 0xFFFFFFFFL);
    }
}
