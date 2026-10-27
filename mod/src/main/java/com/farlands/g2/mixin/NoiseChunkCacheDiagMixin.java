package com.farlands.g2.mixin;

import com.farlands.g2.runtime.NoiseChunkDiagAccess;
import com.farlands.g2.runtime.WideContext;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Diagnostic (-Dfarlands.diag.cachediag): at the seam probe voxel log the
 * {@code CacheAllInCell} value together with the cache index it selected and
 * the fill state (inCell / cellStart / cell size). Tells whether the cache
 * returned the wrong array slot (index bug) or a wrong stored value (fill-time
 * evaluation bug).
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$CacheAllInCell")
public abstract class NoiseChunkCacheDiagMixin {

    @Shadow private NoiseChunk this$0;

    @Unique
    private static final boolean FARLANDS$CACHEDIAG = Boolean.getBoolean("farlands.diag.cachediag");

    @Unique
    private static final boolean[] FARLANDS$DONE = new boolean[256];

    @Inject(method = "compute", at = @At("RETURN"))
    private void farlands$logCache(DensityFunction.FunctionContext context,
        CallbackInfoReturnable<Double> cir) {
        if (!FARLANDS$CACHEDIAG || !(context instanceof WideContext wide)) {
            return;
        }
        double x = wide.getBlockXDouble();
        double y = wide.getBlockYDouble();
        double z = wide.getBlockZDouble();
        if (Math.abs(x - 2147483664.0) > 0.5 || z != 293.0 || y < 65.0 || y > 125.0) {
            return;
        }
        int yi = (int) y;
        if (FARLANDS$DONE[yi]) {
            return;
        }
        FARLANDS$DONE[yi] = true;
        NoiseChunkDiagAccess acc = (NoiseChunkDiagAccess) (Object) this.this$0;
        int w = acc.farlands$cellWidth();
        int h = acc.farlands$cellHeight();
        int index = (h - 1 - acc.farlands$inCellY()) * w * w
            + acc.farlands$inCellX() * w + acc.farlands$inCellZ();
        System.out.println("[CACHEDIAG] y=" + yi + " value=" + cir.getReturnValue()
            + " index=" + index + " inCell=" + acc.farlands$inCellX() + ","
            + acc.farlands$inCellY() + "," + acc.farlands$inCellZ()
            + " cellStartY=" + acc.farlands$cellStartBlockY()
            + " cellWidth=" + w + " cellHeight=" + h
            + " cellStartXLong=" + acc.farlands$cellStartBlockXLong()
            + " originX=" + acc.farlands$originX());
        System.out.flush();
    }
}
