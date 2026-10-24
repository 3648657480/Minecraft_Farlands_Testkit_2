package com.farlands.g2.mixin;

import com.farlands.g2.client.RenderKeys;
import com.farlands.g2.runtime.section.RenderSectionKeyAccessor;
import com.farlands.g2.runtime.section.SectionKey;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

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

    @Override
    public SectionKey farlands$getKey() {
        return this.farlands$key;
    }

    @Override
    public void farlands$setKey(SectionKey key) {
        this.farlands$key = key;
        if (key == null) {
            return;
        }
        this.reset();
        int x = key.x << 4;
        int y = key.y << 4;
        int z = key.z << 4;
        this.renderOrigin.set(x, y, z);
        this.bb = new AABB(x, y, z, x + 16.0, y + 16.0, z + 16.0);
        RenderKeys.put(key, (SectionRenderDispatcher.RenderSection) (Object) this);
        // reset() dropped the mesh; mark the section dirty so the compile pipeline
        // picks it up again (first assignment and band moves both need this).
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && mc.level != null) {
            mc.level.setSectionDirtyWithNeighbors(key.x, key.y, key.z);
        }
    }
}
