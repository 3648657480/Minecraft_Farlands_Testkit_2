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
 * <p>The check reads the backing limbs directly: calling getX() from a HEAD
 * injection on getX() would recurse. {@code xLo/yLo/zLo} are the low 64-bit
 * limbs added by {@code Vec3iInline128Patch}; a value is only int-exact when
 * the low limb fits an int <em>and</em> the high limb is its sign extension
 * (i.e. the value really fits 64 bits).</p>
 */
@Mixin(Vec3i.class)
public class Vec3iViewAuditMixin {

    @Shadow
    private long xLo;
    @Shadow
    private long xHi;
    @Shadow
    private long yLo;
    @Shadow
    private long yHi;
    @Shadow
    private long zLo;
    @Shadow
    private long zHi;

    @Inject(method = "getX", at = @At("HEAD"))
    private void farlands$auditX(CallbackInfoReturnable<Integer> cir) {
        if ((long) (int) xLo != xLo || xHi != (xLo >> 63)) {
            Narrowing.warn("Vec3i.getX()");
        }
    }

    @Inject(method = "getY", at = @At("HEAD"))
    private void farlands$auditY(CallbackInfoReturnable<Integer> cir) {
        if ((long) (int) yLo != yLo || yHi != (yLo >> 63)) {
            Narrowing.warn("Vec3i.getY()");
        }
    }

    @Inject(method = "getZ", at = @At("HEAD"))
    private void farlands$auditZ(CallbackInfoReturnable<Integer> cir) {
        if ((long) (int) zLo != zLo || zHi != (zLo >> 63)) {
            Narrowing.warn("Vec3i.getZ()");
        }
    }
}
