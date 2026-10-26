package com.farlands.g2.runtime;

import com.farlands.g2.runtime.WindowedChunk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * W-2: block access for positions whose real coordinate does not fit int.
 * Routes to the chunk's absolute section store (route D data layer) and uses
 * long local coordinates; normal positions never enter here.
 */
public final class WideBlockAccess {

    private WideBlockAccess() {
    }

    public static boolean isWide(long x, long y, long z) {
        return x != (long) (int) x || y != (long) (int) y || z != (long) (int) z;
    }

    private static int sectionY(long y) {
        return (int) Math.floorDiv(y, 16L);
    }

    private static int local(long v) {
        return (int) Math.floorMod(v, 16L);
    }

    public static BlockState get(ChunkAccess chunk, long x, long y, long z) {
        if (!(chunk instanceof WindowedChunk windowed)) {
            return Blocks.VOID_AIR.defaultBlockState();
        }
        LevelChunkSection section = windowed.farlands$allSections()
            .get(windowed.farlands$key(com.scalarwide.mc.WideSectionCoord512.of(sectionY(y))));
        if (section == null) {
            return Blocks.VOID_AIR.defaultBlockState();
        }
        return section.getBlockState(local(x), local(y), local(z));
    }

    public static BlockState set(ChunkAccess chunk, long x, long y, long z, BlockState state) {
        if (!(chunk instanceof WindowedChunk windowed)) {
            return Blocks.VOID_AIR.defaultBlockState();
        }
        LevelChunkSection section = windowed.farlands$sectionAt(
            com.scalarwide.mc.WideSectionCoord512.of(sectionY(y)));
        return section.setBlockState(local(x), local(y), local(z), state);
    }
}
