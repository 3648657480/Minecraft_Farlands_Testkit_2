package com.farlands.g2.mixin;

import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Mth.class)
public class MthCeilMixin {

    @Overwrite
    public static int ceil(double v) {
        return (int) (long) Math.ceil(v);
    }

    @Overwrite
    public static int ceil(float v) {
        return (int) (long) Math.ceil((double) v);
    }
}
