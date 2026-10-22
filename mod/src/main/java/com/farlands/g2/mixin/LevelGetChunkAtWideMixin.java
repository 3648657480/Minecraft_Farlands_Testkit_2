package com.farlands.g2.mixin;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
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
 *
 * <p>Far-domain server lookups (block 2^35 / chunk 2^31) must not go through
 * {@code getChunk(int,int)}: {@code blockToSectionCoord(long)} truncates the
 * section to int, so +2^31 becomes -2^31 and the lookup requests the negative
 * alias. During post-processing this created a "ghost" holder whose light step
 * failed ({@code startedWork=null}). When the BlockPos carries true wide
 * coordinates and we are on a ServerLevel, resolve the wide ChunkPos through
 * {@code ServerChunkCache.farlands$getChunkWide}; the int-only (packet) path
 * keeps the player-nearest recovery below.</p>
 */
@Mixin(Level.class)
public class LevelGetChunkAtWideMixin {

    @Unique private static final boolean farlands$traceEnabled = Boolean.getBoolean("farlands.trace.seam");
    @Unique private static final boolean farlands$chunkatDiag = Boolean.getBoolean("farlands.diag.chunkat");
    @Unique private static int farlands$chunkatLogged;

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
        long trueSectionX = pos.getLongX() >> 4;
        long trueSectionZ = pos.getLongZ() >> 4;
        int sx = SectionPos.blockToSectionCoord(pos.getLongX());
        int sz = SectionPos.blockToSectionCoord(pos.getLongZ());
        if (farlands$chunkatDiag) {
            if ((trueSectionX != (long) sx || trueSectionZ != (long) sz) && farlands$chunkatLogged < 8) {
                farlands$chunkatLogged++;
                System.out.println("[CHUNKAT] block=" + pos.getLongX() + "," + pos.getLongZ()
                    + " trueSection=" + trueSectionX + "," + trueSectionZ
                    + " intSection=" + sx + "," + sz
                    + " intView=" + pos.getX() + "," + pos.getZ());
                new Throwable("chunkat overflow").printStackTrace(System.out);
                System.out.flush();
            }
        }
        if (Math.abs((long) sx) < 134_217_000L && Math.abs((long) sz) < 134_217_000L) {
            cir.setReturnValue(self.getChunk(sx, sz));
            return;
        }
        // True-wide BlockPos on the server: resolve the real chunk identity.
        // getChunk(int,int) can only carry the truncated int view, which at
        // chunk 2^31 aliases to the negative band and builds a ghost holder.
        if (self instanceof ServerLevel server
            && (pos.getLongX() != (long) pos.getX() || pos.getLongZ() != (long) pos.getZ())) {
            LevelChunk wideChunk = server.getChunkSource()
                .farlands$getChunkWide(new ChunkPos(trueSectionX, trueSectionZ));
            if (wideChunk != null) {
                cir.setReturnValue(wideChunk);
                return;
            }
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
