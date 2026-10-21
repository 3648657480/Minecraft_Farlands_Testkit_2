package com.farlands.g2.mixin;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Rebuilds applyBiomeDecoration's ChunkPos window from the wide centre. */
@Mixin(ChunkGenerator.class)
public class BiomeDecorationWindowMixin {

    @Redirect(method = "applyBiomeDecoration",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/ChunkPos;rangeClosed(Lnet/minecraft/world/level/ChunkPos;I)Ljava/util/stream/Stream;"))
    private static Stream<ChunkPos> farlands$window(ChunkPos center, int radius) {
        final long cx = center.xLong();
        final long cz = center.zLong();
        final int side = radius * 2 + 1;
        final int total = side * side;
        Iterator<ChunkPos> it = new Iterator<>() {
            private int i;
            @Override public boolean hasNext() { return i < total; }
            @Override public ChunkPos next() {
                int dx = i / side;
                int dz = i % side;
                i++;
                return new ChunkPos(cx - radius + dx, cz - radius + dz);
            }
        };
        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED | Spliterator.NONNULL), false);
    }
}
