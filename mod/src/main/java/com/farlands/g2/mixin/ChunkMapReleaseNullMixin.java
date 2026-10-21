package com.farlands.g2.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** releaseGeneration must tolerate a missing holder in the far cache window. */
@Mixin(ChunkMap.class)
public class ChunkMapReleaseNullMixin {

    @Inject(method = "releaseGeneration", at = @At("HEAD"), cancellable = true)
    private void farlands$nullSafe(GenerationChunkHolder chunkHolder, CallbackInfo ci) {
        if (chunkHolder == null) {
            ci.cancel();
        }
    }
}
