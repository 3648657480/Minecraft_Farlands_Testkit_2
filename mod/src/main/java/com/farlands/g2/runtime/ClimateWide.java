package com.farlands.g2.runtime;

import net.minecraft.world.level.biome.Climate;

/**
 * W1-5: compute a climate target point from absolute long quart coordinates.
 * The vanilla {@link Climate.Sampler#sample(int,int,int)} API cannot represent
 * quart 2^51 (block 2^53); this helper shifts the quart coordinate to a long
 * block coordinate and uses {@link RealContext}, whose absolute-double accessors
 * preserve the real magnitude.
 */
public final class ClimateWide {

    private ClimateWide() {
    }

    public static Climate.TargetPoint target(Climate.Sampler sampler,
        long quartX, long quartY, long quartZ) {
        RealContext ctx = new RealContext(quartX << 2, quartY << 2, quartZ << 2);
        return Climate.target(
            (float) sampler.temperature().compute(ctx),
            (float) sampler.humidity().compute(ctx),
            (float) sampler.continentalness().compute(ctx),
            (float) sampler.erosion().compute(ctx),
            (float) sampler.depth().compute(ctx),
            (float) sampler.weirdness().compute(ctx));
    }
}
