package com.farlands.g2.mixin;

import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Diagnostic (-Dfarlands.diag.flatdiag): print the FlatCache value returned for
 * the seam chunk columns, with the caller's real coordinates.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$FlatCache")
public abstract class NoiseChunkFlatCacheDiagMixin {

    @Unique
    private static final AtomicInteger FARLANDS$FLAT =
        Boolean.getBoolean("farlands.diag.flatdiag") ? new AtomicInteger() : null;

    @Inject(method = "compute", at = @At("RETURN"))
    private void farlands$logFlat(DensityFunction.FunctionContext context,
        CallbackInfoReturnable<Double> cir) {
        if (FARLANDS$FLAT == null) {
            return;
        }
        double x = context instanceof com.farlands.g2.runtime.WideContext w
            ? w.getBlockXDouble() : context.blockX();
        double z = context instanceof com.farlands.g2.runtime.WideContext w
            ? w.getBlockZDouble() : context.blockZ();
        double y = context instanceof com.farlands.g2.runtime.WideContext w
            ? w.getBlockYDouble() : context.blockY();
        if (x < 2147483648.0 || x > 2147483696.0 || z < 288.0 || z > 304.0) {
            return;
        }
        if (FARLANDS$FLAT.incrementAndGet() > 100) {
            return;
        }
        System.out.println("[FLATDIAG] x=" + x + " y=" + y
            + " z=" + z + " -> " + cir.getReturnValue());
        System.out.flush();
    }
}
