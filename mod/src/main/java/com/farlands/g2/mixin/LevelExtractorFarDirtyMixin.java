package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client render invalidation for far X.
 *
 * <p>{@code LevelExtractor.setBlockDirty} walks {@code pos.getX() - 1 ..
 * pos.getX() + 1} with ints. At block 2^31 the wrapped value makes
 * {@code start > end}, so the loop body never runs and no section is marked
 * dirty: a broken/placed block stays visually unchanged and no particles or
 * block-break overlay appear. Mark the 3x3x3 sections from the wide
 * coordinates instead.</p>
 */
@Mixin(targets = "net.minecraft.client.renderer.extract.LevelExtractor")
public abstract class LevelExtractorFarDirtyMixin {

    @Shadow
    private void setSectionDirty(int sectionX, int sectionY, int sectionZ, boolean playerChanged) {
        throw new AbstractMethodError();
    }

    @Inject(method = "setBlockDirty", at = @At("HEAD"), cancellable = true)
    private void farlands$wideDirty(BlockPos pos, boolean playerChanged, CallbackInfo ci) {
        long x = pos.getLongX();
        long z = pos.getLongZ();
        if (x == (long) (int) x && z == (long) (int) z) {
            return; // normal coordinates: vanilla
        }
        int sx = (int) (x >> 4);
        int sy = pos.getY() >> 4;
        int sz = (int) (z >> 4);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    this.setSectionDirty(sx + dx, sy + dy, sz + dz, playerChanged);
                }
            }
        }
        ci.cancel();
    }
}