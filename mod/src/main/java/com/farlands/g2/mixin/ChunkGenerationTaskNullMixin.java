package com.farlands.g2.mixin;

import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Far cache windows may contain null holders; treat them as "not schedulable". */
@Mixin(ChunkGenerationTask.class)
public class ChunkGenerationTaskNullMixin {

    @Inject(method = "scheduleChunkInLayer", at = @At("HEAD"), cancellable = true)
    private void farlands$nullSafe(ChunkStatus status, boolean needsGeneration, GenerationChunkHolder holder,
        CallbackInfoReturnable<Boolean> cir) {
        if (holder == null) {
            cir.setReturnValue(false);
        }
    }
}
