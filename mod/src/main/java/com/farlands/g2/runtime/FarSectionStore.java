package com.farlands.g2.runtime;

import com.scalarwide.mc.WideSectionKey2560;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.storage.LevelResource;

/**
 * B-batch sidecar storage for the windowed section map.
 *
 * <p>Vanilla {@code SerializableChunkData} saves the light-engine section range
 * and stores section Y as a byte; a far window (e.g. 134217703) is therefore
 * never written. This class writes every section in {@code farlands$all} to a
 * per-chunk {@code .g2s} file (absolute int section Y + serialized section bytes,
 * gzip) and restores it at read time. The vanilla format is untouched, so
 * normal worlds and old saves keep working; the sidecar is additive.</p>
 *
 * <p>This is the active-window/all-in-memory first step: sections evicted from
 * {@code farlands$all} are not recoverable yet. It also keeps block entities at
 * old bands resolvable, because the sections they stand on are restored too.</p>
 */
public final class FarSectionStore {

    private static final int MAGIC = 0x47325331; // "G2S1"
    private static final int VERSION = 1;
    /** Only chunks whose window is outside the dimension band need a sidecar. */
    private static final long FAR_SECTION = 1_000_000L;

    private FarSectionStore() {
    }

    private static Path dir(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("farlands_sections");
    }

    private static Path file(ServerLevel level, ChunkPos pos) {
        return dir(level).resolve(pos.xLong() + "_" + pos.zLong() + ".g2s");
    }

    public static void save(ServerLevel level, ChunkAccess chunk) {
        if (!Boolean.parseBoolean(System.getProperty("farlands.farsex.save", "true"))) {
            return; // read-only verification run: do not overwrite the fixture
        }
        if (!(chunk instanceof WindowedChunk windowed)
            || !Boolean.getBoolean("farlands.y.window")) {
            return;
        }
        int min = windowed.farlands$windowMinSectionY();
        int max = windowed.farlands$windowMaxSectionY();
        if (Math.abs((long) min) < FAR_SECTION && Math.abs((long) max) < FAR_SECTION) {
            return; // dimension-band chunk: vanilla save is correct
        }
        Map<WideSectionKey2560, LevelChunkSection> all = windowed.farlands$allSections();
        List<Map.Entry<WideSectionKey2560, LevelChunkSection>> entries = new ArrayList<>();
        for (Map.Entry<WideSectionKey2560, LevelChunkSection> entry : all.entrySet()) {
            if (entry.getValue() != null) {
                entries.add(entry);
            }
        }
        if (entries.isEmpty()) {
            return;
        }
        Path out = file(level, chunk.getPos());
        try {
            Files.createDirectories(out.getParent());
            try (DataOutputStream data = new DataOutputStream(
                new GZIPOutputStream(Files.newOutputStream(out)))) {
                data.writeInt(MAGIC);
                data.writeInt(VERSION);
                data.writeInt(min);
                data.writeInt(entries.size());
                for (Map.Entry<WideSectionKey2560, LevelChunkSection> entry : entries) {
                    int sy = (int) entry.getKey().y().toBigInteger().longValueExact();
                    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                    entry.getValue().write(buffer);
                    byte[] bytes = ByteBufUtil.getBytes(buffer);
                    if (Boolean.getBoolean("farlands.y.diag")) {
                        System.out.println("[FARSEC] save sy=" + sy
                            + " center=" + entry.getValue().getBlockState(8, 8, 8)
                            + " air=" + entry.getValue().hasOnlyAir());
                        if (sy == 4) {
                            System.out.println("[FARSEC] save s4(9,0,9)="
                                + entry.getValue().getBlockState(9, 0, 9));
                        }
                    }
                    data.writeInt(sy);
                    data.writeInt(bytes.length);
                    data.write(bytes);
                }
            }
            if (Boolean.getBoolean("farlands.y.diag")) {
                System.out.println("[FARSEC] saved pos=" + chunk.getPos() + " min=" + min
                    + " sections=" + entries.size() + " bytes=" + Files.size(out));
                System.out.flush();
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("[FARSEC] save failed pos=" + chunk.getPos() + ": " + e);
            System.out.flush();
        }
    }

    public static void load(ServerLevel level, ChunkPos pos, ChunkAccess chunk) {
        if (!(chunk instanceof WindowedChunk windowed)
            || !Boolean.getBoolean("farlands.y.window")) {
            return;
        }
        int windowMin = windowed.farlands$windowMinSectionY();
        int windowMax = windowed.farlands$windowMaxSectionY();
        if (Math.abs((long) windowMin) < FAR_SECTION
            && Math.abs((long) windowMax) < FAR_SECTION) {
            return; // dimension-band chunk: no sidecar
        }
        Path in = file(level, pos);
        if (!Files.isRegularFile(in)) {
            return;
        }
        try (DataInputStream data = new DataInputStream(
            new GZIPInputStream(Files.newInputStream(in)))) {
            int magic = data.readInt();
            int version = data.readInt();
            if (magic != MAGIC || version != VERSION) {
                System.out.println("[FARSEC] ignoring sidecar pos=" + pos
                    + " magic=" + Integer.toHexString(magic) + " version=" + version);
                System.out.flush();
                return;
            }
            int min = data.readInt();
            int count = data.readInt();
            int restored = 0;
            for (int i = 0; i < count; i++) {
                int sy = data.readInt();
                int len = data.readInt();
                byte[] bytes = new byte[len];
                data.readFully(bytes);
                LevelChunkSection section = new LevelChunkSection(level.palettedContainerFactory());
                section.read(new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes)));
                if (Boolean.getBoolean("farlands.y.diag")) {
                    System.out.println("[FARSEC] load sy=" + sy
                        + " center=" + section.getBlockState(8, 8, 8)
                        + " air=" + section.hasOnlyAir());
                    if (sy == 4) {
                        System.out.println("[FARSEC] load s4(9,0,9)="
                            + section.getBlockState(9, 0, 9));
                    }
                }
                windowed.farlands$allSections().put(windowed.farlands$key(sy), section);
                restored++;
            }
            // Rebuild the window only when it is the dimension band or already the
            // sidecar's page. A detached ProtoChunk for prefetch/regenerate has a
            // different far target; moving it back to the old page would make the
            // fill skip every block (the filled=0 bug).
            int currentMin = windowed.farlands$windowMinSectionY();
            if (currentMin == min || Math.abs((long) currentMin) < FAR_SECTION) {
                windowed.farlands$moveWindowToStart(min);
            }
            if (Boolean.getBoolean("farlands.y.diag")) {
                System.out.println("[FARSEC] loaded pos=" + pos + " min=" + min
                    + " sections=" + restored + " band=" + windowed.farlands$windowMinSectionY()
                    + ".." + windowed.farlands$windowMaxSectionY());
                System.out.flush();
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("[FARSEC] load failed pos=" + pos + ": " + e);
            System.out.flush();
        }
    }
}
