package com.farlands.g2.mixin;

import com.farlands.g2.runtime.FarFillDiag;
import com.farlands.g2.runtime.NoiseChunkDiagAccess;
import com.farlands.g2.runtime.RealContext;
import com.farlands.g2.runtime.WideContext;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Diagnostic (-Dfarlands.diag.corner): the slice filling context provider
 * ({@code NoiseChunk$1}) evaluates each interpolator slice corner. For the seam
 * chunk, log every corner with the coordinates actually used
 * ({@code cellStartLong + inCell} and {@code getBlockX/ZDouble}), the value
 * written, and the raw {@code finalDensity} at the same point (RealContext,
 * read-only). This path never calls an interpolator, so it cannot perturb it.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$1")
public abstract class NoiseChunkSliceProviderDiagMixin {

    @Shadow @Final private NoiseChunk this$0;

    @Unique
    private static final boolean FARLANDS$CORNER = Boolean.getBoolean("farlands.diag.corner");

    @Unique
    private static final AtomicInteger FARLANDS$N = new AtomicInteger();

    @Unique
    private static final java.util.Set<String> FARLANDS$SEEN =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    @Redirect(method = "fillAllDirectly",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/DensityFunction;compute"
                + "(Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;)D"))
    private double farlands$sliceCorner(DensityFunction filler,
        DensityFunction.FunctionContext context) {
        double value = filler.compute(context);
        if (!FARLANDS$CORNER) {
            return value;
        }
        NoiseChunkDiagAccess acc = (NoiseChunkDiagAccess) (Object) this.this$0;
        if (acc.farlands$originX() != 2147483664L) {
            return value;
        }
        long x = acc.farlands$cellStartBlockXLong() + acc.farlands$inCellX();
        long z = acc.farlands$cellStartBlockZLong() + acc.farlands$inCellZ();
        int y = acc.farlands$cellStartBlockY() + acc.farlands$inCellY();
        if (x < 2147483664L || x > 2147483680L || z < 288L || z > 296L
            || y < 64 || y > 128) {
            return value;
        }
        String fillerName = filler.getClass().getSimpleName();
        if (fillerName.equals("YClampedGradient") || fillerName.equals("Constant")) {
            return value;
        }
        String key = fillerName + "@" + x + "," + y + "," + z;
        if (!FARLANDS$SEEN.add(key)) {
            return value;
        }
        if (FARLANDS$N.incrementAndGet() > 150) {
            return value;
        }
        double valueReal = filler.compute(new RealContext(x, y, z));
        double raw = Double.NaN;
        DensityFunction finalDensity = FarFillDiag.finalDensity();
        if (finalDensity != null) {
            raw = finalDensity.compute(new RealContext(x, y, z));
        }
        WideContext wide = (WideContext) (Object) this.this$0;
        System.out.println("[CORNER] filler=" + filler.getClass().getSimpleName()
            + " x=" + x + " y=" + y + " z=" + z
            + " valueChunkCtx=" + value + " valueRealCtx=" + valueReal
            + " diff=" + (value - valueReal) + " rawFinal=" + raw
            + " inCell=" + acc.farlands$inCellX() + "," + acc.farlands$inCellY()
            + "," + acc.farlands$inCellZ()
            + " cellStartXint=" + acc.farlands$cellStartBlockX()
            + " cellStartXLong=" + acc.farlands$cellStartBlockXLong()
            + " cellStartZint=" + acc.farlands$cellStartBlockZ()
            + " cellStartZLong=" + acc.farlands$cellStartBlockZLong()
            + " getBlockXDouble=" + wide.getBlockXDouble()
            + " getBlockZDouble=" + wide.getBlockZDouble());
        System.out.flush();
        return value;
    }
}
