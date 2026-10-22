package com.farlands.g2.mixin;

import com.farlands.g2.runtime.VerticalBounds;
import net.minecraft.world.level.LevelHeightAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Y-B: the playable height comes from {@link VerticalBounds}, not from
 * {@code DimensionType} (whose int {@code height} cannot span ±2^31).
 *
 * <p>This changes only the predicates; the actual band a chunk covers is the
 * window owned by {@code ChunkAccessWindowMixin}. Both must be enabled together
 * ({@code -Dfarlands.y.window=true}) - see EXPERIMENTS §53 for why a
 * bounds-only change is invalid.</p>
 */
@Mixin(LevelHeightAccessor.class)
public interface LevelHeightAccessorBoundsMixin {

    @Inject(method = "isOutsideBuildHeight(I)Z", at = @At("HEAD"), cancellable = true)
    default void farlands$wideOutside(int blockY, CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.getBoolean("farlands.y.window")) {
            return;
        }
        cir.setReturnValue(!VerticalBounds.inBuildHeight(blockY));
    }

    @Inject(method = "isInsideBuildHeight(I)Z", at = @At("HEAD"), cancellable = true)
    default void farlands$wideInside(int blockY, CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.getBoolean("farlands.y.window")) {
            return;
        }
        cir.setReturnValue(VerticalBounds.inBuildHeight(blockY));
    }
}
