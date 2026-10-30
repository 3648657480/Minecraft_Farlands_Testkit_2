package com.farlands.g2.mixin;

import com.farlands.g2.runtime.FarCollisionHelper;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicLong;

/**
 * W1 collision chunk access: far block coordinates must not fall through
 * {@code (int)(x>>4)} / {@code ChunkPos.pack(int,int)}. Normal coordinates keep
 * the existing patched path untouched; only chunk coordinates outside int use
 * the wide chunk map.
 */
@Mixin(BlockCollisions.class)
public class BlockCollisionsWideChunkMixin {

    @Shadow private CollisionGetter collisionGetter;

    @org.spongepowered.asm.mixin.Unique
    private static final AtomicLong farlands$chunkCalls = new AtomicLong();

    @Inject(method = "getChunk", at = @At("HEAD"), cancellable = true)
    private void farlands$wideChunk(long x, long z, CallbackInfoReturnable<BlockGetter> cir) {
        long chunkX = x >> 4;
        long chunkZ = z >> 4;
        if (chunkX == (long) (int) chunkX && chunkZ == (long) (int) chunkZ) {
            return;
        }
        BlockGetter result = FarCollisionHelper.getChunkForCollisions(collisionGetter, x, z);
        long n = farlands$chunkCalls.incrementAndGet();
        if (n <= 5 && Boolean.getBoolean("farlands.diag.collisionWide")) {
            System.out.println("[COLLWIDE-CHUNK] n=" + n + " x=" + x + " z=" + z
                + " getter=" + collisionGetter.getClass().getSimpleName()
                + " result=" + (result == null ? "null" : result.getClass().getSimpleName()));
            System.out.flush();
        }
        cir.setReturnValue(result);
    }
}
