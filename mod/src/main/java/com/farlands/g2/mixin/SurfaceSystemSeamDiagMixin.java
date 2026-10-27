package com.farlands.g2.mixin;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Diagnostic (-Dfarlands.diag.surfdiag): print the WORLD_SURFACE_WG height of
 * the seam chunk before the surface stage runs.
 */
@Mixin(SurfaceSystem.class)
public abstract class SurfaceSystemSeamDiagMixin {

    @Inject(method = "buildSurface", at = @At("HEAD"))
    private void farlands$surfDiag(RandomState randomState, BiomeManager biomeManager,
        boolean useLegacyRandom, WorldGenerationContext context, ChunkAccess chunk,
        NoiseChunk noiseChunk, SurfaceRules.RuleSource ruleSource, Set<?> set, CallbackInfo ci) {
        if (!Boolean.getBoolean("farlands.diag.surfdiag")) {
            return;
        }
        long cx = chunk.getPos().xLong();
        if (cx < 134217727L || cx > 134217730L || chunk.getPos().zLong() != 18L) {
            return;
        }
        System.out.println("[SURFDIAG] chunk=" + cx + "," + chunk.getPos().zLong()
            + " wsWG(0,5)=" + chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 0, 5)
            + " floorWG(0,5)=" + chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, 0, 5)
            + " wsWG(15,5)=" + chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 15, 5));
        System.out.flush();
    }
}
