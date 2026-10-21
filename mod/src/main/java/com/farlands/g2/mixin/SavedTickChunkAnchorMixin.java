package com.farlands.g2.mixin;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.ticks.SavedTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/** Re-anchor saved tick positions from the chunk origin instead of trusting int NBT x/z. */
@Mixin(SavedTick.class)
public class SavedTickChunkAnchorMixin {

    /**
     * @author G2
     * @reason SavedTick.codec stores x/z as Codec.INT, so ticks west of block 2^31
     *         load with negative int coordinates and LevelTicks cannot find their
     *         chunk container. The list belongs to one chunk, so rebuild each
     *         position from that chunk's wide origin plus the local 0..15 offset.
     */
    @Overwrite
    public static <T> List<SavedTick<T>> filterTickListForChunk(List<SavedTick<T>> savedTicks, ChunkPos chunkPos) {
        long baseX = chunkPos.xLong() << 4;
        long baseZ = chunkPos.zLong() << 4;
        List<SavedTick<T>> out = new ArrayList<>(savedTicks.size());
        for (SavedTick<T> tick : savedTicks) {
            BlockPos p = tick.pos();
            long x = baseX + (p.getX() & 15);
            long z = baseZ + (p.getZ() & 15);
            out.add(new SavedTick<>(tick.type(), new BlockPos(x, p.getY(), z), tick.delay(), tick.priority()));
        }
        return out;
    }
}
