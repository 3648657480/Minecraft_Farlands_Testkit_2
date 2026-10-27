package com.farlands.g2.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Only the int {@link BoundingBox} frustum test needs a far-coordinate bypass:
 * its int fields cannot express the seam. The double AABB/point tests already do
 * camera-relative double math in 26.2, so they are left untouched (bypassing
 * them disabled culling entirely and made every camera rotation recompile the
 * whole view area).
 */
@Mixin(Frustum.class)
public class FrustumMixin {
    @Unique private static int bbHits;

    @Inject(method = "cubeInFrustum(Lnet/minecraft/world/level/levelgen/structure/BoundingBox;)I",
            at = @At("HEAD"), cancellable = true)
    private void bypassCubeBB(BoundingBox bb, CallbackInfoReturnable<Integer> cir) {
        if (Math.abs(bb.minX()) > 30000000 || Math.abs(bb.maxX()) > 30000000
            || Math.abs(bb.minZ()) > 30000000 || Math.abs(bb.maxZ()) > 30000000) {
            ++bbHits;
            if (bbHits % 200 == 0) System.out.println("[D1] FrustumBB bypass hits=" + bbHits);
            cir.setReturnValue(-2);
        }
    }
}