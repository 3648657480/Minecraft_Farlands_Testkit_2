package com.farlands.g2.mixin;

import com.farlands.g2.client.RenderKeys;
import com.farlands.g2.runtime.section.RenderSectionKeyAccessor;
import com.farlands.g2.runtime.section.SectionKey;
import com.farlands.g2.runtime.section.WideSectionAnchor;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Objects;
import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Assigns the 2560-bit {@link SectionKey} to every render section whenever the
 * rotating grid is re-centred.
 *
 * <p>The grid works in a <b>local</b> frame (camera section minus a fixed base,
 * see {@code WideRenderBase}), so the packed node value in this loop is a local
 * section. The world identity is {@code key = base + localNode}: the anchor is
 * the base and never moves with the camera, so a slot that keeps its node keeps
 * its key, and a slot that rotates follows its world cell by the node delta.
 * Keys therefore track the camera through the grid rotation instead of being
 * re-anchored every chunk.</p>
 *
 * <p>{@code repositionCenter} runs every frame when the camera moves, so a node
 * whose key is unchanged is skipped: setting the key resets the mesh (cancels
 * compile tasks), which must only happen when the node really moved.</p>
 */
@Mixin(RotatingSectionStorage.class)
public abstract class RotatingSectionStorageKeyMixin {

    @Shadow @Final private int radius;
    @Shadow @Final private int minY;
    @Shadow @Final private int sectionGridSizeY;
    @Shadow @Final private int sectionGridSizeXZ;

    @Unique private SectionPos farlands$lastCenter;
    @Unique private BigInteger farlands$lastBaseX;
    @Unique private BigInteger farlands$lastBaseZ;
    @Unique private boolean farlands$loggedWideKey;
    @Unique private static int farlands$keyLogs;

    @Inject(method = "repositionCenter", at = @At("RETURN"))
    private void farlands$assignKeys(SectionPos newCenter, CallbackInfoReturnable<Boolean> cir) {
        // The base owns the world origin of this grid's local frame. It is read
        // straight from the frame (not from the storage) so a rebuilt ViewArea
        // cannot lose it; only a (re-)base makes the keys stale.
        BigInteger anchorX = com.farlands.g2.client.WideRenderBase.established()
            ? com.farlands.g2.client.WideRenderBase.baseX() : null;
        BigInteger anchorZ = com.farlands.g2.client.WideRenderBase.established()
            ? com.farlands.g2.client.WideRenderBase.baseZ() : null;
        if (newCenter.equals(this.farlands$lastCenter)
            && Objects.equals(anchorX, this.farlands$lastBaseX)
            && Objects.equals(anchorZ, this.farlands$lastBaseZ)) {
            return; // same grid origin + base: keys cannot have changed
        }
        this.farlands$lastCenter = newCenter;
        this.farlands$lastBaseX = anchorX;
        this.farlands$lastBaseZ = anchorZ;
        long keyBefore = com.farlands.g2.client.RenderResetStats.keySets();
        int i = 0;
        Iterator<?> it = ((Iterable<?>) (Object) this).iterator();
        while (it.hasNext()) {
            Object value = it.next();
            if (value instanceof RenderSectionKeyAccessor accessor
                && value instanceof net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection renderSection) {
                long node = renderSection.getSectionNode();
                int worldX = SectionPos.x(node);
                int worldY = SectionPos.y(node);
                int worldZ = SectionPos.z(node);
                BigInteger keyX = anchorX == null ? BigInteger.valueOf(worldX)
                    : anchorX.add(BigInteger.valueOf(worldX));
                BigInteger keyZ = anchorZ == null ? BigInteger.valueOf(worldZ)
                    : anchorZ.add(BigInteger.valueOf(worldZ));
                SectionKey oldKey = accessor.farlands$getKey();
                long oldNode = accessor.farlands$keyNode();
                boolean oldFar = oldKey != null
                    && (oldKey.lx != (long) (int) oldKey.lx
                        || oldKey.ly != (long) (int) oldKey.ly
                        || oldKey.lz != (long) (int) oldKey.lz);
                SectionKey key;
                if (oldFar && oldNode == node) {
                    // Same slot: keep world identity even if the anchor moved.
                    RenderKeys.put(oldKey, renderSection);
                    continue;
                } else if (oldFar) {
                    // The slot rotated: follow the world cell by the node delta.
                    key = oldKey.offset(
                        worldX - SectionPos.x(oldNode),
                        worldY - SectionPos.y(oldNode),
                        worldZ - SectionPos.z(oldNode));
                } else {
                    key = SectionKey.of(keyX, BigInteger.valueOf(worldY), keyZ);
                }
                if (Boolean.getBoolean("farlands.diag.origin") && i < 2
                    && farlands$keyLogs++ < 12) {
                    System.out.println("[KEYASSIGN] center=" + newCenter
                        + " anchor=" + anchorX + "," + anchorZ
                        + " world=" + worldX + "," + worldZ + " key=" + key);
                    System.out.flush();
                }
                if (!farlands$loggedWideKey && anchorX != null
                    && anchorX.abs().bitLength() > 40) {
                    farlands$loggedWideKey = true;
                    System.out.println("[WIDEKEY] anchor=" + anchorX + "," + anchorZ
                        + " center=" + newCenter + " key=" + key);
                    System.out.flush();
                }
                if (!key.equals(accessor.farlands$getKey())) {
                    if (i < 3 && Boolean.getBoolean("farlands.diag.origin")) {
                        System.out.println("[KEYDIFF] i=" + i
                            + " node=" + node + " local=" + worldX + "," + worldY + "," + worldZ
                            + " old=" + accessor.farlands$getKey() + " new=" + key);
                        System.out.flush();
                    }
                    accessor.farlands$setKey(key);
                }
                // Always register, not just on the "unchanged" path: the first pass
                // sets the key (RenderKeys was empty) and repositionCenter then
                // short-circuits on an unchanged centre, so the registry would stay
                // empty forever and every far lookup would miss.
                RenderKeys.put(key, renderSection);
            }
            i++;
        }
        long keyChanged = com.farlands.g2.client.RenderResetStats.keySets() - keyBefore;
        if (keyChanged > 0) {
            System.out.println("[KEYREKEY] center=" + newCenter
                + " anchor=" + anchorX + "," + anchorZ
                + " changed=" + keyChanged);
            System.out.flush();
        }
    }
}
