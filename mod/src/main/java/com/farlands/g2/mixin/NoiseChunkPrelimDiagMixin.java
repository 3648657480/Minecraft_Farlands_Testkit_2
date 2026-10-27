package com.farlands.g2.mixin;

import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Diagnostic (-Dfarlands.diag.prelimq): print every preliminary-surface query
 * for the seam chunk columns (wrapped x -2147483632..-2147483617, z 288..304)
 * together with the querying NoiseChunk's wide origin and the result.
 */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkPrelimDiagMixin {

    @Shadow private long farlands$originX;
    @Shadow private long farlands$originZ;

    @Unique
    private static final AtomicInteger FARLANDS$PRELIMQ =
        Boolean.getBoolean("farlands.diag.prelimq") ? new AtomicInteger() : null;

    @Inject(method = "preliminarySurfaceLevel", at = @At("RETURN"))
    private void farlands$logPrelimQuery(int x, int z, CallbackInfoReturnable<Integer> cir) {
        if (FARLANDS$PRELIMQ == null || x < -2147483632 || x > -2147483617
            || z < 288 || z > 304) {
            return;
        }
        if (FARLANDS$PRELIMQ.incrementAndGet() > 200) {
            return;
        }
        System.out.println("[PRELIMQ] origin=" + this.farlands$originX + "," + this.farlands$originZ
            + " x=" + x + " z=" + z + " -> " + cir.getReturnValue());
        System.out.flush();
    }
}
