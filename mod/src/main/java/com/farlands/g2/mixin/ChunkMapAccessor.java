package com.farlands.g2.mixin;

import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Accessor for the private unload path used by the Y-D regeneration probe. */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {

    @Invoker("scheduleUnload")
    void farlands$scheduleUnload(long pos, ChunkHolder holder);

    @Invoker("tick")
    void farlands$tick(BooleanSupplier haveTime);
}
