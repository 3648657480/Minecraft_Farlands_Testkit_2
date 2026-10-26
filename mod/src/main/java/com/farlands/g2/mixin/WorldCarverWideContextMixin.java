package com.farlands.g2.mixin;

import com.farlands.g2.runtime.RealContext;
import com.farlands.g2.runtime.WideBlockAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.carver.CarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * W-2: {@code getCarveState} built the aquifer context from the int views of the
 * probe position, so at far X the aquifer sampled a different column. Re-run the
 * same logic with the long position only when the int view is lossy.
 */
@Mixin(WorldCarver.class)
public abstract class WorldCarverWideContextMixin {

    @Shadow
    protected static FluidState LAVA;

    @Shadow
    private static boolean isDebugEnabled(CarverConfiguration configuration) {
        throw new AssertionError();
    }

    @Shadow
    private static BlockState getDebugState(CarverConfiguration configuration, BlockState state) {
        throw new AssertionError();
    }

    @Inject(method = "getCarveState", at = @At("HEAD"), cancellable = true)
    private void farlands$wideCarveState(CarvingContext context, CarverConfiguration configuration,
        BlockPos blockPos, Aquifer aquifer, CallbackInfoReturnable<BlockState> cir) {
        if (!WideBlockAccess.isWide(blockPos.getLongX(), blockPos.getLongY(), blockPos.getLongZ())) {
            return;
        }
        if (blockPos.getLongY() <= configuration.lavaLevel.resolveY(context)) {
            cir.setReturnValue(LAVA.createLegacyBlock());
            return;
        }
        BlockState state = aquifer.computeSubstance(
            new RealContext(blockPos.getLongX(), blockPos.getLongY(), blockPos.getLongZ()), 0.0);
        if (state == null) {
            cir.setReturnValue(isDebugEnabled(configuration)
                ? configuration.debugSettings.getBarrierState() : null);
        } else {
            cir.setReturnValue(isDebugEnabled(configuration)
                ? getDebugState(configuration, state) : state);
        }
    }
}
