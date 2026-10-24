package com.farlands.g2.mixin;

import com.farlands.g2.client.RenderKeys;
import com.farlands.g2.runtime.section.RenderSectionKeyAccessor;
import com.farlands.g2.runtime.section.SectionKey;
import java.util.Iterator;
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
 * rotating grid is re-centred. The coordinates come straight from the grid maths
 * (never from the packed long), so far-Y sections get their true absolute Y.
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

    @Inject(method = "repositionCenter", at = @At("TAIL"))
    private void farlands$assignKeys(SectionPos newCenter, CallbackInfoReturnable<Boolean> cir) {
        if (newCenter.equals(this.farlands$lastCenter)) {
            return; // same grid origin: keys cannot have changed
        }
        this.farlands$lastCenter = newCenter;
        int gridXZ = this.sectionGridSizeXZ;
        int gridY = this.sectionGridSizeY;
        int lowestX = newCenter.x() - this.radius;
        int lowestZ = newCenter.z() - this.radius;
        int i = 0;
        Iterator<?> it = ((Iterable<?>) (Object) this).iterator();
        while (it.hasNext()) {
            Object value = it.next();
            if (value instanceof RenderSectionKeyAccessor accessor) {
                int x = i % gridXZ;
                int rest = i / gridXZ;
                int y = rest % gridY;
                int z = rest / gridY;
                int worldX = lowestX + Math.floorMod(x - lowestX, gridXZ);
                int worldZ = lowestZ + Math.floorMod(z - lowestZ, gridXZ);
                SectionKey key = SectionKey.of(worldX, this.minY + y, worldZ);
                if (!key.equals(accessor.farlands$getKey())) {
                    accessor.farlands$setKey(key);
                } else if (value instanceof net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection section) {
                    RenderKeys.put(key, section);
                }
            }
            i++;
        }
    }
}
