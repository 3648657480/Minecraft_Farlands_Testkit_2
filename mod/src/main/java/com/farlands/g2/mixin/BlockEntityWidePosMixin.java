package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wide block-entity positions in NBT.
 *
 * <p>Vanilla stores {@code x}/{@code y}/{@code z} as ints, and
 * {@code getPosFromTag} compares the section of that int with the chunk it was
 * loaded from. Past 2^31 the int wraps (observed: a block entity at
 * {@code 2147483675} loaded as {@code -2147483621} inside chunk
 * {@code [134217729, 0]}), the check fires, and the position is "repaired" to the
 * chunk - silently losing the real coordinate. This is data corruption, not a
 * distance phenomenon, so the fix belongs in storage: write the real longs
 * alongside the vanilla ints and prefer them on load.</p>
 */
@Mixin(BlockEntity.class)
public class BlockEntityWidePosMixin {

    @Inject(method = "saveWithoutMetadata", at = @At("RETURN"))
    private void farlands$saveWidePos(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        if (tag == null) {
            return;
        }
        BlockPos pos = ((BlockEntity) (Object) this).getBlockPos();
        long x = pos.getLongX();
        long y = pos.getLongY();
        long z = pos.getLongZ();
        if (x == (long) (int) x && y == (long) (int) y && z == (long) (int) z) {
            return; // normal range: keep the tag byte-identical to vanilla
        }
        tag.putLong("farlands$x", x);
        tag.putLong("farlands$y", y);
        tag.putLong("farlands$z", z);
    }

    @Inject(method = "getPosFromTag", at = @At("HEAD"), cancellable = true)
    private static void farlands$readWidePos(ChunkPos base, CompoundTag tag,
        CallbackInfoReturnable<BlockPos> cir) {
        if (!tag.contains("farlands$x")) {
            return;
        }
        cir.setReturnValue(new BlockPos(tag.getLong("farlands$x").orElse(0L),
            tag.getLong("farlands$y").orElse(0L), tag.getLong("farlands$z").orElse(0L)));
    }
}
