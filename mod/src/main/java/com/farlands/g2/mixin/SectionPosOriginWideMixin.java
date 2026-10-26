package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** W-2c: section origin keeps the long block coordinates (int views still wrap). */
@Mixin(SectionPos.class)
public class SectionPosOriginWideMixin {

    @Inject(method = "origin", at = @At("HEAD"), cancellable = true)
    private void farlands$wideOrigin(CallbackInfoReturnable<BlockPos> cir) {
        SectionPos self = (SectionPos) (Object) this;
        long x = (long) self.x() << 4;
        long y = (long) self.y() << 4;
        long z = (long) self.z() << 4;
        cir.setReturnValue(new BlockPos(x, y, z));
    }
}