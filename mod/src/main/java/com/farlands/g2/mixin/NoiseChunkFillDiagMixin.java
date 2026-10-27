package com.farlands.g2.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Diagnostic (-Dfarlands.diag.filldiag): for the seam chunk 134217729
 * (originX = 2^31+16) and column (int x = -2147483632, z = 293), print the
 * block-state rule result for y = 65..125.
 *
 * <p>In the fill, {@code getInterpolatedState()} returning null means
 * "density &gt; 0" (the aquifer returns null for solid) and the generator then
 * writes {@code NoiseGeneratorSettings.defaultBlock()} (stone). The probe only
 * reads the state; it must not compute the density itself (an extra compute
 * perturbs the interpolator).</p>
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
    private static final boolean[] FARLANDS$STATE_DONE = new boolean[256];

    @Unique
    private int farlands$probeY() {
        if (!FARLANDS$FILLDIAG || this.farlands$originX != 2147483664L) {
            return -1;
        }
        int x = this.cellStartBlockX + this.inCellX;
        int y = this.cellStartBlockY + this.inCellY;
        int z = this.cellStartBlockZ + this.inCellZ;
        if (x != -2147483632 || z != 293 || y < 65 || y > 125) {
            return -1;
        }
        return y;
    }

    @Inject(method = "getInterpolatedState", at = @At("RETURN"))
    private void farlands$logState(CallbackInfoReturnable<BlockState> cir) {
        int y = farlands$probeY();
        if (y < 0 || FARLANDS$STATE_DONE[y]) {
            return;
        }
        FARLANDS$STATE_DONE[y] = true;
        BlockState state = cir.getReturnValue();
        System.out.println("[FILLDIAG] y=" + y + " state="
            + (state == null ? "NULL(solid->defaultBlock)" : state.getBlock().toString()));
        System.out.flush();
    }
}
