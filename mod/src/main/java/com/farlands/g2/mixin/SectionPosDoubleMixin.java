package com.farlands.g2.mixin;

import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * X1b: camera block -> section must not fold through int. {@code Mth.floor}
 * saturates at 2^31-1, so a camera exactly at block 2^31 lands one section low.
 * Compute the floor in the long domain and clamp only when converting back to
 * the int section coordinate.
 */
@Mixin(SectionPos.class)
public class SectionPosDoubleMixin {

    @Overwrite
    public static int posToSectionCoord(double pos) {
        long floor = (long) Math.floor(pos);
        long section = Math.floorDiv(floor, 16L);
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, section));
    }
}
