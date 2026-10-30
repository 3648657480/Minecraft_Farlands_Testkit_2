package com.farlands.g2.mixin;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import com.farlands.g2.runtime.RegionKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * SHA-256 region filenames keyed by the exact wide region coordinate.
 *
 * <p>The old implementation hashed on every chunk read/write and used the first
 * 8 digest bytes as the cache key, so it paid SHA for cache hits and could alias
 * two different regions on a 64-bit digest collision. This version keys the LRU
 * by an exact {@code (rx,rz)} pair and computes the digest only on a miss.
 * The filename scheme is unchanged, so existing region files keep working.</p>
 */
@Mixin(RegionFileStorage.class)
public class RegionFileShaMixin {

    @Shadow @Final private RegionStorageInfo info;
    @Shadow @Final private Path folder;
    @Shadow @Final private boolean sync;

    @Unique
    private final LinkedHashMap<RegionKey, RegionFile> farlands$regions =
        new LinkedHashMap<>(256, 0.75f, true);

    /**
     * @author G2
     * @reason Region identity must be the exact wide coordinate; the legacy
     *         int-based filename aliases every region beyond 2^31 chunks.
     */
    @Overwrite
    private RegionFile getRegionFile(ChunkPos pos) throws IOException {
        long rx = pos.xLong() >> 5;
        long rz = pos.zLong() >> 5;
        RegionKey key = new RegionKey(rx, rz);
        synchronized (this.farlands$regions) {
            RegionFile hit = this.farlands$regions.get(key);
            if (hit != null) {
                return hit;
            }
            if (this.farlands$regions.size() >= 256) {
                Iterator<Map.Entry<RegionKey, RegionFile>> it =
                    this.farlands$regions.entrySet().iterator();
                if (it.hasNext()) {
                    RegionFile eldest = it.next().getValue();
                    it.remove();
                    eldest.close();
                }
            }
            byte[] digest = digest(rx, rz);
            String name = "r." + hex(digest) + ".mca";
            net.minecraft.util.FileUtil.createDirectoriesSafe(this.folder);
            Path file = this.folder.resolve(name);
            RegionFile created = new RegionFile(this.info, file, this.folder, this.sync);
            this.farlands$regions.put(key, created);
            return created;
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void farlands$closeRegions(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        synchronized (this.farlands$regions) {
            for (RegionFile region : this.farlands$regions.values()) {
                try {
                    region.close();
                } catch (IOException e) {
                    System.out.println("[MCA] close failed: " + e);
                }
            }
            this.farlands$regions.clear();
        }
    }

    private static byte[] digest(long rx, long rz) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            ByteBuffer buf = ByteBuffer.allocate(16);
            buf.putLong(rx).putLong(rz);
            return md.digest(buf.array());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
