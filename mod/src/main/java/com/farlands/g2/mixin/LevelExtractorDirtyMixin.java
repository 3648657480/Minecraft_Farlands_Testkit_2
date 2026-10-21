package com.farlands.g2.mixin;

import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Overwrite;

/** Dirty-section ranges must not overflow their int loop bounds at block 2^31. */
@Mixin(LevelExtractor.class)
public class LevelExtractorDirtyMixin {

    @Shadow private void setSectionDirty(int sectionX, int sectionY, int sectionZ, boolean playerChanged) {
        throw new AssertionError();
    }

    @Shadow private void setSectionDirty(int sectionX, int sectionY, int sectionZ) {
        throw new AssertionError();
    }

    /**
     * @author G2
     * @reason int pos.getZ()+-1 wraps at the boundary, turning the 3x3 loop into a
     *         full-int-range loop and freezing the render thread.
     */
    @Overwrite
    private void setBlockDirty(BlockPos pos, boolean playerChanged) {
        long z0 = (long) pos.getZ() - 1L;
        long z1 = (long) pos.getZ() + 1L;
        long x0 = (long) pos.getX() - 1L;
        long x1 = (long) pos.getX() + 1L;
        long y0 = (long) pos.getY() - 1L;
        long y1 = (long) pos.getY() + 1L;
        for (long z = z0; z <= z1; z++) {
            for (long x = x0; x <= x1; x++) {
                for (long y = y0; y <= y1; y++) {
                    this.setSectionDirty(
                        SectionPos.blockToSectionCoord((int) x),
                        SectionPos.blockToSectionCoord((int) y),
                        SectionPos.blockToSectionCoord((int) z),
                        playerChanged
                    );
                }
            }
        }
    }

    @Overwrite
    public void setBlocksDirty(int x0, int y0, int z0, int x1, int y1, int z1) {
        long lz0 = (long) z0 - 1L;
        long lz1 = (long) z1 + 1L;
        long lx0 = (long) x0 - 1L;
        long lx1 = (long) x1 + 1L;
        long ly0 = (long) y0 - 1L;
        long ly1 = (long) y1 + 1L;
        for (long z = lz0; z <= lz1; z++) {
            for (long x = lx0; x <= lx1; x++) {
                for (long y = ly0; y <= ly1; y++) {
                    this.setSectionDirty(
                        SectionPos.blockToSectionCoord((int) x),
                        SectionPos.blockToSectionCoord((int) y),
                        SectionPos.blockToSectionCoord((int) z)
                    );
                }
            }
        }
    }
}
