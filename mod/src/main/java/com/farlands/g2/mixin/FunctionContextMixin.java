package com.farlands.g2.mixin;

import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DensityFunction.FunctionContext.class)
public interface FunctionContextMixin extends com.farlands.g2.runtime.RealCoords {
    @Override
    default double getBlockXDouble() { return ((DensityFunction.FunctionContext) this).blockX(); }
    @Override
    default double getBlockYDouble() { return ((DensityFunction.FunctionContext) this).blockY(); }
    @Override
    default double getBlockZDouble() { return ((DensityFunction.FunctionContext) this).blockZ(); }
}
