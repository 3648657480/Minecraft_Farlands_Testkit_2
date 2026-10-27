package com.farlands.g2.mixin;

import com.farlands.g2.runtime.RealContext;
import com.farlands.g2.runtime.WideContext;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FindTopSurface rebuilt its sampling context from the int block accessors, so at
 * far X it sampled the wrapped column and returned a wrong preliminary surface.
 * Re-run the same scan with the exact long column.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$FindTopSurface")
public class FindTopSurfaceWideMixin {

    @Shadow private DensityFunction density;
    @Shadow private DensityFunction upperBound;
    @Shadow private int lowerBound;
    @Shadow private int cellHeight;

    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    private void farlands$wideCompute(DensityFunction.FunctionContext context,
        CallbackInfoReturnable<Double> cir) {
        long realX = context instanceof WideContext wide ? (long) wide.getBlockXDouble()
            : context.blockX();
        long realZ = context instanceof WideContext wide ? (long) wide.getBlockZDouble()
            : context.blockZ();
        int topY = Mth.floor(this.upperBound.compute(context) / this.cellHeight) * this.cellHeight;
        if (topY <= this.lowerBound) {
            cir.setReturnValue((double) this.lowerBound);
            return;
        }
        for (int blockY = topY; blockY >= this.lowerBound; blockY -= this.cellHeight) {
            if (this.density.compute(new RealContext(realX, blockY, realZ)) > 0.0) {
                cir.setReturnValue((double) blockY);
                return;
            }
        }
        cir.setReturnValue((double) this.lowerBound);
    }
}