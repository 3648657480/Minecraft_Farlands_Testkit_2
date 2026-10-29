package com.farlands.g2.runtime;

import com.scalarwide.mc.WideChunkPos;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-chunk vertical window override (Y-D groundwork).
 *
 * <p>Keyed by the wide chunk identity; the value is the desired window centre in
 * absolute section Y. When a chunk is (re)created its window is placed here
 * first, then the global {@code -Dfarlands.y.center} test property, then the
 * dimension band.</p>
 */
public final class YBandOverrides {

    private static final Map<WideChunkPos, Integer> OVERRIDES = new ConcurrentHashMap<>();

    private YBandOverrides() {
    }

    public static void set(WideChunkPos pos, int centerSectionY) {
        OVERRIDES.put(pos, centerSectionY);
    }

    public static Integer get(WideChunkPos pos) {
        return OVERRIDES.get(pos);
    }

    public static void clear(WideChunkPos pos) {
        OVERRIDES.remove(pos);
    }

    /**
     * Clears every override and forgets which world file was loaded, so the next
     * {@link #ensureLoaded} re-reads the new world's file. Called from
     * {@link G2Runtime#resetSession()} on server start/stop.
     */
    public static synchronized void clearAll() {
        OVERRIDES.clear();
        farlands$loadedPath = null;
    }

    /**
     * Persist the overrides next to the world so a moved band survives restarts
     * (without this, a chunk saved at a moved band loads at the default band and
     * its sections fall outside the window, leaving a hole).
     */
    public static void save(Path file) {
        StringBuilder sb = new StringBuilder();
        OVERRIDES.forEach((pos, center) -> sb.append(pos.xLong()).append(' ')
            .append(pos.zLong()).append(' ').append(center).append('\n'));
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("[YFOLLOW] band override save failed: " + e);
        }
    }

    private static Path farlands$loadedPath;

    /**
     * Loads the persisted overrides once per world, as early as the server knows its
     * world path. Chunks created before the first server tick would otherwise be placed
     * at the dimension band and their saved sections would fall outside the window.
     */
    public static synchronized void ensureLoaded(net.minecraft.server.MinecraftServer server) {
        Path path = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("farlands_bands.txt");
        if (path.equals(farlands$loadedPath)) {
            return;
        }
        farlands$loadedPath = path;
        OVERRIDES.clear();
        load(path);
    }

    public static void load(Path file) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                String[] parts = line.trim().split(" ");
                if (parts.length != 3) {
                    continue;
                }
                try {
                    OVERRIDES.put(WideChunkPos.of(Long.parseLong(parts[0]), Long.parseLong(parts[1])),
                        Integer.parseInt(parts[2]));
                } catch (NumberFormatException ignored) {
                    // skip malformed line
                }
            }
            System.out.println("[YFOLLOW] loaded " + OVERRIDES.size() + " band overrides");
            System.out.flush();
        } catch (IOException e) {
            System.out.println("[YFOLLOW] band override load failed: " + e);
        }
    }
}
