package com.farlands.g2.mixin;

import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the saved min section ({@code yPos}) of chunk data being loaded. */
@Mixin(SerializableChunkData.class)
public interface SerializableChunkDataAccessor {

    @Accessor("minSectionY")
    int farlands$minSectionY();
}
