package com.farlands.g2.mixin;

import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Diagnostic: dump the +16 seam column right before surface rules run. */
@Mixin(SurfaceSystem.class)
public class SurfaceBeforeDiagMixin {

    @Inject(method = "buildSurface", at = @At("HEAD"))
    private void farlands$dump(RandomState randomState, BiomeManager biomeManager, boolean useLegacyRandom,
        WorldGenerationContext generationContext, net.minecraft.world.level.chunk.ChunkAccess protoChunk,
        NoiseChunk noiseChunk, SurfaceRules.RuleSource ruleSource, java.util.Set<Holder<Biome>> possibleBiomes,
        CallbackInfo ci) {
        if (!Boolean.getBoolean("farlands.diag.surfbefore")) {
            return;
        }
        if (protoChunk.getPos().xLong() != 134217729L || protoChunk.getPos().zLong() != 18L) {
            return;
        }
        int height = protoChunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 0, 5);
        StringBuilder sb = new StringBuilder("[SURFBEFORE] height=" + height + " blocks=");
        int y = -64;
        while (y <= 130) {
            var st = protoChunk.getBlockState(new net.minecraft.core.BlockPos(
                protoChunk.getPos().getMinBlockXLong() + 0,
                y,
                protoChunk.getPos().getMinBlockZLong() + 5));
            if (!st.isAir()) {
                int start = y;
                while (y + 1 <= 130) {
                    var n = protoChunk.getBlockState(new net.minecraft.core.BlockPos(
                        protoChunk.getPos().getMinBlockXLong(),
                        y + 1,
                        protoChunk.getPos().getMinBlockZLong() + 5));
                    if (n.isAir()) {
                        break;
                    }
                    y++;
                }
                sb.append(start).append("..").append(y).append(":").append(st.getBlock().getDescriptionId()).append(" ");
            }
            y++;
        }
        System.out.println(sb);
        System.out.flush();
    }
}