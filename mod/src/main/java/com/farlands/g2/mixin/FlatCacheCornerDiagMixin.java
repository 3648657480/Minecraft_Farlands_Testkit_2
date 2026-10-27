package com.farlands.g2.mixin;

import com.farlands.g2.runtime.RealContext;
import com.farlands.g2.runtime.WideContext;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Diagnostic (-Dfarlands.diag.flatcache2): at the +16 seam column's quart
 * corner, compare the {@code FlatCache} answer with a direct evaluation of its
 * filler at the same coordinates.
 *
 * <p>The cache is a 2D quart lattice filled once per chunk. If the cached value
 * differs from the raw filler at the same (x, 0, z) the lookup/column mapping
 * is wrong; if it matches, the flat cache is not the source of the wrong fill
 * density.</p>
 */
@Mixin(targets = "net/minecraft/world/level/levelgen/NoiseChunk$FlatCache")
public abstract class FlatCacheCornerDiagMixin {

    @Shadow private DensityFunction noiseFiller;

    @Unique
    private static final boolean FARLANDS$FLAT2 = Boolean.getBoolean("farlands.diag.flatcache2");

    @Unique
    private static int farlands$n;

    @Inject(method = "compute", at = @At("RETURN"))
    private void farlands$cmp(DensityFunction.FunctionContext context,
        CallbackInfoReturnable<Double> cir) {
        if (!FARLANDS$FLAT2 || farlands$n > 12) {
            return;
        }
        double x = context instanceof WideContext w ? w.getBlockXDouble() : context.blockX();
        double z = context instanceof WideContext w ? w.getBlockZDouble() : context.blockZ();
        if (x != 2147483664.0 || (z != 292.0 && z != 296.0)) {
            return;
        }
        farlands$n++;
        double raw = this.noiseFiller.compute(new RealContext((long) x, 0L, (long) z));
        System.out.println("[FLATCACHE2] x=" + x + " z=" + z
            + " cached=" + cir.getReturnValue() + " raw=" + raw);
        System.out.flush();
    }
}
