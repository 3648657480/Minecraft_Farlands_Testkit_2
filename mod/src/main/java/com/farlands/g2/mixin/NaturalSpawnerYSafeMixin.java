package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Y-E: vanilla {@code getRandomPosWithin} draws Y between the <b>level's</b>
 * minimum Y and the chunk's surface height. With a moved vertical window the
 * level minimum (-64) and the surface (billions) differ by ~2^31, so
 * {@code max - min + 1} overflows and {@code nextInt} throws
 * "Bound must be positive" during {@code tickSpawningChunk}.
 *
 * <p>Use the chunk's own vertical bounds (the window) for the range; normal
 * worlds have identical level/chunk bounds, so the behaviour is unchanged.</p>
 */
@Mixin(NaturalSpawner.class)
public class NaturalSpawnerYSafeMixin {

    @Overwrite
    private static BlockPos getRandomPosWithin(Level level, LevelChunk chunk) {
        var pos = chunk.getPos();
        int x = pos.getMinBlockX() + level.getRandom().nextInt(16);
        int z = pos.getMinBlockZ() + level.getRandom().nextInt(16);
        int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
        int minY = Math.max(level.getMinY(), chunk.getMinY());
        int maxY = Math.min(surface, chunk.getMaxY());
        if (maxY < minY) {
            maxY = minY;
        }
        int y = Mth.randomBetweenInclusive(level.getRandom(), minY, maxY);
        return new BlockPos(x, y, z);
    }
}
