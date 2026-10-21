package com.farlands.g2.mixin;

import java.util.Objects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Stored NBT positions are int views; accept them when they match the requested chunk's int view. */
@Mixin(SerializableChunkData.class)
public class SerializableChunkDataPositionMixin {

    @Redirect(method = "read",
        at = @At(value = "INVOKE", target = "Ljava/util/Objects;equals(Ljava/lang/Object;Ljava/lang/Object;)Z"))
    private static boolean farlands$sameStoredChunk(Object requested, Object stored) {
        if (requested instanceof ChunkPos a && stored instanceof ChunkPos b) {
            return (a.xLong() == b.xLong() && a.zLong() == b.zLong())
                || (a.x() == b.x() && a.z() == b.z());
        }
        return Objects.equals(requested, stored);
    }
}
