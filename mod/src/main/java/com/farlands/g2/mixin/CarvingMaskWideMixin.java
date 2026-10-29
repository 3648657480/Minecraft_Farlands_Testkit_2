package com.farlands.g2.mixin;

import java.util.BitSet;
import net.minecraft.world.level.chunk.CarvingMask;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Carving mask lookups at far Y.
 *
 * <p>The index is {@code (y - minY) << 8}; when a chunk window sits at a far Y
 * while a carver probes a dimension-band Y (or vice versa) that subtraction
 * overflows and {@link BitSet#get(int)} throws {@code bitIndex < 0}. Compute the
 * index in the long domain and treat unrepresentable indices as "not carved"
 * instead of crashing.</p>
 */
@Mixin(CarvingMask.class)
public class CarvingMaskWideMixin {

    @Shadow @Final private int minY;
    @Shadow @Final private BitSet mask;
    @Shadow private CarvingMask.Mask additionalMask;

    @org.spongepowered.asm.mixin.Unique
    private long farlands$index(int x, int y, int z) {
        return (long) (x & 15) | (long) (z & 15) << 4 | ((long) y - (long) this.minY) << 8;
    }

    @Overwrite
    public void set(int x, int y, int z) {
        long index = this.farlands$index(x, y, z);
        if (index >= 0L && index <= Integer.MAX_VALUE) {
            this.mask.set((int) index);
        }
    }

    @Overwrite
    public boolean get(int x, int y, int z) {
        if (this.additionalMask.test(x, y, z)) {
            return true;
        }
        long index = this.farlands$index(x, y, z);
        return index >= 0L && index <= Integer.MAX_VALUE && this.mask.get((int) index);
    }
}