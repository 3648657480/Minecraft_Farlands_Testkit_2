package com.farlands.g2.runtime;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

/** W1-5 optional long-quart biome entry. */
public interface WideBiomeSource {

    Holder<Biome> getNoiseBiomeWide(long quartX, long quartY, long quartZ,
        Climate.Sampler sampler);
}
