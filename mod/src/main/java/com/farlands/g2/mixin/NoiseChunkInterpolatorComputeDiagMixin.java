package com.farlands.g2.mixin;

import com.farlands.g2.runtime.NoiseChunkDiagAccess;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Diagnostic (-Dfarlands.diag.interp): at the seam probe voxels, print the 8
 * corner fields and the lerp3 result of every noise interpolator computed
 * during a cell-cache fill ({@code fillingCell == true}), with the
 * interpolation weights and the cell starts, plus a local lerp3 recompute.
 * Read-only.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$NoiseInterpolator")
public abstract class NoiseChunkInterpolatorComputeDiagMixin {

    @Shadow @Final private NoiseChunk this$0;
    @Shadow private DensityFunction noiseFiller;
    @Shadow private double[][] slice0;
    @Shadow private double[][] slice1;
    @Shadow private double noise000;
    @Shadow private double noise100;
    @Shadow private double noise010;
    @Shadow private double noise110;
    @Shadow private double noise001;
    @Shadow private double noise101;
    @Shadow private double noise011;
    @Shadow private double noise111;

    @Unique
    private static final boolean FARLANDS$INTERP = Boolean.getBoolean("farlands.diag.interp");

    @Unique
    private static final Set<String> FARLANDS$SEEN =
        Collections.synchronizedSet(new HashSet<>());

    @Inject(method = "compute", at = @At("RETURN"))
    private void farlands$logCompute(DensityFunction.FunctionContext context,
        CallbackInfoReturnable<Double> cir) {
        if (!FARLANDS$INTERP) {
            return;
        }
        NoiseChunkDiagAccess acc = (NoiseChunkDiagAccess) (Object) this.this$0;
        if (!acc.farlands$fillingCell()) {
            return;
        }
        long wx = acc.farlands$cellStartBlockXLong() + acc.farlands$inCellX();
        long wz = acc.farlands$cellStartBlockZLong() + acc.farlands$inCellZ();
        int y = acc.farlands$cellStartBlockY() + acc.farlands$inCellY();
        long origin = acc.farlands$originX();
        if ((origin != 2147483664L && origin != 2147483680L)
            || (wx != 2147483664L && wx != 2147483680L) || wz != 293L || y != 120) {
            return;
        }
        String key = origin + "/" + this.noiseFiller.getClass().getName() + "/"
            + System.identityHashCode(this);
        if (!FARLANDS$SEEN.add(key)) {
            return;
        }
        int w = acc.farlands$cellWidth();
        int h = acc.farlands$cellHeight();
        double fx = (double) acc.farlands$inCellX() / (double) w;
        double fy = (double) acc.farlands$inCellY() / (double) h;
        double fz = (double) acc.farlands$inCellZ() / (double) w;
        double recomputed = Mth.lerp3(fx, fy, fz,
            this.noise000, this.noise100, this.noise010, this.noise110,
            this.noise001, this.noise101, this.noise011, this.noise111);
        System.out.println("[INTERP] origin=" + origin
            + " filler=" + this.noiseFiller.getClass().getSimpleName()
            + " x=" + wx + " y=" + y + " z=" + wz
            + " result=" + cir.getReturnValue() + " recomputed=" + recomputed
            + " same=" + (cir.getReturnValue() == recomputed)
            + " inCell=" + acc.farlands$inCellX() + "," + acc.farlands$inCellY()
            + "," + acc.farlands$inCellZ()
            + " w/h=" + w + "/" + h
            + " corners=[" + this.noise000 + "," + this.noise100 + ","
            + this.noise010 + "," + this.noise110 + "," + this.noise001 + ","
            + this.noise101 + "," + this.noise011 + "," + this.noise111 + "]"
            + " cellStart=" + acc.farlands$cellStartBlockX() + "/"
            + acc.farlands$cellStartBlockXLong() + ","
            + acc.farlands$cellStartBlockY() + ","
            + acc.farlands$cellStartBlockZ() + "/"
            + acc.farlands$cellStartBlockZLong()
            + " firstCellX/Z=" + acc.farlands$firstCellX() + "/" + acc.farlands$firstCellZ()
            + " slice0Hash=" + System.identityHashCode(this.slice0)
            + " slice1Hash=" + System.identityHashCode(this.slice1));
        System.out.flush();
    }

    @Inject(method = "swapSlices", at = @At("HEAD"))
    private void farlands$logSwap(CallbackInfo ci) {
        if (!FARLANDS$INTERP) {
            return;
        }
        System.out.println("[SLICEID] swapSlices before slice0=" + System.identityHashCode(this.slice0)
            + " slice1=" + System.identityHashCode(this.slice1));
        System.out.flush();
    }
}
