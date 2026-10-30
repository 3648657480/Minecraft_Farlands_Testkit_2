package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.atomic.AtomicLong;

/**
 * W1 collision cursor probe: log the block state that BlockCollisions actually
 * reads at far X/Z. Gated by {@code -Dfarlands.diag.collisionWide=true}; no
 * behavior change.
 */
@Mixin(BlockCollisions.class)
public class BlockCollisionsProbeMixin {

    @Unique
    private static final AtomicLong farlands$reads = new AtomicLong();

    @Redirect(method = "computeNext",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState farlands$logRead(BlockGetter getter, BlockPos pos) {
        BlockState state = getter.getBlockState(pos);
        if (Boolean.getBoolean("farlands.diag.collisionWide")
            && Math.abs(pos.getLongX()) > (1L << 40)) {
            long n = farlands$reads.incrementAndGet();
            if (n <= 24) {
                System.out.println("[COLLBLK] n=" + n + " pos=" + pos.getLongX() + ","
                    + pos.getLongY() + "," + pos.getLongZ()
                    + " state=" + state + " getter=" + getter.getClass().getSimpleName());
                System.out.flush();
            }
        }
        return state;
    }
}
