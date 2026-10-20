package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Level.getBlockState resolves the chunk from BlockPos.getX() (int) instead of
 * getChunkAt, so east of block 2^31 it looks up the negative chunk band while
 * the client stores the chunk under the positive coordinate. AABB/raycast,
 * interaction and entity collision all lose the east blocks. Use the wide
 * chunk resolver only near the seam; normal coordinates keep the vanilla path.
 */
@Mixin(Level.class)
public class LevelGetBlockStateWideMixin {

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void farlands$wide(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        long sx = pos.getLongX() >> 4;
        long sz = pos.getLongZ() >> 4;
        if (Math.abs(sx) < 134_217_000L && Math.abs(sz) < 134_217_000L) {
            return; // normal domain: vanilla
        }
        Level self = (Level) (Object) this;
        LevelChunk chunk = self.getChunkAt(pos);
        cir.setReturnValue(chunk == null ? Blocks.VOID_AIR.defaultBlockState() : chunk.getBlockState(pos));
    }
}
