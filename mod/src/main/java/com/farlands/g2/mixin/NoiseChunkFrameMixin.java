package com.farlands.g2.mixin;

import com.farlands.g2.runtime.WideFrame;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Attaches the immutable coordinate frame to a NoiseChunk. */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkFrameMixin {

    @Unique
    private WideFrame farlands$frame;

    @Shadow private long farlands$originX;
    @Shadow private long farlands$originZ;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void farlands$frame(CallbackInfo ci) {
        this.farlands$frame = WideFrame.forChunk(this.farlands$originX >> 4, this.farlands$originZ >> 4);
    }

    @Unique
    public WideFrame farlands$frame() {
        return this.farlands$frame;
    }
}