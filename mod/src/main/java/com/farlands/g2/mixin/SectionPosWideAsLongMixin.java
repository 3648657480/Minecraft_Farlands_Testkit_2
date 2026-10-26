package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * W-2: {@code SectionPos.asLong(BlockPos)} read the int views of the position.
 * At far X the view wraps, so feature neighbour checks (BulkSectionAccess) looked
 * at the wrong section. Pack from the long fields instead; X/Z keep the vanilla
 * 29-bit layout, Y stays 6-bit (documented limit, wider keys are used elsewhere).
 */
@Mixin(SectionPos.class)
public class SectionPosWideAsLongMixin {

    @Inject(method = "asLong(Lnet/minecraft/core/BlockPos;)J", at = @At("HEAD"),
        cancellable = true)
    private static void farlands$wideAsLong(BlockPos pos, CallbackInfoReturnable<Long> cir) {
        long x = pos.getLongX();
        long y = pos.getLongY();
        long z = pos.getLongZ();
        if (x == (long) (int) x && y == (long) (int) y && z == (long) (int) z) {
            return; // exact int view: vanilla path
        }
        long sx = Math.floorDiv(x, 16L);
        long sy = Math.floorDiv(y, 16L);
        long sz = Math.floorDiv(z, 16L);
        long packed = ((sx & 536870911L) << 35) | (sy & 63L) | ((sz & 536870911L) << 6);
        cir.setReturnValue(packed);
    }
}
