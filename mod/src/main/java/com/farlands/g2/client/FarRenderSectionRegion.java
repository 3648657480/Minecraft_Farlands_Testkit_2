package com.farlands.g2.client;

import com.farlands.g2.runtime.section.SectionKey;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCopy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * A 3x3x3 region whose section lookup is relative to its own wide base section.
 * Vanilla's minSectionX/Y/Z are ints; using -1 with absolute 2^49 section coords
 * sends every lookup out of range and silently falls back to the centre copy.
 */
public final class FarRenderSectionRegion extends RenderSectionRegion {

    private final SectionKey base;
    private final SectionCopy[] copies;
    private final ClientLevel level;
    private SectionCopy empty;

    public FarRenderSectionRegion(ClientLevel level, SectionKey base, SectionCopy[] copies) {
        super(level, base.x - 1, base.y - 1, base.z - 1, copies);
        this.base = base;
        this.copies = copies;
        this.level = level;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return section(pos).getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return section(pos).getBlockState(pos).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return section(pos).getBlockEntity(pos);
    }

    private SectionCopy section(BlockPos pos) {
        long sx = Math.floorDiv(pos.getLongX(), 16L);
        long sy = Math.floorDiv(pos.getLongY(), 16L);
        long sz = Math.floorDiv(pos.getLongZ(), 16L);
        int dx = (int) (sx - base.lx);
        int dy = (int) (sy - base.ly);
        int dz = (int) (sz - base.lz);
        int index = (dx + 1) + (dy + 1) * 3 + (dz + 1) * 9;
        if (index < 0 || index >= copies.length || copies[index] == null) {
            if (empty == null) {
                empty = FarRegionBuilder.emptyCopy(level);
            }
            return empty;
        }
        return copies[index];
    }
}
