package com.farlands.g2.mixin;

import com.farlands.g2.client.BandState;
import net.minecraft.client.SectionUpdateTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * The compile gate {@code hasAllNeighbors} also requires "light on in column" at
 * the section's zero (y=0) node. Moved windows skip light initialisation, so at
 * far Y that light query never succeeds and no section can ever be compiled
 * (EXPERIMENTS §91). When a far band is active, accept the neighbours (the
 * section's content is gated by the chunk window itself).
 */
@Mixin(SectionUpdateTracker.class)
public abstract class SectionUpdateTrackerFarMixin {

    @Shadow private boolean doesChunkExistAt(ClientLevel level, long sectionNode) {
        throw new AssertionError();
    }

    @Overwrite
    public boolean hasAllNeighbors(ClientLevel level, long sectionNode) {
        if (BandState.far()) {
            return true;
        }
        return this.doesChunkExistAt(level, SectionPos.offset(sectionNode, Direction.WEST))
            && this.doesChunkExistAt(level, SectionPos.offset(sectionNode, Direction.NORTH))
            && this.doesChunkExistAt(level, SectionPos.offset(sectionNode, Direction.EAST))
            && this.doesChunkExistAt(level, SectionPos.offset(sectionNode, Direction.SOUTH))
            && this.doesChunkExistAt(level, SectionPos.offset(sectionNode, -1, 0, -1))
            && this.doesChunkExistAt(level, SectionPos.offset(sectionNode, -1, 0, 1))
            && this.doesChunkExistAt(level, SectionPos.offset(sectionNode, 1, 0, -1))
            && this.doesChunkExistAt(level, SectionPos.offset(sectionNode, 1, 0, 1));
    }
}
