package com.farlands.g2.mixin;

import com.farlands.g2.runtime.WindowedChunk;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Y-E: saved heightmaps are band-relative ({@code y - chunk.getMinY()}). Once a
 * chunk's window moves, the same NBT value is interpreted against a different
 * minY and every runtime heightmap shifts by the band delta (observed:
 * {@code topY=-50} instead of 62 after shifting the band by 7 sections).
 *
 * <p>Blocks are stored by absolute section Y and survive the shift; only the
 * heightmaps need re-deriving. On load, re-prime the runtime heightmaps from
 * the loaded blocks (WG heightmaps are generation-time products and are left
 * alone so fingerprints do not change).</p>
 */
@Mixin(SerializableChunkData.class)
public class SerializableChunkDataWindowMixin {

    @Inject(method = "read", at = @At("RETURN"))
    private void farlands$reprimeHeightmaps(ServerLevel level, PoiManager poiManager,
        RegionStorageInfo regionInfo, ChunkPos pos, CallbackInfoReturnable<ProtoChunk> cir) {
        if (!Boolean.getBoolean("farlands.y.window")) {
            return;
        }
        ProtoChunk chunk = cir.getReturnValue();
        if (!(chunk instanceof WindowedChunk)) {
            return;
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(
            Heightmap.Types.WORLD_SURFACE,
            Heightmap.Types.MOTION_BLOCKING,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Heightmap.Types.OCEAN_FLOOR));
    }
}
