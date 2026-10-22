package com.farlands.g2.mixin;

import com.farlands.g2.runtime.StageSteps;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records every chunk step the vanilla pipeline executes (see {@link StageSteps}). */
@Mixin(ChunkStep.class)
public class ChunkStepCaptureMixin {

    @Inject(method = "apply", at = @At("HEAD"))
    private void farlands$capture(WorldGenContext context, StaticCache2D<GenerationChunkHolder> cache,
        ChunkAccess chunk, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        StageSteps.put((ChunkStep) (Object) this);
    }
}
