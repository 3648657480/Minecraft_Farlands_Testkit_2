package com.farlands.g2.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Diagnostic (-Dfarlands.diag.filldiag): print the interpolated density and the
 * block-state rule result for one probe voxel of the seam chunk 134217729.
 * Tells whether solid stone above the density surface comes from density > 0
 * or from the surface-rule filler.
 */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkFillDiagMixin {

    @Shadow private int cellStartBlockX;
    @Shadow private int cellStartBlockY;
    @Shadow private int cellStartBlockZ;
    @Shadow private int inCellX;
    @Shadow private int inCellY;
    @Shadow private int inCellZ;
    @Shadow private long farlands$originX;

    @Unique
    private static final boolean FARLANDS$FILLDIAG = Boolean.getBoolean("farlands.diag.filldiag");

    @Unique
    private static final AtomicBoolean FARLANDS$FILLDIAG_DONE = new AtomicBoolean();

    @Unique
    private boolean farlands$fillProbe() {
        if (!FARLANDS$FILLDIAG || this.farlands$originX != 2147483664L) {
            return false;
        }
        return this.cellStartBlockX + this.inCellX == -2147483632
            && this.cellStartBlockY + this.inCellY == 90
            && this.cellStartBlockZ + this.inCellZ == 293;
    }

    @Inject(method = "getInterpolatedDensity", at = @At("RETURN"))
    private void farlands$logDensity(CallbackInfoReturnable<Double> cir) {
        if (!farlands$fillProbe() || !FARLANDS$FILLDIAG_DONE.compareAndSet(false, true)) {
            return;
        }
        System.out.println("[FILLDIAG] density at (-2147483632,90,293) = " + cir.getReturnValue()
            + " cellStart=" + this.cellStartBlockX + "," + this.cellStartBlockY + ","
            + this.cellStartBlockZ + " inCell=" + this.inCellX + "," + this.inCellY + ","
            + this.inCellZ);
        System.out.flush();
    }

    @Inject(method = "getInterpolatedState", at = @At("RETURN"))
    private void farlands$logState(CallbackInfoReturnable<BlockState> cir) {
        if (!farlands$fillProbe()) {
            return;
        }
        System.out.println("[FILLDIAG] state at (-2147483632,90,293) = " + cir.getReturnValue());
        System.out.flush();
    }
}
