package com.farlands.g2.mixin;

import com.farlands.g2.runtime.NoiseChunkDiagAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Diagnostic (-Dfarlands.diag.sliceid): trace the cell-slice bookkeeping of the
 * seam chunk - {@code fillSlice} (which cell was written), {@code advanceCellX}
 * (int vs long cell start before/after, and the expected origin + relative
 * offset), and {@code selectCellYZ} (which cell is selected for the corners).
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

    @Unique
    private static final boolean FARLANDS$SLICEID = Boolean.getBoolean("farlands.diag.sliceid");

    @Unique
    private static final AtomicInteger FARLANDS$N = new AtomicInteger();

    @Unique
    private boolean farlands$on() {
        return FARLANDS$SLICEID
            && (this.farlands$originX == 2147483664L || this.farlands$originX == 2147483680L);
    }

    @Inject(method = "fillSlice", at = @At("HEAD"))
    private void farlands$fillSlice(boolean first, int cellX, CallbackInfo ci) {
        if (!farlands$on() || FARLANDS$N.incrementAndGet() > 80) {
            return;
        }
        NoiseChunkDiagAccess acc = (NoiseChunkDiagAccess) (Object) this;
        long expected = this.farlands$originX
            + ((long) cellX - (long) acc.farlands$firstCellX()) * (long) acc.farlands$cellWidth();
        System.out.println("[SLICEID] fillSlice first=" + first + " cellX=" + cellX
            + " productInt=" + (cellX * acc.farlands$cellWidth())
            + " beforeLong=" + this.farlands$cellStartBlockXLong
            + " expectedLong=" + expected
            + " origin=" + this.farlands$originX
            + " firstCellX=" + acc.farlands$firstCellX());
        System.out.flush();
    }

    @Inject(method = "advanceCellX", at = @At("HEAD"))
    private void farlands$advanceHead(int cellXIndex, CallbackInfo ci) {
        if (!farlands$on() || FARLANDS$N.incrementAndGet() > 120) {
            return;
        }
        NoiseChunkDiagAccess acc = (NoiseChunkDiagAccess) (Object) this;
        long expected = this.farlands$originX + (long) cellXIndex * (long) acc.farlands$cellWidth();
        System.out.println("[SLICEID] advanceCellX HEAD idx=" + cellXIndex
            + " cellStartXint=" + this.cellStartBlockX
            + " long=" + this.farlands$cellStartBlockXLong
            + " expectedLong=" + expected
            + " origin=" + this.farlands$originX);
        System.out.flush();
    }

    @Inject(method = "advanceCellX", at = @At("RETURN"))
    private void farlands$advanceReturn(int cellXIndex, CallbackInfo ci) {
        if (!farlands$on() || FARLANDS$N.incrementAndGet() > 160) {
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

    @Inject(method = "selectCellYZ", at = @At("HEAD"))
    private void farlands$selectCell(int cellY, int cellZ, CallbackInfo ci) {
        if (!farlands$on() || FARLANDS$N.incrementAndGet() > 200) {
            return;
        }
        System.out.println("[SLICEID] selectCellYZ cellY=" + cellY + " cellZ=" + cellZ
            + " cellStartY=" + this.cellStartBlockY
            + " cellStartZint=" + this.cellStartBlockZ
            + " cellStartZLong=" + this.farlands$cellStartBlockZLong
            + " cellStartXint=" + this.cellStartBlockX
            + " cellStartXLong=" + this.farlands$cellStartBlockXLong);
        System.out.flush();
    }
}
