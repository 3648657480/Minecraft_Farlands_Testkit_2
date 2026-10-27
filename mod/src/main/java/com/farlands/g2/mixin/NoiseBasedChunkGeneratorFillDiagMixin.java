package com.farlands.g2.mixin;

import com.farlands.g2.runtime.FarFillDiag;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * Diagnostic (-Dfarlands.diag.filldiag): publish the seed-wide raw
 * {@code finalDensity()} tree so the fill probe can evaluate it with two
 * different FunctionContexts (NoiseChunk vs RealContext) at the same voxel.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorFillDiagMixin {

    @Unique
    private static final boolean FARLANDS$FILLDIAG = Boolean.getBoolean("farlands.diag.filldiag");

    @Inject(method = "fillFromNoise", at = @At("HEAD"))
    private void farlands$captureFinalDensity(Blender blender, RandomState randomState,
        StructureManager structureManager, ChunkAccess chunk,
        CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (FARLANDS$FILLDIAG) {
            FarFillDiag.setFinalDensity(randomState.router().finalDensity());
        }
    }
}
