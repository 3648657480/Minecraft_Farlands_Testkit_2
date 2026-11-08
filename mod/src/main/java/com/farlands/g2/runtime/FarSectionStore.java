package com.farlands.g2.runtime;

import com.scalarwide.mc.WideChunkPos;
import com.scalarwide.mc.WideSectionKey2560;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
 * Section sidecar storage.
 *
 * <p>v2 is the 2560-bit identity format: each section is keyed by its absolute
 * {@link WideSectionKey2560} (X/Y/Z BigInteger), and the chunk file is named by
 * a hash of the wide ChunkPos so block 2^1016 does not exceed path limits. The
 * section window is still an int view; that view remains the phenomenon layer
 * (it is not widened here).</p>
 *
 * <p>v1 (int section Y + long chunk file name) is still readable so existing
 * fixtures keep working. New writes are always v2.</p>
 */
public final class FarSectionStore {

    private static final int MAGIC = 0x47325331; // "G2S1" (legacy)
    private static final int MAGIC2 = 0x47325332; // "G2S2" (2560 section keys)
    private static final int VERSION = 1;
    private static final int VERSION2 = 2;
    /** Only chunks whose window is outside the dimension band need a sidecar. */
    private static final long FAR_SECTION = 1_000_000L;
    private static final int MAX_BIGINT_BYTES = 512;

    private FarSectionStore() {
    }

    private static Path dir(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("farlands_sections");
    }

    /** v2: content-addressed by wide coordinates; safe at any section magnitude. */
    private static Path file(ServerLevel level, WideChunkPos pos) {
        return dir(level).resolve(sha256(pos.x().toString() + ":" + pos.z().toString())
            + ".g2s");
    }

    /** v1 legacy path: only when the long views exist. */
    private static Path legacyFile(ServerLevel level, ChunkPos pos) {
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
        WideChunkPos wide = chunk.getPos().widePos();
        Path out = file(level, wide);
        try {
            Files.createDirectories(out.getParent());
            try (DataOutputStream data = new DataOutputStream(
                new GZIPOutputStream(Files.newOutputStream(out)))) {
                data.writeInt(MAGIC2);
                data.writeInt(VERSION2);
                writeBigInteger(data, wide.x().toBigInteger());
                writeBigInteger(data, wide.z().toBigInteger());
                data.writeInt(min);
                data.writeInt(entries.size());
                for (Map.Entry<WideSectionKey2560, LevelChunkSection> entry : entries) {
                    WideSectionKey2560 key = entry.getKey();
                    writeBigInteger(data, key.x().toBigInteger());
                    writeBigInteger(data, key.y().toBigInteger());
                    writeBigInteger(data, key.z().toBigInteger());
                    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                    entry.getValue().write(buffer);
                    byte[] bytes = ByteBufUtil.getBytes(buffer);
                    data.writeInt(bytes.length);
                    data.write(bytes);
                }
            }
            if (Boolean.getBoolean("farlands.y.diag")) {
                System.out.println("[FARSEC] v2 saved pos=" + wide + " min=" + min
                    + " sections=" + entries.size() + " bytes=" + Files.size(out));
                System.out.flush();
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("[FARSEC] save failed pos=" + wide + ": " + e);
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
        WideChunkPos wide = pos.widePos();
        Path in = file(level, wide);
        boolean legacy = false;
        if (!Files.isRegularFile(in)) {
            Path old = legacyFile(level, pos);
            if (!Files.isRegularFile(old)) {
                return;
            }
            in = old;
            legacy = true;
        }
        try (DataInputStream data = new DataInputStream(
            new GZIPInputStream(Files.newInputStream(in)))) {
            int magic = data.readInt();
            int version = data.readInt();
            if (magic == MAGIC2 && version == VERSION2) {
                loadV2(level, windowed, wide, data);
            } else if (magic == MAGIC && version == VERSION) {
                loadV1(level, windowed, pos, data);
            } else {
                System.out.println("[FARSEC] ignoring sidecar pos=" + wide
                    + " magic=" + Integer.toHexString(magic) + " version=" + version
                    + " legacy=" + legacy);
                System.out.flush();
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("[FARSEC] load failed pos=" + wide + ": " + e);
            System.out.flush();
        }
    }

    private static void loadV2(ServerLevel level, WindowedChunk windowed, WideChunkPos expected,
        DataInputStream data) throws IOException {
        BigInteger chunkX = readBigInteger(data);
        BigInteger chunkZ = readBigInteger(data);
        int min = data.readInt();
        int count = data.readInt();
        boolean coordsMatch = expected.x().equals(chunkX) && expected.z().equals(chunkZ);
        int restored = 0;
        for (int i = 0; i < count; i++) {
            BigInteger x = readBigInteger(data);
            BigInteger y = readBigInteger(data);
            BigInteger z = readBigInteger(data);
            int len = data.readInt();
            byte[] bytes = new byte[len];
            data.readFully(bytes);
            LevelChunkSection section = new LevelChunkSection(level.palettedContainerFactory());
            section.read(new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes)));
            windowed.farlands$putSection(y.longValueExact(), section);
            restored++;
        }
        int currentMin = windowed.farlands$windowMinSectionY();
        if (currentMin == min || Math.abs((long) currentMin) < FAR_SECTION) {
            windowed.farlands$moveWindowToStart(min);
        }
        if (Boolean.getBoolean("farlands.y.diag")) {
            System.out.println("[FARSEC] v2 loaded pos=" + expected + " coordsMatch="
                + coordsMatch + " min=" + min + " sections=" + restored + " band="
                + windowed.farlands$windowMinSectionY() + ".."
                + windowed.farlands$windowMaxSectionY());
            System.out.flush();
        }
    }

    private static void loadV1(ServerLevel level, WindowedChunk windowed, ChunkPos pos,
        DataInputStream data) throws IOException {
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
            windowed.farlands$putSection(sy, section);
            restored++;
        }
        int currentMin = windowed.farlands$windowMinSectionY();
        if (currentMin == min || Math.abs((long) currentMin) < FAR_SECTION) {
            windowed.farlands$moveWindowToStart(min);
        }
        if (Boolean.getBoolean("farlands.y.diag")) {
            System.out.println("[FARSEC] v1 loaded pos=" + pos + " min=" + min
                + " sections=" + restored + " band=" + windowed.farlands$windowMinSectionY()
                + ".." + windowed.farlands$windowMaxSectionY());
            System.out.flush();
        }
    }

    private static void writeBigInteger(DataOutputStream out, BigInteger value)
        throws IOException {
        byte[] bytes = value.toByteArray();
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static BigInteger readBigInteger(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len <= 0 || len > MAX_BIGINT_BYTES) {
            throw new IOException("invalid BigInteger length " + len);
        }
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new BigInteger(bytes);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

