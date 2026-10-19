package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Level.class)
public class LevelMixin {
    @Overwrite
    private static boolean isInWorldBoundsHorizontal(BlockPos pos) {
        return true;
    }

    @Overwrite
    private static boolean isInValidBoundsHorizontal(BlockPos pos) {
        return true;
    }
}
