package com.farlands.g2.mixin;

import com.farlands.g2.runtime.WideContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Diagnostic (-Dfarlands.diag.aqsub): follow one column of the +16 seam chunk
 * through {@code Aquifer$NoiseBasedAquifer.computeSubstance}.
 *
 * <p>Prints, per y, the density handed to the aquifer, the aquifer's
 * {@code skipSamplingAboveY} threshold and the returned substance. That
 * separates the three ways the fill can end up solid above the density
 * surface: {@code density &gt; 0} (cache/lattice wrong), the barrier term
 * ({@code density + barrier12 &gt; 0}, aquifer noise/grid), or the
 * above-surface bypass ({@code posY &gt; skipSamplingAboveY}).</p>
 */
@Mixin(targets = "net/minecraft/world/level/levelgen/Aquifer$NoiseBasedAquifer")
public class AquiferSubstanceDiagMixin {

    @Shadow private int skipSamplingAboveY;

    @Unique private static final boolean FARLANDS$AQSUB =
        Boolean.getBoolean("farlands.diag.aqsub");

    @Unique private static final boolean[] FARLANDS$SEEN = new boolean[512];

    @Unique private int farlands$slot(DensityFunction.FunctionContext context) {
        if (!FARLANDS$AQSUB) {
            return -1;
        }
        double x = context instanceof WideContext w ? w.getBlockXDouble() : context.blockX();
        double z = context instanceof WideContext w ? w.getBlockZDouble() : context.blockZ();
        int y = context.blockY();
        if (x != 2147483664.0 || z != 293.0 || y < -64 || y > 130) {
            return -1;
        }
        return y + 64;
    }

    @Inject(method = "computeSubstance", at = @At("HEAD"))
    private void farlands$head(DensityFunction.FunctionContext context, double density,
        CallbackInfoReturnable<BlockState> cir) {
        int slot = farlands$slot(context);
        if (slot < 0 || FARLANDS$SEEN[slot]) {
            return;
        }
        FARLANDS$SEEN[slot] = true;
        System.out.println("[AQSUB] y=" + context.blockY() + " density=" + density
            + " skipAboveY=" + this.skipSamplingAboveY
            + " ctx=" + context.getClass().getSimpleName());
        System.out.flush();
    }

    @Inject(method = "computeSubstance", at = @At("RETURN"))
    private void farlands$ret(DensityFunction.FunctionContext context, double density,
        CallbackInfoReturnable<BlockState> cir) {
        int slot = farlands$slot(context);
        if (slot < 0) {
            return;
        }
        System.out.println("[AQSUB] y=" + context.blockY() + " -> " + cir.getReturnValue());
        System.out.flush();
    }
}
