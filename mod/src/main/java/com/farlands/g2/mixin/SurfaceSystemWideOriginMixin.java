package com.farlands.g2.mixin;

import com.farlands.g2.runtime.SurfaceWide;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Function;

/**
 * W-2c: surface rules receive the column position through int views. Publish the
 * exact long column for the duration of the call so the noise conditions can
 * recover the real coordinate; the scope is one method call.
 */
@Mixin(SurfaceSystem.class)
public class SurfaceSystemWideOriginMixin {

    @Inject(method = "topMaterial", at = @At("HEAD"))
    private void farlands$begin(SurfaceRules.RuleSource ruleSource, CarvingContext context,
        Function<BlockPos, Holder<Biome>> biomeGetter, ChunkAccess chunk, NoiseChunk noiseChunk,
        BlockPos pos, boolean underFluid, CallbackInfoReturnable<Optional<BlockState>> cir) {
        SurfaceWide.begin(pos.getLongX(), pos.getX(), pos.getLongZ(), pos.getZ());
    }

    @Inject(method = "topMaterial", at = @At("RETURN"))
    private void farlands$end(SurfaceRules.RuleSource ruleSource, CarvingContext context,
        Function<BlockPos, Holder<Biome>> biomeGetter, ChunkAccess chunk, NoiseChunk noiseChunk,
        BlockPos pos, boolean underFluid, CallbackInfoReturnable<Optional<BlockState>> cir) {
        SurfaceWide.end();
    }
}
