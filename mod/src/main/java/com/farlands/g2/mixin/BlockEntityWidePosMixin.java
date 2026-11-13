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
 * loaded from. Past block 2^31 two separate things go wrong:</p>
 *
 * <ul>
 *   <li><b>Generation</b>: {@code WorldGenRegion.setBlock} writes a
 *       {@code DUMMY} tag for every block entity placed by a feature/structure
 *       ({@code tag.putInt("x", pos.getX())}). At block 2^31 the int wraps
 *       (observed: {@code x:-2147483621} for the real {@code 2147483675} in
 *       chunk {@code [134217729,0]}), the {@code getPosFromTag} check fires and
 *       "repairs" the position with {@code ChunkPos.getBlockX(int)}, which
 *       overflows the same way. {@code ChunkAccess.setBlockEntityNbt} then keys
 *       the pending block entity on a wrapped position, so it never resolves to
 *       the real block: chests/loot/furnace NBT created at the seam is dropped.
 *       Measured on a fresh world at chunk {@code 134217727}+7x7: 100+ warnings,
 *       all {@code DUMMY}, all from worker threads.</li>
 *   <li><b>Save/reload</b>: {@code saveMetadata} writes the same int coords, so
 *       the tag on disk carries the wrapped value; a far chunk must not trust
 *       it (at 2^53 the wrapped value can even make the vanilla chunk check
 *       pass and silently return block 0).</li>
 * </ul>
 *
 * <p>The four low bits of a block coordinate survive the int wrap (the wrap is
 * 2^32, which is a multiple of 16), and the tag is stored in the chunk that
 * contains the block. So the exact position is
 * {@code chunkMinBlock + (wrappedInt & 15)}. That is what this mixin uses,
 * through the wide {@code ChunkPos} accessors. Chunks whose block origin still
 * fits an int keep the vanilla path untouched (bit-identical at normal
 * coordinates).</p>
 *
 * <p>Tags that carry {@code farlands$x/y/z} (the wide NBT written next to the
 * vanilla ints for the network/component path) are preferred when present.</p>
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
        if (tag.contains("farlands$x")) {
            cir.setReturnValue(new BlockPos(tag.getLong("farlands$x").orElse(0L),
                tag.getLong("farlands$y").orElse(0L), tag.getLong("farlands$z").orElse(0L)));
            return;
        }
        long minX = base.getMinBlockXLong();
        long minZ = base.getMinBlockZLong();
        if (minX == (long) (int) minX && minZ == (long) (int) minZ) {
            return; // chunk origin still fits an int: vanilla int path is exact
        }
        int x = tag.getIntOr("x", 0);
        int z = tag.getIntOr("z", 0);
        // Low four bits survive the 2^32 wrap; the tag lives in the chunk that
        // contains the block, so chunk origin + local offset is exact.
        cir.setReturnValue(new BlockPos(minX + (x & 15), tag.getIntOr("y", 0),
            minZ + (z & 15)));
    }
}
