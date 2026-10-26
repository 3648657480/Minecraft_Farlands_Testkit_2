package com.farlands.g2.mixin;

import com.farlands.g2.runtime.WindowedChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Route D0: far-Y block access goes straight to the chunk's absolute section
 * store. No window, no page base: the section key is the real absolute Y.
 * Reads never create sections; writes create them on demand.
 */
@Mixin(Level.class)
public class LevelAbsYFastMixin {

    private static boolean farlands$far(long y) {
        return Math.abs(y) > 1_000_000L;
    }

    private static int farlands$local(long block) {
        return (int) Math.floorMod(block, 16L);
    }

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void farlands$absGet(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        long y = pos.getLongY();
        if (!farlands$far(y)) {
            return;
        }
        Level self = (Level) (Object) this;
        ChunkAccess chunk = self.getChunkSource().getChunk(
            SectionPos.blockToSectionCoord(pos.getX()),
            SectionPos.blockToSectionCoord(pos.getZ()), ChunkStatus.FULL, false);
        if (chunk instanceof WindowedChunk windowed) {
            LevelChunkSection section = windowed.farlands$allSections()
                .get(windowed.farlands$key((int) Math.floorDiv(y, 16L)));
            cir.setReturnValue(section == null ? Blocks.VOID_AIR.defaultBlockState()
                : section.getBlockState(farlands$local(pos.getX()),
                    farlands$local(y), farlands$local(pos.getZ())));
        } else {
            cir.setReturnValue(Blocks.VOID_AIR.defaultBlockState());
        }
    }

    @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true)
    private void farlands$absSet(BlockPos pos, BlockState state, int flags,
        CallbackInfoReturnable<Boolean> cir) {
        long y = pos.getLongY();
        if (!farlands$far(y)) {
            return;
        }
        Level self = (Level) (Object) this;
        ChunkAccess chunk = self.getChunkSource().getChunk(
            SectionPos.blockToSectionCoord(pos.getX()),
            SectionPos.blockToSectionCoord(pos.getZ()), ChunkStatus.FULL, false);
        if (chunk instanceof WindowedChunk windowed) {
            LevelChunkSection section = windowed.farlands$sectionAt((int) Math.floorDiv(y, 16L));
            BlockState old = section.setBlockState(farlands$local(pos.getX()),
                farlands$local(y), farlands$local(pos.getZ()), state);
            cir.setReturnValue(!old.equals(state));
        }
    }
}
