package com.farlands.g2.mixin;

import com.farlands.g2.runtime.Narrowing;
import net.minecraft.world.level.LevelHeightAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * W-1 audit: callers that fold a far block Y into an int section index.
 * {@code getSectionIndex(int)} is a default method on LevelHeightAccessor.
 */
@Mixin(LevelHeightAccessor.class)
public interface LevelHeightSectionIndexAuditMixin {

    @Inject(method = "getSectionIndex", at = @At("HEAD"))
    private void farlands$auditSectionIndex(int blockY, CallbackInfoReturnable<Integer> cir) {
        if (Math.abs((long) blockY) > 1_000_000L) {
            Narrowing.warn("LevelHeightAccessor.getSectionIndex(int)");
        }
    }
}
