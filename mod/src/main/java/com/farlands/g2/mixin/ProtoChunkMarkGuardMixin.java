package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Y-E: with a moved vertical window, features/heightmaps can still place blocks at
 * dimension heights (e.g. y=64) inside a chunk whose section array starts at the
 * window minimum. {@code ChunkAccess.getOrCreateOffsetList} then computes a huge
 * negative section index and throws.
 *
 * <p>Positions outside the chunk's own vertical bounds cannot belong to the window,
 * so their post-processing entry is meaningless: ignore it instead of crashing.</p>
 */
@Mixin(ProtoChunk.class)
public class ProtoChunkMarkGuardMixin {

    @Inject(method = "markPosForPostProcessing", at = @At("HEAD"), cancellable = true)
    private void farlands$guardMark(BlockPos pos, CallbackInfo ci) {
        ChunkAccess chunk = (ChunkAccess) (Object) this;
        if (pos.getY() < chunk.getMinY() || pos.getY() > chunk.getMaxY()) {
            ci.cancel();
        }
    }
}
