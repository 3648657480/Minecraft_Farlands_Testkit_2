package com.farlands.g2.mixin;

import com.farlands.g2.runtime.FarFillDiag;
import com.farlands.g2.runtime.RealContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Diagnostic (-Dfarlands.diag.pair): at the same voxel print the fill's
 * {@code getInterpolatedState()} result and the raw {@code finalDensity} at the
 * exact same coordinates (RealContext), for the seam-adjacent columns
 * x = 2147483663 (chunk 134217728, east edge) and x = 2147483664 (chunk
 * 134217729, west edge), y = 64..125, z = 293. Read-only.
 */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkSeamPairDiagMixin {

    @Shadow private int cellStartBlockX;
    @Shadow private int cellStartBlockY;
    @Shadow private int cellStartBlockZ;
    @Shadow private int inCellX;
    @Shadow private int inCellY;
    @Shadow private int inCellZ;
    @Shadow private long farlands$originX;
    @Shadow private long farlands$cellStartBlockXLong;
    @Shadow private long farlands$cellStartBlockZLong;

    @Unique
    private static final boolean FARLANDS$PAIR = Boolean.getBoolean("farlands.diag.pair");

    @Unique
    private static final boolean[] FARLANDS$DONE = new boolean[2 * 200];

    @Inject(method = "getInterpolatedState", at = @At("RETURN"))
    private void farlands$logPair(CallbackInfoReturnable<BlockState> cir) {
        if (!FARLANDS$PAIR) {
            return;
        }
        long wx = this.farlands$cellStartBlockXLong + this.inCellX;
        long wz = this.farlands$cellStartBlockZLong + this.inCellZ;
        int y = this.cellStartBlockY + this.inCellY;
        if ((wx != 2147483663L && wx != 2147483664L) || wz != 293L || y < 64 || y > 125) {
            return;
        }
        int slot = (int) (wx - 2147483663L) * 200 + (y + 64);
        if (FARLANDS$DONE[slot]) {
            return;
        }
        FARLANDS$DONE[slot] = true;
        double raw = Double.NaN;
        DensityFunction finalDensity = FarFillDiag.finalDensity();
        if (finalDensity != null) {
            raw = finalDensity.compute(new RealContext(wx, y, wz));
        }
        BlockState state = cir.getReturnValue();
        System.out.println("[PAIR] x=" + wx + " y=" + y + " z=" + wz
            + " origin=" + this.farlands$originX
            + " state=" + (state == null ? "NULL(solid)" : state.getBlock().toString())
            + " raw=" + raw);
        System.out.flush();
    }
}
