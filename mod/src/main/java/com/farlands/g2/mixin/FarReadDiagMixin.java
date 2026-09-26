package com.farlands.g2.mixin;

import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.server.level.WorldGenRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Diagnostic (G2 2^31 investigation), OFF by default: when
 * {@code -Dfarlands.diag.farread=true}, the first time vanilla is about to log
 * "unsafe terrain read" for a chunk far from the generation origin
 * (|chunk| &gt; 1e6), dump the generation origin, write radius and the caller
 * stack - this is how the un-widened consumer was located. The vanilla per-read
 * warning is also suppressed while enabled, so a wrapped-coordinate storm does
 * not flood the log.
 */
@Mixin(WorldGenRegion.class)
public class FarReadDiagMixin {

    private static final boolean farlands$ENABLED =
        Boolean.getBoolean("farlands.diag.farread");

    private static final AtomicBoolean farlands$logged = new AtomicBoolean();

    @Shadow private int centerChunkX;
    @Shadow private int centerChunkZ;
    @Shadow private int writeRadius;

    private static final long farlands$FAR = 1_000_000L;

    @Inject(method = "warnIfReadOutsideWriteZone(II)V", at = @At("HEAD"), cancellable = true)
    private void farlands$diagFarRead(int x, int z, CallbackInfo ci) {
        if (!farlands$ENABLED) {
            return;
        }
        ci.cancel(); // suppress the vanilla per-read spam (we only want our one-time dump)
        if (Math.abs((long) x) <= farlands$FAR && Math.abs((long) z) <= farlands$FAR) {
            return;
        }
        if (!farlands$logged.compareAndSet(false, true)) {
            return;
        }
        System.out.println("[FarLands-G2][diag] first FAR unsafe terrain read chunk=(" + x + "," + z
            + ") center=(" + this.centerChunkX + "," + this.centerChunkZ
            + ") writeRadius=" + this.writeRadius);
        new Throwable("far-read-caller").printStackTrace(System.out);
        System.out.flush();
    }
}
