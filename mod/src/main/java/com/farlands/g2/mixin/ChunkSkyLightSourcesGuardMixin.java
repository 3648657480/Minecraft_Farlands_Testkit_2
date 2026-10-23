package com.farlands.g2.mixin;

import com.farlands.g2.runtime.WindowedChunk;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Y-E: {@link ChunkSkyLightSources} stores block Y relative to a dimension-sized
 * minimum in a 9-bit array (0..511). A windowed chunk at a moved band feeds it
 * absolute Y in the billions, so every block update throws
 * {@code value ... not in the inclusive range 0..511}.
 *
 * <p>Skip the edge update for windowed chunks (the window gets full sky light from
 * the band bridge) and, defensively, for any Y outside the array's representable
 * range.</p>
 */
@Mixin(ChunkSkyLightSources.class)
public class ChunkSkyLightSourcesGuardMixin {

    @Shadow @Final private int minY;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void farlands$guardUpdate(BlockGetter level, int x, int y, int z,
        CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.getBoolean("farlands.y.window") && level instanceof WindowedChunk) {
            cir.setReturnValue(false);
            return;
        }
        if (y < this.minY || y > this.minY + 511) {
            cir.setReturnValue(false);
        }
    }
}
