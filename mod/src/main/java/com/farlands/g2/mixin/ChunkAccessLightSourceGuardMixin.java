package com.farlands.g2.mixin;

import com.farlands.g2.runtime.WindowedChunk;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Y-E: vanilla {@code ChunkSkyLightSources} stores "first sky-light-blocking Y"
 * in a dimension-sized bit array. A chunk whose window sits at a moved band feeds
 * it absolute Y in the billions, which throws
 * {@code value 2147483329 is not in the inclusive range 0..511} while the client
 * applies a level-chunk packet.
 *
 * <p>Skip light-source initialisation for windowed chunks whose band differs from
 * the dimension band; the window sections get full sky light from the band bridge
 * / light engine instead.</p>
 */
@Mixin(ChunkAccess.class)
public class ChunkAccessLightSourceGuardMixin {

    @Shadow @Final protected LevelHeightAccessor levelHeightAccessor;

    @Inject(method = "initializeLightSources", at = @At("HEAD"), cancellable = true)
    private void farlands$skipSkyLightSources(CallbackInfo ci) {
        if (!Boolean.getBoolean("farlands.y.window")) {
            return;
        }
        if (!(this instanceof WindowedChunk windowed)) {
            return;
        }
        if (windowed.farlands$windowMinSectionY() == this.levelHeightAccessor.getMinSectionY()) {
            return;
        }
        ci.cancel();
    }
}
