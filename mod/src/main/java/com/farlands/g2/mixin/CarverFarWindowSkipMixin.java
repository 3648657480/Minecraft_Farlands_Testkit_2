package com.farlands.g2.mixin;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carving masks are indexed by {@code y - minY} within the dimension band; a
 * chunk whose window is parked at a far Y has no representable index
 * (BitSet throws "bitIndex &lt; 0"). Skip carvers for such windows: far-Y
 * terrain generates without caves until the mask is window-relative.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public class CarverFarWindowSkipMixin {

    @Inject(method = "applyCarvers", at = @At("HEAD"), cancellable = true)
    private void farlands$skipFarWindow(WorldGenRegion region, long seed, RandomState randomState,
        BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk,
        CallbackInfo ci) {
        if (chunk.getMinY() != chunk.getHeightAccessorForGeneration().getMinY()) {
            ci.cancel();
        }
    }
}