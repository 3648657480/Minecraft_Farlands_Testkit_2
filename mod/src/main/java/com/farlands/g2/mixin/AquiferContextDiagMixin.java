package com.farlands.g2.mixin;

import com.farlands.g2.runtime.WideContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Diagnostic: at the aquifer sample point, compare the int view {@code blockX()}
 * with the wide accessor. Used to decide whether the exact-context rewrite can
 * be applied without changing normal-world generation.
 */
@Mixin(targets = "net/minecraft/world/level/levelgen/Aquifer$NoiseBasedAquifer")
public class AquiferContextDiagMixin {

    @Unique
    private static int farlands$logged;

    @Inject(method = "computeSubstance", at = @At("HEAD"))
    private void farlands$diag(DensityFunction.FunctionContext context, double density,
        CallbackInfoReturnable<BlockState> cir) {
        if (!Boolean.getBoolean("farlands.diag.aquifer") || farlands$logged >= 6) {
            return;
        }
        farlands$logged++;
        int bx = context.blockX();
        int bz = context.blockZ();
        double dx = context instanceof WideContext wide ? wide.getBlockXDouble() : bx;
        double dz = context instanceof WideContext wide ? wide.getBlockZDouble() : bz;
        System.out.println("[AQDIAG] class=" + context.getClass().getName()
            + " bx=" + bx + " dx=" + dx + " bz=" + bz + " dz=" + dz
            + " gridInt=" + ((bx - 5) >> 4) + " gridCtx=" + ((long) Math.floorDiv((long) dx - 5L, 16L)));
        System.out.flush();
    }
}
