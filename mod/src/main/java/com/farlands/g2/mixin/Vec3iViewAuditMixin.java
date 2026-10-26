package com.farlands.g2.mixin;

import com.farlands.g2.runtime.Narrowing;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * W-1 audit: any caller that reads the int view of a wide position whose real
 * value does not fit int is a narrowing site. Vec3i is the base of BlockPos, so
 * this catches every consumer that still thinks in int block coordinates.
 *
 * <p>The check reads the backing long fields directly: calling getX() from a
 * HEAD injection on getX() would recurse.</p>
 */
@Mixin(Vec3i.class)
public class Vec3iViewAuditMixin {

    @Shadow
    private long x;
    @Shadow
    private long y;
    @Shadow
    private long z;

    @Inject(method = "getX", at = @At("HEAD"))
    private void farlands$auditX(CallbackInfoReturnable<Integer> cir) {
        if ((long) (int) x != x) {
            Narrowing.warn("Vec3i.getX()");
        }
    }

    @Inject(method = "getY", at = @At("HEAD"))
    private void farlands$auditY(CallbackInfoReturnable<Integer> cir) {
        if ((long) (int) y != y) {
            Narrowing.warn("Vec3i.getY()");
        }
    }

    @Inject(method = "getZ", at = @At("HEAD"))
    private void farlands$auditZ(CallbackInfoReturnable<Integer> cir) {
        if ((long) (int) z != z) {
            Narrowing.warn("Vec3i.getZ()");
        }
    }
}
