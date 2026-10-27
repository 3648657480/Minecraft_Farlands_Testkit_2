package com.farlands.g2.mixin;

import com.farlands.g2.runtime.NoiseChunkDiagAccess;
import com.farlands.g2.runtime.RealContext;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Diagnostic (-Dfarlands.diag.slice): at the seam chunk, print the slice a
 * noise interpolator just filled - filler class, the cell start (int vs long),
 * inCell X/Z, the values at the surface y range, the same filler evaluated with
 * an exact {@link RealContext} at those coordinates, and the first stack frames
 * (which caller filled the slice). Read-only; it never calls the interpolator
 * itself.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$NoiseInterpolator")
public abstract class NoiseChunkInterpolatorSliceDiagMixin {

    @Shadow @Final private NoiseChunk this$0;
    @Shadow private DensityFunction noiseFiller;

    @Unique
    private static final boolean FARLANDS$SLICE = Boolean.getBoolean("farlands.diag.slice");

    @Unique
    private static final AtomicInteger FARLANDS$N = new AtomicInteger();

    @Inject(method = "fillArray", at = @At("RETURN"))
    private void farlands$slice(double[] array, DensityFunction.ContextProvider provider,
        CallbackInfo ci) {
        if (!FARLANDS$SLICE) {
            return;
        }
        NoiseChunkDiagAccess acc = (NoiseChunkDiagAccess) (Object) this.this$0;
        if (acc.farlands$originX() != 2147483664L) {
            return;
        }
        long cornerX = acc.farlands$cellStartBlockXLong() + acc.farlands$inCellX();
        long cornerZ = acc.farlands$cellStartBlockZLong() + acc.farlands$inCellZ();
        if (cornerX < 2147483664L || cornerX > 2147483680L
            || cornerZ < 288L || cornerZ > 304L) {
            return;
        }
        if (FARLANDS$N.incrementAndGet() > 40) {
            return;
        }
        int cellHeight = acc.farlands$cellHeight();
        int minY = acc.farlands$cellNoiseMinY();
        StringBuilder slice = new StringBuilder();
        StringBuilder raw = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            int y = (i + minY) * cellHeight;
            if (y < 64 || y > 128) {
                continue;
            }
            slice.append(y).append(':').append(array[i]).append(' ');
            raw.append(y).append(':').append(this.noiseFiller.compute(
                new RealContext(cornerX, y, cornerZ))).append(' ');
        }
        StackTraceElement[] stack = new Throwable().getStackTrace();
        StringBuilder frames = new StringBuilder();
        for (int i = 1; i < Math.min(stack.length, 4); i++) {
            frames.append(" < ").append(stack[i].getClassName())
                .append('.').append(stack[i].getMethodName());
        }
        System.out.println("[SLICE] filler=" + this.noiseFiller.getClass().getSimpleName()
            + " cellX=" + acc.farlands$cellStartBlockX() + "/" + acc.farlands$cellStartBlockXLong()
            + " inCellX=" + acc.farlands$inCellX()
            + " cellZ=" + acc.farlands$cellStartBlockZ() + "/" + acc.farlands$cellStartBlockZLong()
            + " inCellZ=" + acc.farlands$inCellZ()
            + " cellStartY=" + acc.farlands$cellStartBlockY()
            + " len=" + array.length
            + " slice={" + slice + "}"
            + " raw={" + raw + "}"
            + " frames=" + frames);
        System.out.flush();
    }
}
