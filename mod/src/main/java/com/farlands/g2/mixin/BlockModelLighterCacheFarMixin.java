package com.farlands.g2.mixin;

import com.farlands.g2.runtime.FarLightMode;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Far-domain model lighting: skip the 64-bit position cache and use full bright.
 *
 * <p>26.2's section compiler shades blocks through {@code BlockModelLighter.Cache},
 * which keys the packed light coords and the shade brightness by
 * {@code BlockPos.asLong()} - a <b>lossy</b> 26/12/26 packing (T3: packed longs are
 * a bridge, never identity). Far blocks alias onto keys of blocks 2^32 away, so a
 * quad can be handed the brightness or shade cached for a different coordinate.
 * The visible result is dark quads over otherwise lit terrain, which is also why
 * the constant far light level did not remove them: the value came from the cache,
 * not from the light engine.</p>
 *
 * <p>In the far domain the value is not cached and the light coords are the
 * full-bright constant {@code 15728880} - the same policy as the far light guard
 * ({@code ConstantLightValueMixin} / {@code ConstantRawBrightnessMixin}). The shade
 * factor still comes from the block state, so faces keep their relative shading.
 * Normal coordinates keep the cache untouched.</p>
 */
@Mixin(BlockModelLighter.Cache.class)
public class BlockModelLighterCacheFarMixin {

    /** {@code LightCoordsUtil.FULL_BRIGHT}: block 15, sky 15. */
    private static final int FULL_BRIGHT = 15728880;

    @Inject(method = "getLightCoords", at = @At("HEAD"), cancellable = true)
    private void farlands$fullBright(BlockState state, BlockAndTintGetter level,
        BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (FarLightMode.active()) {
            cir.setReturnValue(FULL_BRIGHT);
        }
    }

    @Inject(method = "getShadeBrightness", at = @At("HEAD"), cancellable = true)
    private void farlands$directShade(BlockState state, BlockAndTintGetter level,
        BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (FarLightMode.active()) {
            cir.setReturnValue(state.getShadeBrightness(level, pos));
        }
    }
}
