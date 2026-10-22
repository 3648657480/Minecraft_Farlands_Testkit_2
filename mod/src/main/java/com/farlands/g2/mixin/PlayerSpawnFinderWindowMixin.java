package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Y-B test harness: the initial-spawn search scans from the heightmap value down
 * to {@code level.getMinY()}. With a windowed chunk whose band is at Y≈2^31 the
 * heightmap reports ≈2^31, so vanilla scans ~2^31 blocks (observed as a CPU
 * spin in {@code PlayerSpawnFinder.getLevelRespawnPos}).
 *
 * <p>For windowed test worlds ({@code -Dfarlands.y.window=true} +
 * {@code -Dfarlands.y.center=<sectionY>}) the search is answered directly at the
 * window band. This is a test concession, not the final spawn semantics
 * (Y-D must bound the real search to the window).</p>
 */
@Mixin(PlayerSpawnFinder.class)
public class PlayerSpawnFinderWindowMixin {

    @Inject(method = "getSpawnPosInChunk", at = @At("HEAD"), cancellable = true)
    private static void farlands$windowSpawn(ServerLevel level, ChunkPos chunkPos,
        CallbackInfoReturnable<BlockPos> cir) {
        String center = System.getProperty("farlands.y.center");
        if (!Boolean.getBoolean("farlands.y.window") || center == null || center.isEmpty()) {
            return;
        }
        int sectionY = Integer.parseInt(center);
        cir.setReturnValue(new BlockPos(chunkPos.getMinBlockX() + 8, sectionY * 16, chunkPos.getMinBlockZ() + 8));
    }

    @Inject(method = "getLevelRespawnPos", at = @At("HEAD"), cancellable = true)
    private static void farlands$windowSpawnAt(ServerLevel level, int x, int z,
        CallbackInfoReturnable<BlockPos> cir) {
        String center = System.getProperty("farlands.y.center");
        if (!Boolean.getBoolean("farlands.y.window") || center == null || center.isEmpty()) {
            return;
        }
        int sectionY = Integer.parseInt(center);
        cir.setReturnValue(new BlockPos(x, sectionY * 16, z));
    }
}
