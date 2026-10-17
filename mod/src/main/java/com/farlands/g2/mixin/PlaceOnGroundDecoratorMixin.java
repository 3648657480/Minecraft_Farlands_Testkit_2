package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.treedecorators.PlaceOnGroundDecorator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * W5 guard: tree log positions are wide BlockPos values. A tree whose block
 * reaches x/z = 2^31 has int getX()/getZ() = Integer.MIN_VALUE, which moves
 * this decorator's sampling box across the seam (reads at chunk -134217728
 * while generating +134217727, retry storm). Clamp the long coordinate to the
 * int boundary here; at normal coordinates getLongX() == getX(), so behaviour
 * is unchanged. Residual: decorations of a tree straddling the seam collapse
 * onto the edge block instead of the first block across it.
 */
@Mixin(PlaceOnGroundDecorator.class)
public class PlaceOnGroundDecoratorMixin {

    @Redirect(method = "place", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/core/BlockPos;getX()I"))
    private int farlands$clampX(BlockPos pos) {
        return (int) Math.clamp(pos.getLongX(), (long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE);
    }

    @Redirect(method = "place", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/core/BlockPos;getZ()I"))
    private int farlands$clampZ(BlockPos pos) {
        return (int) Math.clamp(pos.getLongZ(), (long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE);
    }
}
