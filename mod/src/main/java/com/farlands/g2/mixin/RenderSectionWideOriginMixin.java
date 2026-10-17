package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M3: the render section origin and culling box are derived from
 * SectionPos.sectionToBlockCoord(int), i.e. section*16 in the int domain.
 * Section 2^27 (block 2^31) wraps to Integer.MIN_VALUE, so sections east of
 * the 2^31 seam get their origin/AABB 4.29G blocks away and are frustum-culled
 * (chunks visible on the server but not rendered). Recompute both in the long
 * domain; at normal coordinates the values are identical.
 */
@Mixin(targets = "net/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection")
public abstract class RenderSectionWideOriginMixin {

    @Shadow private BlockPos.MutableBlockPos renderOrigin;

    @Shadow private AABB bb;

    @Inject(method = "setSectionNode", at = @At("TAIL"))
    private void farlands$wideOrigin(long sectionNode, CallbackInfo ci) {
        long x = (long) SectionPos.x(sectionNode) * 16L;
        long y = (long) SectionPos.y(sectionNode) * 16L;
        long z = (long) SectionPos.z(sectionNode) * 16L;
        this.renderOrigin.set(x, y, z);
        this.bb = new AABB((double) x, (double) y, (double) z,
            (double) (x + 16L), (double) (y + 16L), (double) (z + 16L));
    }
}
