package com.farlands.g2.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only accessors for the spawn-stall diagnostics (task c): the entity
 * manager's chunk load bookkeeping tells whether a stalled spawn chunk never
 * reached a non-HIDDEN full status (scheduling) or is stuck mid-load (I/O).
 */
@Mixin(PersistentEntitySectionManager.class)
public interface PersistentEntitySectionManagerAccessor {

    @Accessor("chunkLoadStatuses")
    Long2ObjectMap farlands$chunkLoadStatuses();

    @Accessor("chunkVisibility")
    Long2ObjectMap farlands$chunkVisibility();
}
