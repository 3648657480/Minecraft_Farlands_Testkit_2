package com.farlands.g2.mixin;

import com.farlands.g2.runtime.FarFillDiag;
import com.farlands.g2.runtime.NoiseChunkDiagAccess;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Diagnostic (-Dfarlands.diag.chain): at the seam chunk, print the class chain of
 * {@code fullNoiseDensity} and of {@code router.finalDensity()}, and the actual
 * {@code getInterpolatedDensity()} value at (3664,120,293). Read-only: it never
 * evaluates fullNoiseDensity itself.
 */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkTreeChainDiagMixin {

    @Shadow private DensityFunction fullNoiseDensity;
    @Shadow private int cellStartBlockX;
    @Shadow private int cellStartBlockY;
    @Shadow private int cellStartBlockZ;
    @Shadow private int inCellX;
    @Shadow private int inCellY;
    @Shadow private int inCellZ;
    @Shadow private long farlands$originX;
    @Shadow private long farlands$originZ;
    @Shadow private long farlands$cellStartBlockXLong;
    @Shadow private long farlands$cellStartBlockZLong;

    @Unique
    private static final boolean FARLANDS$CHAIN = Boolean.getBoolean("farlands.diag.chain");

    @Unique
    private static final AtomicBoolean FARLANDS$CHAIN_DONE = new AtomicBoolean();

    @Unique
    private static final AtomicBoolean FARLANDS$VOXEL_DONE = new AtomicBoolean();

    @Unique
    private static String farlands$chain(DensityFunction start) {
        StringBuilder sb = new StringBuilder();
        DensityFunction f = start;
        for (int i = 0; i < 6 && f != null; i++) {
            sb.append(i).append(':').append(f.getClass().getName()).append(' ');
            if (f instanceof DensityFunctions.MarkerOrMarked marked) {
                f = marked.wrapped();
            } else {
                break;
            }
        }
        return sb.toString();
    }

    @Inject(method = "getInterpolatedState", at = @At("HEAD"))
    private void farlands$logChain(CallbackInfoReturnable<net.minecraft.world.level.block.state.BlockState> cir) {
        if (!FARLANDS$CHAIN) {
            return;
        }
        NoiseChunkDiagAccess acc = (NoiseChunkDiagAccess) (Object) this;
        if (this.farlands$originX != 2147483664L || acc.farlands$originZ() != 288L) {
            return;
        }
        if (!FARLANDS$CHAIN_DONE.compareAndSet(false, true)) {
            return;
        }
        System.out.println("[CHAIN] fullNoiseDensity = " + farlands$chain(this.fullNoiseDensity));
        System.out.println("[CHAIN] router.finalDensity = " + farlands$chain(FarFillDiag.finalDensity()));
        System.out.flush();
    }
}
