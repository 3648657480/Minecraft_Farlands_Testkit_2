package com.farlands.g2.mixin;

import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChunkAccess.class)
public abstract class ChunkAccessFillBiomesMixin {

    @Shadow public abstract ChunkPos getPos();

    @Shadow public abstract LevelHeightAccessor getHeightAccessorForGeneration();

    @Shadow public abstract LevelChunkSection[] getSections();

    @Overwrite
    public void fillBiomesFromNoise(BiomeResolver resolver, Climate.Sampler sampler) {
        ChunkPos pos = this.getPos();
        int quartX = (int) (pos.getMinBlockXLong() >> 2);
        int quartZ = (int) (pos.getMinBlockZLong() >> 2);
        LevelHeightAccessor height = this.getHeightAccessorForGeneration();
        int minSectionY = height.getMinSectionY();
        LevelChunkSection[] sections = this.getSections();
        for (int sectionY = minSectionY; sectionY <= height.getMaxSectionY(); sectionY++) {
            LevelChunkSection section = sections[sectionY - minSectionY];
            section.fillBiomesFromNoise(resolver, sampler, quartX,
                QuartPos.fromSection(sectionY), quartZ);
        }
    }
}
