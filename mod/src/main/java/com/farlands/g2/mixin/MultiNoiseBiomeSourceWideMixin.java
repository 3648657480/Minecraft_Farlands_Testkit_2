package com.farlands.g2.mixin;

import com.farlands.g2.runtime.ClimateWide;
import com.farlands.g2.runtime.WideBiomeSource;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;

/**
 * W1-5: long-quart entry for the overworld biome source. The vanilla
 * {@code getNoiseBiome(int,int,int,Climate.Sampler)} path cannot carry quart
 * 2^51 (block 2^53); this entry computes the target point from long quart and
 * uses the same parameter list.
 */
@Mixin(MultiNoiseBiomeSource.class)
public class MultiNoiseBiomeSourceWideMixin implements WideBiomeSource {

    @Override
    public Holder<Biome> getNoiseBiomeWide(long quartX, long quartY, long quartZ,
        Climate.Sampler sampler) {
        MultiNoiseBiomeSource self = (MultiNoiseBiomeSource) (Object) this;
        return self.getNoiseBiome(ClimateWide.target(sampler, quartX, quartY, quartZ));
    }
}
