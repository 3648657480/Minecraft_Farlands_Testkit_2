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
 * Diagnostic (-Dfarlands.diag.filldiag): for the seam chunk 134217729
 * (originX = 2^31+16) and column (int x = -2147483632, z = 293):
 *
 * <ul>
 *   <li>print the block-state rule result for y = 65..125 (null = density &gt; 0,
 *       so the generator writes the default block = stone);</li>
 *   <li>evaluate the raw {@code router.finalDensity()} with two contexts at the
 *       same voxel:
 *       <ul>
 *         <li>{@code compute(noiseChunk)} - the fill's real FunctionContext; if
 *             this already returns the mirrored profile, a density node still
 *             uses the int {@code blockX()} view;</li>
 *         <li>{@code compute(RealContext(realX, y, realZ))} - the truth.</li>
 *       </ul>
 *       The raw tree has no NoiseChunk wrappers, so this evaluation does not
 *       perturb the interpolator.</li>
 * </ul>
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
    @Shadow private DensityFunction fullNoiseDensity;
    @Shadow private net.minecraft.world.level.levelgen.blending.Blender blender;
    @Shadow private net.minecraft.world.level.levelgen.DensityFunctions.BeardifierOrMarker beardifier;

    @Unique
    private static final boolean FARLANDS$FILLDIAG = Boolean.getBoolean("farlands.diag.filldiag");

    @Unique
    private static final boolean[] FARLANDS$STATE_DONE = new boolean[256];

    @Unique
    private static final boolean[] FARLANDS$ROUTER_DONE = new boolean[256];

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

    @Inject(method = "getInterpolatedState", at = @At("HEAD"))
    private void farlands$logRouterContexts(CallbackInfoReturnable<BlockState> cir) {
        int y = farlands$probeY();
        if (y < 0 || FARLANDS$ROUTER_DONE[y]) {
            return;
        }
        DensityFunction finalDensity = FarFillDiag.finalDensity();
        if (finalDensity == null) {
            return;
        }
        FARLANDS$ROUTER_DONE[y] = true;
        double viaChunk = finalDensity.compute(
            (DensityFunction.FunctionContext) (Object) this);
        double viaReal = finalDensity.compute(new RealContext(2147483664L, y, 293L));
        System.out.println("[FILLDIAG] y=" + y + " routerViaNoiseChunk=" + viaChunk
            + " routerViaRealContext=" + viaReal);
        if (y == 120) {
            System.out.println("[FILLDIAG] wrappedClass=" + this.fullNoiseDensity.getClass().getName()
                + " blender=" + this.blender.getClass().getName()
                + " blenderEmpty=" + (this.blender == net.minecraft.world.level.levelgen.blending.Blender.empty())
                + " beardifier=" + this.beardifier.getClass().getName()
                + " beardEmpty=" + (this.beardifier == net.minecraft.world.level.levelgen.Beardifier.EMPTY));
        }
        System.out.flush();
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
