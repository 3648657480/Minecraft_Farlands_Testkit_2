package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Diagnostic: stack trace for the first high-Y write into the +16 seam column. */
@Mixin(ProtoChunk.class)
public class SetBlockStackDiagMixin {

    @Unique private static boolean farlands$printed;

    @Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;",
        at = @At("HEAD"))
    private void farlands$stack(BlockPos pos, BlockState state, int flags,
        CallbackInfoReturnable<BlockState> cir) {
        if (!Boolean.getBoolean("farlands.diag.setstack") || farlands$printed) {
            return;
        }
        var chunk = (net.minecraft.world.level.chunk.ProtoChunk) (Object) this;
        if (pos.getY() > 72 && (pos.getLongX() & 15L) == 0L && (pos.getLongZ() & 15L) == 5L
            && chunk.getPos().xLong() == 134217729L) {
            farlands$printed = true;
            new Exception("[SETSTACK] first high-Y write " + pos + " state=" + state).printStackTrace(System.out);
            System.out.flush();
        }
    }
}