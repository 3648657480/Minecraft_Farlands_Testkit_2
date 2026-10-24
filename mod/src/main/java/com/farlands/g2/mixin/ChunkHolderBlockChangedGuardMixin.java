package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Y-E: {@code ChunkHolder.blockChanged} maps the block Y to a window-relative
 * section index. Fluids at the bottom edge of a moved window spread one block
 * below the window minimum, so the index becomes -1 and the server crashes in
 * {@code tickFluid} (EXPERIMENTS §87).
 *
 * <p>A block outside the chunk's own vertical bounds is not part of the window;
 * ignore its change instead of indexing the section array.</p>
 */
@Mixin(ChunkHolder.class)
public abstract class ChunkHolderBlockChangedGuardMixin {

    @Shadow public abstract LevelChunk getTickingChunk();

    @Inject(method = "blockChanged", at = @At("HEAD"), cancellable = true)
    private void farlands$guardBlockChanged(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        LevelChunk chunk = this.getTickingChunk();
        if (chunk != null && (pos.getY() < chunk.getMinY() || pos.getY() > chunk.getMaxY())) {
            cir.setReturnValue(false);
        }
    }
}
