package com.farlands.g2.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

/**
 * Reconstructs the wide block position of a network packet.
 *
 * <p>Packets carry {@code BlockPos} as three ints. At block >= 2^31 the int
 * view wraps, so the receiver only has the low 32 bits. The target block is
 * necessarily near the acting player, and the chunk pos is still a valid int
 * at the 2^31 seam, so the wide position is recovered from the player's chunk
 * and the low four bits. Wide {@code BlockPos} values (long fields) are left
 * untouched.</p>
 */
public final class WidePosRemap {

    private static final int SEARCH_CHUNKS = 2;

    private WidePosRemap() {
    }

    public static BlockPos remap(Player player, BlockPos pos) {
        if (player == null) {
            return pos;
        }
        if (pos.getLongX() != (long) pos.getX() || pos.getLongZ() != (long) pos.getZ()) {
            return pos;
        }
        if (Math.abs(pos.getX()) < 30_000_000 && Math.abs(pos.getZ()) < 30_000_000) {
            return pos;
        }

        ChunkPos center = player.chunkPosition();
        int lx = pos.getX() & 15;
        int lz = pos.getZ() & 15;
        for (int dx = -SEARCH_CHUNKS; dx <= SEARCH_CHUNKS; dx++) {
            for (int dz = -SEARCH_CHUNKS; dz <= SEARCH_CHUNKS; dz++) {
                long cx = (long) center.x() + dx;
                long cz = (long) center.z() + dz;
                long wx = (cx << 4) | lx;
                long wz = (cz << 4) | lz;
                if ((int) wx == pos.getX() && (int) wz == pos.getZ()) {
                    return new BlockPos(wx, (long) pos.getY(), wz);
                }
            }
        }
        return pos;
    }
}
