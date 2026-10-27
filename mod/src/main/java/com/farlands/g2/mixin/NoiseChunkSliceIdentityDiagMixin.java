package com.farlands.g2.mixin;

import com.farlands.g2.runtime.NoiseChunkDiagAccess;
import com.farlands.g2.runtime.NoiseInterpolatorDiagAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Diagnostic (-Dfarlands.diag.sliceid): trace the cell-slice bookkeeping of the
 * seam chunk (originX = 2^31+16, originZ = 288) - {@code fillSlice} (which cell
 * was written), {@code advanceCellX} (int vs long cell start before/after), and
 * {@code selectCellYZ} (which cell is selected for the corners, with the slice
 * values it will hand to the interpolator and a same-coordinate raw value).
 * Read-only.
 */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkSliceIdentityDiagMixin {

    @Shadow private int cellStartBlockX;
    @Shadow private int cellStartBlockZ;
    @Shadow private int cellStartBlockY;
    @Shadow private long farlands$originX;
    @Shadow private long farlands$cellStartBlockXLong;
    @Shadow private long farlands$cellStartBlockZLong;
    @Shadow private List interpolators;

    @Unique
    private static final boolean FARLANDS$SLICEID = Boolean.getBoolean("farlands.diag.sliceid");

    @Unique
    private static final AtomicInteger FARLANDS$N = new AtomicInteger();

    @Unique
    private boolean farlands$on() {
        return FARLANDS$SLICEID && this.farlands$originX == 2147483664L
            && ((NoiseChunkDiagAccess) (Object) this).farlands$originZ() == 288L;
    }

    @Inject(method = "fillSlice", at = @At("HEAD"))
    private void farlands$fillSlice(boolean first, int cellX, CallbackInfo ci) {
        if (!farlands$on() || FARLANDS$N.incrementAndGet() > 60) {
            return;
        }
        NoiseChunkDiagAccess acc = (NoiseChunkDiagAccess) (Object) this;
        long expected = this.farlands$originX
            + ((long) cellX - (long) acc.farlands$firstCellX()) * (long) acc.farlands$cellWidth();
        System.out.println("[SLICEID] fillSlice first=" + first + " cellX=" + cellX
            + " productInt=" + (cellX * acc.farlands$cellWidth())
            + " beforeLong=" + this.farlands$cellStartBlockXLong
            + " expectedLong=" + expected
            + " firstCellX=" + acc.farlands$firstCellX());
        System.out.flush();
    }

    @Inject(method = "advanceCellX", at = @At("RETURN"))
    private void farlands$advanceReturn(int cellXIndex, CallbackInfo ci) {
        if (!farlands$on() || FARLANDS$N.incrementAndGet() > 100) {
            return;
        }
        NoiseChunkDiagAccess acc = (NoiseChunkDiagAccess) (Object) this;
        long expected = this.farlands$originX + (long) cellXIndex * (long) acc.farlands$cellWidth();
        System.out.println("[SLICEID] advanceCellX RETURN idx=" + cellXIndex
            + " cellStartXint=" + this.cellStartBlockX
            + " long=" + this.farlands$cellStartBlockXLong
            + " expectedLong=" + expected
            + " match=" + (this.farlands$cellStartBlockXLong == expected));
        System.out.flush();
    }

    @Inject(method = "selectCellYZ", at = @At("RETURN"))
    private void farlands$selectCellReturn(int cellY, int cellZ, CallbackInfo ci) {
        if (!farlands$on() || cellY != 23) {
            return;
        }
        if (FARLANDS$N.incrementAndGet() > 200) {
            return;
        }
        if (this.interpolators == null || this.interpolators.isEmpty()) {
            return;
        }
        Object first = this.interpolators.get(0);
        if (!(first instanceof NoiseInterpolatorDiagAccess interp)) {
            return;
        }
        NoiseChunkDiagAccess acc = (NoiseChunkDiagAccess) (Object) this;
        int w = acc.farlands$cellWidth();
        int h = acc.farlands$cellHeight();
        double raw = interp.farlands$rawAt(this.farlands$cellStartBlockXLong,
            this.cellStartBlockY, this.farlands$cellStartBlockZLong);
        System.out.println("[SLICEID] selectCellYZ cellY=" + cellY + " cellZ=" + cellZ
            + " cellStart=" + this.cellStartBlockX + "/" + this.farlands$cellStartBlockXLong
            + "," + this.cellStartBlockY
            + "," + this.cellStartBlockZ + "/" + this.farlands$cellStartBlockZLong
            + " w/h=" + w + "/" + h
            + " filler=" + interp.farlands$fillerType()
            + " s0[z][y]=" + interp.farlands$slice0(cellZ, cellY)
            + " s1[z][y]=" + interp.farlands$slice1(cellZ, cellY)
            + " s0[z+1][y+1]=" + interp.farlands$slice0(cellZ + 1, cellY + 1)
            + " rawAtStart=" + raw
            + " hash0=" + interp.farlands$slice0Hash()
            + " hash1=" + interp.farlands$slice1Hash());
        System.out.flush();
    }
}
