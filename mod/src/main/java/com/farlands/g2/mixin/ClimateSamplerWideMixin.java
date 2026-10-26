package com.farlands.g2.mixin;

import com.farlands.g2.runtime.RealContext;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * X1c: climate/biome sampling must use long block coordinates. Vanilla does
 * {@code QuartPos.toBlock(quartX)} (a 32-bit shift) before sampling, which wraps
 * at block 2^31 and makes the biome east of the seam disagree with the west side.
 *
 * <p>This is the single place the sampler converts quart to block; compute the
 * block coordinate in the long domain and hand an exact context to the density
 * functions (which already read {@code getBlockXDouble}).</p>
 */
@Mixin(Climate.Sampler.class)
public class ClimateSamplerWideMixin {

    @Inject(method = "sample", at = @At("HEAD"), cancellable = true)
    private void farlands$wideSample(int quartX, int quartY, int quartZ,
        CallbackInfoReturnable<Climate.TargetPoint> cir) {
        Climate.Sampler self = (Climate.Sampler) (Object) this;
        if (Boolean.getBoolean("farlands.diag.climate") && quartX == 536870912) {
            System.out.println("[CLIM] qx=" + quartX + " bx=" + ((long) quartX << 2)
                + " y=" + quartY + " z=" + quartZ);
            System.out.flush();
        }
        RealContext context = new RealContext((long) quartX << 2, (long) quartY << 2,
            (long) quartZ << 2);
        cir.setReturnValue(Climate.target(
            (float) self.temperature().compute(context),
            (float) self.humidity().compute(context),
            (float) self.continentalness().compute(context),
            (float) self.erosion().compute(context),
            (float) self.depth().compute(context),
            (float) self.weirdness().compute(context)));
    }
}
