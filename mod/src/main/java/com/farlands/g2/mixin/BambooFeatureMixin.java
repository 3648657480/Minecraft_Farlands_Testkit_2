package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.BambooFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.ProbabilityFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BambooFeature.class)
public class BambooFeatureMixin {

    @Shadow private static BlockState BAMBOO_TRUNK;
    @Shadow private static BlockState BAMBOO_FINAL_LARGE;
    @Shadow private static BlockState BAMBOO_TOP_LARGE;
    @Shadow private static BlockState BAMBOO_TOP_SMALL;

    @Overwrite
    public boolean place(FeaturePlaceContext<ProbabilityFeatureConfiguration> context) {
        boolean ok = false;
        BlockPos origin = context.origin();
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        ProbabilityFeatureConfiguration config = context.config();
        BlockPos.MutableBlockPos pos = origin.mutable();
        BlockPos.MutableBlockPos below = origin.mutable();

        if (level.isEmptyBlock(pos)) {
            if (Blocks.BAMBOO.defaultBlockState().canSurvive(level, pos)) {
                int height = random.nextInt(12) + 5;
                if (random.nextFloat() < config.probability) {
                    int radius = random.nextInt(4) + 1;
                    long ox = origin.getLongX();
                    long oz = origin.getLongZ();
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (dx * dx + dz * dz <= radius * radius) {
                                long x = ox + dx;
                                long z = oz + dz;
                                int h = heightAt(level, Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                                below.set(x, h, z);
                                if (level.getBlockState(below).is(BlockTags.BENEATH_BAMBOO_PODZOL_REPLACEABLE)) {
                                    level.setBlock(below, Blocks.PODZOL.defaultBlockState(), 2);
                                }
                            }
                        }
                    }
                }

                for (int i = 0; i < height && level.isEmptyBlock(pos); i++) {
                    level.setBlock(pos, BAMBOO_TRUNK, 2);
                    pos.move(Direction.UP, 1);
                }

                if (pos.getY() - origin.getY() >= 3) {
                    level.setBlock(pos, BAMBOO_FINAL_LARGE, 2);
                    pos.move(Direction.DOWN, 1);
                    level.setBlock(pos, BAMBOO_TOP_LARGE, 2);
                    pos.move(Direction.DOWN, 1);
                    level.setBlock(pos, BAMBOO_TOP_SMALL, 2);
                }
                ok = true;
            } else {
                ok = true;
            }
        }
        return ok;
    }

    private static int heightAt(WorldGenLevel level, Heightmap.Types type, long x, long z) {
        ChunkAccess chunk = level.getChunk((int) (x >> 4), (int) (z >> 4));
        return chunk.getHeight(type, (int) (x & 15), (int) (z & 15)) + 1;
    }
}
