package com.farlands.g2.mixin;

import com.farlands.g2.runtime.WideBlockAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** W-2: server/client block access through the int BlockPos view at far X. */
@Mixin(LevelChunk.class)
public class LevelChunkWideBlockMixin {

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void farlands$wideGet(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (WideBlockAccess.isWide(pos.getLongX(), pos.getLongY(), pos.getLongZ())) {
            cir.setReturnValue(WideBlockAccess.get((LevelChunk) (Object) this,
                pos.getLongX(), pos.getLongY(), pos.getLongZ()));
        }
    }

    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void farlands$wideSet(BlockPos pos, BlockState state, int flags,
        CallbackInfoReturnable<BlockState> cir) {
        if (WideBlockAccess.isWide(pos.getLongX(), pos.getLongY(), pos.getLongZ())) {
            cir.setReturnValue(WideBlockAccess.set((LevelChunk) (Object) this,
                pos.getLongX(), pos.getLongY(), pos.getLongZ(), state));
        }
    }
}
