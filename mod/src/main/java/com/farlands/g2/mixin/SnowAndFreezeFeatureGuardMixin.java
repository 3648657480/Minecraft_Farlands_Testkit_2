package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.SnowAndFreezeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * W5: SnowAndFreezeFeature builds int positions from origin.getX()/getZ()
 * (+dx/dz) and writes/reads through MutableBlockPos.set(int,int,int). At block
 * >= 2^31 the wide origin's int view wraps to the negative side, so the
 * feature reads/writes a chunk 2^28 away (unsafe-read storm, wrapped tick
 * scheduling). Skip the feature when the origin is outside the int block
 * domain; the residual is no snow/ice in the last block band, no corruption.
 */
@Mixin(SnowAndFreezeFeature.class)
public class SnowAndFreezeFeatureGuardMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void farlands$guard(FeaturePlaceContext<?> context, CallbackInfoReturnable<Boolean> cir) {
        BlockPos origin = context.origin();
        long x = origin.getLongX();
        long z = origin.getLongZ();
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE
            || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) {
            cir.setReturnValue(true);
        }
    }
}
