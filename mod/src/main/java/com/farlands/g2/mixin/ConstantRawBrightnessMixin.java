package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/** Constant raw brightness for the far-domain build. */
@Mixin(LevelLightEngine.class)
public class ConstantRawBrightnessMixin {

    /**
     * @author G2
     * @reason See ConstantLightValueMixin; the renderer asks this entry point.
     */
    @Overwrite
    public int getRawBrightness(BlockPos pos, int amount) {
        return 14;
    }
}
