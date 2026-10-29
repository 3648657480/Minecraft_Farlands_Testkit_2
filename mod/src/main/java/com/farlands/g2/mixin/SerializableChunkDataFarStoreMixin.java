package com.farlands.g2.mixin;

import com.farlands.g2.runtime.FarSectionStore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Additive sidecar for far-Y window sections.
 *
 * <p>{@code SerializableChunkData.copyOf} walks only the light-engine range and
 * writes section Y as a byte, so a far window is never persisted. The sidecar
 * writes/restores {@code farlands$all} instead; the vanilla NBT is untouched.</p>
 */
@Mixin(SerializableChunkData.class)
public class SerializableChunkDataFarStoreMixin {

    @Inject(method = "copyOf", at = @At("RETURN"))
    private static void farlands$saveFarSections(ServerLevel level, ChunkAccess chunk,
        CallbackInfoReturnable<SerializableChunkData> cir) {
        FarSectionStore.save(level, chunk);
    }
}
