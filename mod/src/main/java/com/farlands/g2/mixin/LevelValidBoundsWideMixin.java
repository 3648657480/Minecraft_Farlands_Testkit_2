package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the vertical build-height check but drops the 30M horizontal ceiling
 * in {@code Level.isInValidBounds}. {@code Level.setBlock} rejects every write
 * outside that ceiling, which made far-domain interaction silently fail.
 */
@Mixin(Level.class)
public class LevelValidBoundsWideMixin {

    @Inject(method = "isInValidBounds(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void farlands$wideBounds(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Level self = (Level) (Object) this;
        cir.setReturnValue(self.isInsideBuildHeight(pos));
    }
}
