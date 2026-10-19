package com.farlands.g2.mixin;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * W5/M3: Level.getChunkAt(BlockPos) resolves the chunk with the int block
 * view; at block >= 2^31 the view wraps, so setBlock/getBlockState on the
 * seam chunk fails (blocks cannot be broken/placed on the far side). Resolve
 * from the wide block coordinates; when the BlockPos only carries the wrapped
 * int value (packets, raycast mutable positions), recover the section nearest
 * to the acting player so east of the seam maps to the positive chunk the
 * server actually sends.
 */
@Mixin(Level.class)
public class LevelGetChunkAtWideMixin {

    @Unique private static final boolean farlands$traceEnabled = Boolean.getBoolean("farlands.trace.seam");

    @Unique
    private static long farlands$circular(long d) {
        d &= 0x0FFFFFFFL;
        if (d >= 0x08000000L) {
            d -= 0x10000000L;
        }
        return d;
    }

    @Inject(method = "getChunkAt", at = @At("HEAD"), cancellable = true)
    private void farlands$wide(BlockPos pos, CallbackInfoReturnable<LevelChunk> cir) {
        Level self = (Level) (Object) this;
        int sx = SectionPos.blockToSectionCoord(pos.getLongX());
        int sz = SectionPos.blockToSectionCoord(pos.getLongZ());
        if (Math.abs((long) sx) < 134_217_000L && Math.abs((long) sz) < 134_217_000L) {
            cir.setReturnValue(self.getChunk(sx, sz));
            return;
        }
        int rx = sx;
        int rz = sz;

        List<? extends Player> players = self.players();
        if (players != null && !players.isEmpty()) {
            Player best = null;
            long bestDist = Long.MAX_VALUE;
            for (Player p : players) {
                ChunkPos cp = p.chunkPosition();
                long dx = farlands$circular((long) sx - cp.x());
                long dz = farlands$circular((long) sz - cp.z());
                long d = Math.abs(dx) + Math.abs(dz);
                if (d < bestDist) {
                    bestDist = d;
                    best = p;
                }
            }
            if (best != null) {
                ChunkPos cp = best.chunkPosition();
                long px = cp.x();
                long pz = cp.z();
                long cx = sx;
                long cz = sz;
                if (Math.abs(cx - px) > (1L << 27)) {
                    cx += cx < px ? (1L << 28) : -(1L << 28);
                }
                if (Math.abs(cz - pz) > (1L << 27)) {
                    cz += cz < pz ? (1L << 28) : -(1L << 28);
                }
                if (Math.abs(cx - px) <= 32 && Math.abs(cz - pz) <= 32) {
                    rx = (int) cx;
                    rz = (int) cz;
                }
            }
        }

        LevelChunk chunk = self.getChunk(rx, rz);
        if (farlands$traceEnabled && Math.abs(rx - 134_217_728) <= 1) {
            long now = System.currentTimeMillis();
            if (now - farlands$lastChunkLog >= 500L) {
                farlands$lastChunkLog = now;
                System.out.println("[CHUNK] getChunkAt intX=" + pos.getX()
                    + " longX=" + pos.getLongX() + " sx=" + sx + " rx=" + rx
                    + " -> " + (chunk == null ? "null"
                        : (chunk.getPos() + " empty=" + chunk.isEmpty())));
                System.out.flush();
            }
        }
        cir.setReturnValue(chunk);
    }

    @Unique private static long farlands$lastChunkLog;
}
