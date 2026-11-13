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
        // With the local frame established the view grid is keyed by LOCAL sections
        // (worldSection - base). At block 2^31 the world section still fits an int
        // (134217730), so the old "only when the section exceeds int" branch never
        // fired and the lookup used the world section against a local-keyed table:
        // getRenderSectionAt returned null for the camera itself (observed at
        // x=2147483689, z=9990: camSection=absent while viewC=2,624).
        SectionPos local = com.farlands.g2.client.WideRenderBase.localSectionOf(lx, ly, lz);
        if (local != null) {
            cir.setReturnValue(this.getRenderSection(local.asLong()));
            return;
        }
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
            return; // beyond int section space: let the 2560-key path resolve it
        }
        cir.setReturnValue(this.getRenderSection(SectionPos.asLong(ix, iy, iz)));
    }
}
