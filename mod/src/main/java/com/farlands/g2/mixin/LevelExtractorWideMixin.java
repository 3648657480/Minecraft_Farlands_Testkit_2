package com.farlands.g2.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;

/**
 * The render extractor derives dirty sections from BlockPos.getX()/getZ().
 * East of 2^31 those ints wrap, so the client marks a section in the negative
 * band while the real render sections are positive and the mesh never
 * rebuilds. Resolve section coordinates from the wide block fields.
 */
@Mixin(LevelExtractor.class)
public class LevelExtractorWideMixin {

    @Shadow private Minecraft minecraft;

    @Unique
    private void farlands$markWideSections(BlockPos pos) {
        int sx = SectionPos.blockToSectionCoord(pos.getLongX());
        int sy = SectionPos.blockToSectionCoord(pos.getLongY());
        int sz = SectionPos.blockToSectionCoord(pos.getLongZ());
        ((LevelExtractor) (Object) this).setSectionDirtyWithNeighbors(sx, sy, sz);
    }

    @Overwrite
    private void setBlockDirty(BlockPos pos, boolean flag) {
        farlands$markWideSections(pos);
    }

    @Overwrite
    public void setBlockDirty(BlockPos pos, BlockState oldState, BlockState newState) {
        if (!this.minecraft.getModelManager().requiresRender(oldState, newState)) {
            return;
        }
        farlands$markWideSections(pos);
    }
}
