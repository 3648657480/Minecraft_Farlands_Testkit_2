package com.farlands.g2.mixin;

import com.farlands.g2.runtime.Narrowing;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** W-1 audit: callers that pack wide X/Z/Y into the lossy 29/6/29 section key. */
@Mixin(SectionPos.class)
public class SectionPosAsLongAuditMixin {

    @Inject(method = "asLong(III)J", at = @At("HEAD"))
    private static void farlands$auditAsLong(int x, int y, int z,
        CallbackInfoReturnable<Long> cir) {
        if (y < -32 || y > 31 || Math.abs((long) x) > 268_435_455L
            || Math.abs((long) z) > 268_435_455L) {
            Narrowing.warn("SectionPos.asLong(III)");
        }
    }
}
