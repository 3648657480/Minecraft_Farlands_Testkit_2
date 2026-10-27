package com.farlands.g2.mixin;

import com.farlands.g2.runtime.WideFrame;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Attaches the immutable coordinate frame to a surface-rules context. */
@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$Context")
public abstract class SurfaceRulesContextFrameMixin {

    @Unique
    private WideFrame farlands$frame;

    @Shadow
    private ChunkAccess chunk;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void farlands$frame(CallbackInfo ci) {
        var pos = this.chunk.getPos();
        this.farlands$frame = WideFrame.forChunk(pos.xLong(), pos.zLong());
    }

    @Unique
    public WideFrame farlands$frame() {
        return this.farlands$frame;
    }

}