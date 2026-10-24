package com.farlands.g2.mixin;

import com.farlands.g2.runtime.VerticalBounds;
import java.lang.reflect.Field;
import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Makes the rotating grid usable at far Y.
 *
 * <p>The storage's Y range is fixed at construction from the dimension height
 * (e.g. -32..31 sections) and its lookups decode the packed long's 6-bit Y, so
 * at a moved window only sections whose Y happens to land inside that range are
 * found. That silently disabled the client's {@code SectionUpdateTracker} at
 * far Y (EXPERIMENTS 搂89), so nothing was ever marked dirty/compiled.</p>
 *
 * <p>When the centre is far Y, the grid uses a <b>band base</b> (centre minus
 * half the grid height) instead of {@code minY}, and lookups map Y through that
 * base. The 6-bit long view is matched modulo 64, which is enough because only
 * one vertical band is loaded at a time. Normal worlds keep the vanilla path.</p>
 */
@Mixin(RotatingSectionStorage.class)
public abstract class RotatingSectionStorageBandMixin {

    @Shadow @Final private int minY;
    @Shadow @Final private int maxY;
    @Shadow @Final private int sectionGridSizeY;
    @Shadow @Final private int sectionGridSizeXZ;
    @Shadow @Final private int radius;
    @Shadow private SectionPos centerSectionPos;

    @Unique private int farlands$base = Integer.MIN_VALUE;
    @Unique private static Field farlands$nodesField;
    @Unique private static Field farlands$valueField;

    @Shadow private int getSectionIndex(int x, int y, int z) {
        throw new AssertionError();
    }

    @Unique
    private Object[] farlands$nodes() {
        try {
            if (farlands$nodesField == null) {
                farlands$nodesField = RotatingSectionStorage.class.getDeclaredField("nodes");
                farlands$nodesField.setAccessible(true);
            }
            return (Object[]) farlands$nodesField.get(this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Unique
    private Object farlands$nodeValue(Object node) {
        try {
            if (farlands$valueField == null) {
                farlands$valueField = node.getClass().getDeclaredField("value");
                farlands$valueField.setAccessible(true);
            }
            return farlands$valueField.get(node);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Unique
    private RotatingSectionStorage.Value farlands$valueAt(int x, int y, int z) {
        return (RotatingSectionStorage.Value) farlands$nodeValue(
            farlands$nodes()[getSectionIndex(x, y, z)]);
    }

    @Overwrite
    public boolean repositionCenter(SectionPos newCenterSectionPos) {
        if (newCenterSectionPos.equals(this.centerSectionPos)) {
            return false;
        }
        int base = Integer.MIN_VALUE;
        if (Math.abs(newCenterSectionPos.y()) > 1_000_000
            && (newCenterSectionPos.y() < this.minY || newCenterSectionPos.y() > this.maxY)) {
            // Only for grids whose Y range is the dimension's (the client's update
            // tracker). The ViewArea grid is already built around the window band.
            base = newCenterSectionPos.y() - (this.sectionGridSizeY - 1) / 2;
            int maxPlayable = VerticalBounds.MAX_PLAYABLE_SECTION;
            int minPlayable = VerticalBounds.MIN_PLAYABLE_SECTION;
            if (base + this.sectionGridSizeY - 1 > maxPlayable) {
                base = maxPlayable - this.sectionGridSizeY + 1;
            }
            if (base < minPlayable) {
                base = minPlayable;
            }
        }
        this.farlands$base = base;

        int lowestX = newCenterSectionPos.x() - this.radius;
        int lowestZ = newCenterSectionPos.z() - this.radius;
        for (int gridX = 0; gridX < this.sectionGridSizeXZ; gridX++) {
            int newSectionX = lowestX + Math.floorMod(gridX - lowestX, this.sectionGridSizeXZ);
            for (int gridZ = 0; gridZ < this.sectionGridSizeXZ; gridZ++) {
                int newSectionZ = lowestZ + Math.floorMod(gridZ - lowestZ, this.sectionGridSizeXZ);
                for (int gridY = 0; gridY < this.sectionGridSizeY; gridY++) {
                    int newSectionY = (base == Integer.MIN_VALUE ? this.minY : base) + gridY;
                    RotatingSectionStorage.Value value = farlands$valueAt(gridX, gridY, gridZ);
                    long target = SectionPos.asLong(newSectionX, newSectionY, newSectionZ);
                    if (value.getSectionNode() != target) {
                        value.setSectionNode(target);
                    }
                    // This @Overwrite replaces the method the separate key-assignment
                    // inject targets, so assign the wide key here as well.
                    if (value instanceof com.farlands.g2.runtime.section.RenderSectionKeyAccessor accessor) {
                        com.farlands.g2.runtime.section.SectionKey key =
                            com.farlands.g2.runtime.section.SectionKey.of(
                                newSectionX, newSectionY, newSectionZ);
                        if (!key.equals(accessor.farlands$getKey())) {
                            accessor.farlands$setKey(key);
                        } else {
                            com.farlands.g2.client.RenderKeys.put(key,
                                (net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection) value);
                        }
                    }
                }
            }
        }
        this.centerSectionPos = newCenterSectionPos;
        return true;
    }

    @Overwrite
    private boolean containsSection(int sectionX, int sectionY, int sectionZ) {
        int yMin = this.farlands$base == Integer.MIN_VALUE ? this.minY : this.farlands$base;
        int yMax = this.farlands$base == Integer.MIN_VALUE ? this.maxY
            : this.farlands$base + this.sectionGridSizeY - 1;
        if (sectionY < yMin || sectionY > yMax) {
            return false;
        }
        return sectionX < this.centerSectionPos.x() - this.radius
            || sectionX > this.centerSectionPos.x() + this.radius
            ? false
            : sectionZ >= this.centerSectionPos.z() - this.radius
                && sectionZ <= this.centerSectionPos.z() + this.radius;
    }

    @Overwrite
    public RotatingSectionStorage.Value getValue(int sectionX, int sectionY, int sectionZ) {
        if (!this.containsSection(sectionX, sectionY, sectionZ)) {
            return null;
        }
        int base = this.farlands$base == Integer.MIN_VALUE ? this.minY : this.farlands$base;
        int y = sectionY - base;
        int x = Math.floorMod(sectionX, this.sectionGridSizeXZ);
        int z = Math.floorMod(sectionZ, this.sectionGridSizeXZ);
        return farlands$valueAt(x, y, z);
    }

    @Overwrite
    public RotatingSectionStorage.Value getValue(long sectionNode) {
        int sx = SectionPos.x(sectionNode);
        int sz = SectionPos.z(sectionNode);
        if (this.farlands$base != Integer.MIN_VALUE) {
            int y6 = SectionPos.y(sectionNode) & 63;
            for (int gy = 0; gy < this.sectionGridSizeY; gy++) {
                if (((this.farlands$base + gy) & 63) == y6) {
                    return this.getValue(sx, this.farlands$base + gy, sz);
                }
            }
            return null;
        }
        return this.getValue(sx, SectionPos.y(sectionNode), sz);
    }

    @Overwrite
    public RotatingSectionStorage.Value getValueAt(BlockPos pos) {
        return this.getValue(SectionPos.asLong(pos));
    }
}
