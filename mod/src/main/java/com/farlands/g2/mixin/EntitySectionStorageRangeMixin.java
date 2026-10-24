package com.farlands.g2.mixin;

import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Y-E: {@code EntitySectionStorage} keeps section ids in a sorted set and queries
 * them with {@code subSet(from, to)}. The packed section key only has 6 Y bits,
 * so at a moved vertical band the numeric order of the two bounds can invert
 * ("start element > end element", EXPERIMENTS §82).
 *
 * <p>When the bounds are ordered (every normal world) the original fast path is
 * used unchanged. When they invert, fall back to filtering the whole set by the
 * section's X (and Z for the per-chunk query), which the callers would have
 * applied anyway.</p>
 */
@Mixin(EntitySectionStorage.class)
public class EntitySectionStorageRangeMixin {

    @Shadow @Final private LongSortedSet sectionIds;

    @Redirect(method = "forEachAccessibleNonEmptySection",
        at = @At(value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/longs/LongSortedSet;subSet(JJ)Lit/unimi/dsi/fastutil/longs/LongSortedSet;"))
    private LongSortedSet farlands$safeSubsetRange(LongSortedSet set, long from, long to) {
        if (from <= to) {
            return set.subSet(from, to);
        }
        LongAVLTreeSet out = new LongAVLTreeSet();
        int x = SectionPos.x(from);
        for (long key : this.sectionIds) {
            if (SectionPos.x(key) == x) {
                out.add(key);
            }
        }
        return out;
    }

    @Redirect(method = "getChunkSections",
        at = @At(value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/longs/LongSortedSet;subSet(JJ)Lit/unimi/dsi/fastutil/longs/LongSortedSet;"))
    private LongSortedSet farlands$safeSubsetChunk(LongSortedSet set, long from, long to) {
        if (from <= to) {
            return set.subSet(from, to);
        }
        LongAVLTreeSet out = new LongAVLTreeSet();
        int x = SectionPos.x(from);
        int z = SectionPos.z(from);
        for (long key : this.sectionIds) {
            if (SectionPos.x(key) == x && SectionPos.z(key) == z) {
                out.add(key);
            }
        }
        return out;
    }
}
