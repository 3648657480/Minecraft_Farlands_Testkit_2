package com.farlands.g2.client;

import com.farlands.g2.runtime.WindowedChunk;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import io.netty.buffer.Unpooled;

/**
 * Single-player band bridge: after the server regenerates a chunk's window, push
 * the new sections to the corresponding client chunk.
 *
 * <p>This is a same-JVM, integrated-server shortcut (no protocol change): sections
 * are serialised on the server thread and applied on the client thread, so no
 * live section object is shared across threads. Dedicated servers skip it (the
 * client classes are absent; the caller catches the linkage error).</p>
 */
public final class ClientBandBridge {

    private ClientBandBridge() {
    }

    public static void push(ChunkPos pos, int centerSectionY, int minSectionY,
        List<LevelChunkSection> sections) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || !mc.hasSingleplayerServer()) {
            return; // integrated server only: multiplayer is unsupported
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(sections.size());
        for (int i = 0; i < sections.size(); i++) {
            buffer.writeVarInt(minSectionY + i);
            sections.get(i).write(buffer);
        }
        mc.execute(() -> apply(mc, pos, centerSectionY, buffer));
    }

    private static void apply(Minecraft mc, ChunkPos pos, int centerSectionY, FriendlyByteBuf buffer) {
        if (mc.level == null) {
            return;
        }
        var chunk = mc.level.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false);
        if (!(chunk instanceof LevelChunk levelChunk) || !(chunk instanceof WindowedChunk windowed)) {
            return;
        }
        int count = buffer.readVarInt();
        for (int i = 0; i < count; i++) {
            int sectionY = buffer.readVarInt();
            LevelChunkSection section = new LevelChunkSection(mc.level.palettedContainerFactory());
            section.read(buffer);
            windowed.farlands$allSections().put(windowed.farlands$key(sectionY), section);
        }
        windowed.farlands$moveWindowTo(centerSectionY);
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
        System.out.println("[YBRIDGE] applied pos=" + pos + " band=" + minSy
            + ".." + windowed.farlands$windowMaxSectionY());
        System.out.flush();
    }
}
