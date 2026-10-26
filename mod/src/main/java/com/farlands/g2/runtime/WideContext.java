package com.farlands.g2.runtime;

/**
 * Wide-coordinate view of a density function context. Implemented by
 * {@link RealContext} and by {@code NoiseChunk} (via mixin), so code that only
 * sees {@code DensityFunction.FunctionContext} can still read exact long
 * coordinates without depending on the patched jar's extra methods.
 */
public interface WideContext {

    double getBlockXDouble();

    double getBlockYDouble();

    double getBlockZDouble();
}
