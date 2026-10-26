package com.farlands.g2.mixin;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * X1b: resolve the render section from the long block position. The int BlockPos
 * view wraps at 2^31, so the old lookup returned null (no camera section) east
 * of the seam.
 */
@Mixin(ViewArea.class)
public abstract class ViewAreaWideLookupMixin {

    @Shadow
    protected abstract SectionRenderDispatcher.RenderSection getRenderSection(long node);

    @Inject(method = "getRenderSectionAt", at = @At("HEAD"), cancellable = true)
    private void farlands$wideLookup(BlockPos pos, CallbackInfoReturnable<SectionRenderDispatcher.RenderSection> cir) {
        long lx = pos.getLongX();
        long ly = pos.getLongY();
        long lz = pos.getLongZ();
        if (lx == (long) pos.getX() && ly == (long) pos.getY() && lz == (long) pos.getZ()) {
            return; // int views are exact: vanilla path
        }
        long sx = Math.floorDiv(lx, 16L);
        long sy = Math.floorDiv(ly, 16L);
        long sz = Math.floorDiv(lz, 16L);
        int ix = (int) sx;
        int iy = (int) sy;
        int iz = (int) sz;
        if (sx != (long) ix || sy != (long) iy || sz != (long) iz) {
            cir.setReturnValue(null); // beyond int section space: not addressable yet
            return;
        }
        cir.setReturnValue(this.getRenderSection(SectionPos.asLong(ix, iy, iz)));
    }
}
