package com.farlands.g2.mixin;

import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * W5: LevelChunk.postProcessGeneration ticks fluids (FlowingFluid.tick) at
 * block positions derived with int coordinates. For a chunk at block >= 2^31
 * those positions wrap negative, setBlock resolves the negative mirror chunk,
 * and the recursive chunk load fails (POI SectionStorage ISE, server stall).
 * Skip post-processing for chunks whose block origin is outside the int
 * domain; terrain/rendering are unaffected (residual: no fluid spread in that
 * chunk column).
 */
@Mixin(LevelChunk.class)
public class LevelChunkPostProcessGuardMixin {

    @Inject(method = "postProcessGeneration", at = @At("HEAD"), cancellable = true)
    private void farlands$guard(CallbackInfo ci) {
        LevelChunk self = (LevelChunk) (Object) this;
        long x = self.getPos().getMinBlockXLong();
        long z = self.getPos().getMinBlockZLong();
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE
            || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) {
            ci.cancel();
        }
    }
}
