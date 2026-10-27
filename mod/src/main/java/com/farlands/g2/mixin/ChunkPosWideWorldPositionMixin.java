package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ChunkPos.getWorldPosition()/getMiddleBlockPosition() built BlockPos from the
 * int views, so feature placement started from the wrapped column at chunk
 * 2^27 and read the negative mirror chunk. Build them from the long fields.
 */
@Mixin(ChunkPos.class)
public abstract class ChunkPosWideWorldPositionMixin {

    @Shadow
    public abstract long xLong();

    @Shadow
    public abstract long zLong();

    @Inject(method = "getWorldPosition", at = @At("HEAD"), cancellable = true)
    private void farlands$worldPosition(CallbackInfoReturnable<BlockPos> cir) {
        cir.setReturnValue(new BlockPos(this.xLong() << 4, 0, this.zLong() << 4));
    }

    @Inject(method = "getMiddleBlockPosition", at = @At("HEAD"), cancellable = true)
    private void farlands$middlePosition(int y, CallbackInfoReturnable<BlockPos> cir) {
        cir.setReturnValue(new BlockPos((this.xLong() << 4) + 8L, y, (this.zLong() << 4) + 8L));
    }
}