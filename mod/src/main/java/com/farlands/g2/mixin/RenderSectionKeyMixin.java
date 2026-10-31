package com.farlands.g2.mixin;

import com.farlands.g2.client.RenderKeys;
import com.farlands.g2.runtime.section.RenderSectionKeyAccessor;
import com.farlands.g2.runtime.section.SectionKey;
import com.farlands.g2.runtime.section.WideOriginHolder;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the 2560-bit {@link SectionKey} identity to a render section. The origin
 * and bounding box are recomputed from the key's int coordinates, so far-Y
 * sections no longer depend on the 6-bit Y packing of {@code SectionPos.asLong}.
 */
@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection")
public abstract class RenderSectionKeyMixin implements RenderSectionKeyAccessor {

    @Shadow private BlockPos.MutableBlockPos renderOrigin;
    @Shadow private AABB bb;
    @Shadow public abstract void reset();
    @Shadow public abstract long getSectionNode();

    @Unique private SectionKey farlands$key;
    @Unique private long farlands$wideOriginX;
    @Unique private long farlands$wideOriginY;
    @Unique private long farlands$wideOriginZ;

    @Override
    public SectionKey farlands$getKey() {
        return this.farlands$key;
    }

    @Override
    public long farlands$wideOriginX() {
        return this.farlands$wideOriginX;
    }

    @Override
    public long farlands$wideOriginY() {
        return this.farlands$wideOriginY;
    }

    @Override
    public long farlands$wideOriginZ() {
        return this.farlands$wideOriginZ;
    }

    @Inject(method = "setSectionNode", at = @At("HEAD"))
    private void farlands$dropOldNode(long sectionNode, CallbackInfo ci) {
        RenderKeys.remove(this.getSectionNode());
    }

    @Override
    public void farlands$setKey(SectionKey key) {
        this.farlands$key = key;
        if (key == null) {
            return;
        }
        this.reset();
        // X/Z long->double, following the X/Z render solution: the AABB carries the
        // exact position; the int origin is only a legacy view.
        long lx = key.lx << 4;
        long ly = key.ly << 4;
        long lz = key.lz << 4;
        this.farlands$wideOriginX = lx;
        this.farlands$wideOriginY = ly;
        this.farlands$wideOriginZ = lz;
        boolean far = lx != (long) (int) lx || ly != (long) (int) ly
            || lz != (long) (int) lz;
        if (far && this instanceof WideOriginHolder holder) {
            holder.g2SetWideOrigin(lx, ly, lz);
        }
        this.renderOrigin.set((int) lx, (int) ly, (int) lz);
        this.bb = new AABB((double) lx, (double) ly, (double) lz,
            (double) (lx + 16L), (double) (ly + 16L), (double) (lz + 16L));
        RenderKeys.put(key, (SectionRenderDispatcher.RenderSection) (Object) this);
        // reset() dropped the mesh; mark only this section dirty. Marking the whole
        // 3x3x3 neighbourhood here floods the queue whenever the camera moves and
        // leaves the view blank until every round of recompiles finishes.
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && mc.level != null) {
            mc.level.setSectionRangeDirty(key.x, key.y, key.z, key.x, key.y, key.z);
        }
    }
}
