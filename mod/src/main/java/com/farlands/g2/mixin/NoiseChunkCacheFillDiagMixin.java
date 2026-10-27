package com.farlands.g2.mixin;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Diagnostic (-Dfarlands.diag.cachefill): at the seam chunk, log the context
 * with which {@code fillAllDirectly} evaluates a cell cache (the caller's
 * density function class and the cell start coordinates). Read-only.
 */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkCacheFillDiagMixin {

    @Shadow private int cellStartBlockX;
    @Shadow private int cellStartBlockY;
    @Shadow private int cellStartBlockZ;
    @Shadow private long farlands$originX;
    @Shadow private long farlands$cellStartBlockXLong;
    @Shadow private long farlands$cellStartBlockZLong;

    @Unique
    private static final AtomicInteger FARLANDS$CFILL =
        Boolean.getBoolean("farlands.diag.cachefill") ? new AtomicInteger() : null;

    @Inject(method = "fillAllDirectly", at = @At("HEAD"))
    private void farlands$logCacheFill(double[] array, DensityFunction densityFunction, CallbackInfo ci) {
        if (FARLANDS$CFILL == null || this.farlands$originX != 2147483664L) {
            return;
        }
        if (FARLANDS$CFILL.incrementAndGet() > 12) {
            return;
        }
        System.out.println("[CACHEFILL] densityClass=" + densityFunction.getClass().getName()
            + " cellStartX=" + this.cellStartBlockX + "/" + this.farlands$cellStartBlockXLong
            + " cellStartY=" + this.cellStartBlockY
            + " cellStartZ=" + this.cellStartBlockZ + "/" + this.farlands$cellStartBlockZLong
            + " len=" + array.length);
        System.out.flush();
    }
}
