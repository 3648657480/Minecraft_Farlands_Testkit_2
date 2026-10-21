package com.farlands.g2.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/** SHA-256 region filenames keyed by the wide region coordinate. */
@Mixin(RegionFileStorage.class)
public class RegionFileShaMixin {

    @Shadow @Final private Long2ObjectLinkedOpenHashMap<RegionFile> regionCache;
    @Shadow @Final private RegionStorageInfo info;
    @Shadow @Final private Path folder;
    @Shadow @Final private boolean sync;

    /**
     * @author G2
     * @reason Region identity must be the wide coordinate; the legacy int-based
     *         filename aliases every region beyond 2^31 chunks.
     */
    @Overwrite
    private RegionFile getRegionFile(ChunkPos pos) throws IOException {
        long rx = pos.xLong() >> 5;
        long rz = pos.zLong() >> 5;
        byte[] digest = digest(rx, rz);
        String name = "r." + hex(digest) + ".mca";
        long key = ByteBuffer.wrap(digest, 0, 8).getLong();
        RegionFile region = this.regionCache.getAndMoveToFirst(key);
        if (region != null) {
            return region;
        }
        if (this.regionCache.size() >= 256) {
            this.regionCache.removeLast().close();
        }
        net.minecraft.util.FileUtil.createDirectoriesSafe(this.folder);
        Path file = this.folder.resolve(name);
        RegionFile created = new RegionFile(this.info, file, this.folder, this.sync);
        this.regionCache.putAndMoveToFirst(key, created);
        return created;
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
