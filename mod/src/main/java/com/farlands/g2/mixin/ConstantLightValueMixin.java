package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/** Constant light level for the far-domain build. */
@Mixin(LightEngine.class)
public class ConstantLightValueMixin {

    /**
     * @author G2
     * @reason Wide sections cannot share the packed 64-bit light node; with all
     *         players beyond 2^31 we use a flat level 14 instead of propagating.
     */
    @Overwrite
    public int getLightValue(BlockPos pos) {
        return 14;
    }
}
