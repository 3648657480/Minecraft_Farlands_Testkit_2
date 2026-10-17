package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * W5: TreeFeature places its shape via StructureTemplate.updateShapeAtEdge,
 * whose edge scan uses int BlockPos offsets from the origin. At block >= 2^31
 * the wide origin's int view wraps to the negative side and the scan reads a
 * chunk 2^28 away (unsafe-read storm during feature generation). Skip the tree
 * feature when the origin is outside the int block domain; residual: no trees
 * in the last block band, terrain generation otherwise intact.
 */
@Mixin(TreeFeature.class)
public class TreeFeatureSeamGuardMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void farlands$guard(FeaturePlaceContext<?> context, CallbackInfoReturnable<Boolean> cir) {
        BlockPos origin = context.origin();
        long x = origin.getLongX();
        long z = origin.getLongZ();
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE
            || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) {
            cir.setReturnValue(false);
        }
    }
}
