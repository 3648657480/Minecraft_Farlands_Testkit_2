package com.farlands.g2.mixin;

import com.farlands.g2.runtime.FarSectionStore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restores the far-section sidecar immediately before vanilla validates and
 * creates block entities ({@code LevelChunk.runPostLoad}). Loading earlier (in
 * the chunk constructor) is overwritten by the vanilla NBT section pass; loading
 * later is too late: a chest at y=64 is rejected as "invalid block entity, got
 * air" because the normal-band section is not there yet.
 */
@Mixin(LevelChunk.class)
public class LevelChunkPostLoadFarStoreMixin {

    @Inject(method = "runPostLoad", at = @At("HEAD"))
    private void farlands$restoreBeforePostLoad(CallbackInfo ci) {
        LevelChunk self = (LevelChunk) (Object) this;
        if (self.getLevel() instanceof ServerLevel level) {
            FarSectionStore.load(level, self.getPos(), self);
        }
    }
}
