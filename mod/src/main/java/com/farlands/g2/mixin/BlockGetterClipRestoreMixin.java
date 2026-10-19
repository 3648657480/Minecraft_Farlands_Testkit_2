package com.farlands.g2.mixin;

import com.farlands.g2.runtime.WideClipCoordinates;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockGetter.class)
public interface BlockGetterClipRestoreMixin {

    @Inject(method = "clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;",
        at = @At("HEAD"), cancellable = true)
    private void farlands$splitCrossSeam(ClipContext context, CallbackInfoReturnable<BlockHitResult> cir) {
        BlockHitResult split = WideClipCoordinates.clipCrossSeam((BlockGetter) this, context);
        if (split != null) {
            cir.setReturnValue(split);
        }
    }

    @Inject(method = "clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;",
        at = @At("RETURN"), cancellable = true)
    private void farlands$restoreClip(ClipContext context, CallbackInfoReturnable<BlockHitResult> cir) {
        cir.setReturnValue(WideClipCoordinates.restore(context, cir.getReturnValue()));
    }
}
