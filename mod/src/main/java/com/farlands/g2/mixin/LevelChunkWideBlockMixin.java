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

    /** True when y is outside the chunk's current 24-section window (any band). */
    private static boolean farlands$outsideWindow(LevelChunk chunk, long y) {
        if (y < Integer.MIN_VALUE || y > Integer.MAX_VALUE) {
            return true;
        }
        int index = chunk.getSectionIndex((int) y);
        return index < 0 || index >= chunk.getSections().length;
    }

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void farlands$wideGet(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        if (WideBlockAccess.isWide(pos.getLongX(), pos.getLongY(), pos.getLongZ())
            || farlands$outsideWindow(chunk, pos.getLongY())) {
            cir.setReturnValue(WideBlockAccess.get(chunk,
                pos.getLongX(), pos.getLongY(), pos.getLongZ()));
        }
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void farlands$wideFluid(BlockPos pos,
        org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.level.material.FluidState> cir) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        if (WideBlockAccess.isWide(pos.getLongX(), pos.getLongY(), pos.getLongZ())
            || farlands$outsideWindow(chunk, pos.getLongY())) {
            cir.setReturnValue(WideBlockAccess.getFluid(chunk,
                pos.getLongX(), pos.getLongY(), pos.getLongZ()));
        }
    }

    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void farlands$wideSet(BlockPos pos, BlockState state, int flags,
        CallbackInfoReturnable<BlockState> cir) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        if (WideBlockAccess.isWide(pos.getLongX(), pos.getLongY(), pos.getLongZ())
            || farlands$outsideWindow(chunk, pos.getLongY())) {
            cir.setReturnValue(WideBlockAccess.set(chunk,
                pos.getLongX(), pos.getLongY(), pos.getLongZ(), state));
        }
    }
}
