package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Distance-delayed local sounds at far X.
 *
 * <p>{@code Level.playLocalSound(BlockPos, ...)} builds the source position
 * from int block coordinates. At or beyond 2^31 the position wraps, so the
 * sound is treated as hundreds of millions of blocks away and is never heard
 * (and the distance delay is wrong). Use the wide coordinates; the
 * double-based overload keeps the vanilla distance attenuation.</p>
 */
@Mixin(Level.class)
public class LevelPlayLocalSoundWideMixin {

    @Inject(method = "playLocalSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V",
        at = @At("HEAD"), cancellable = true)
    private void farlands$wideSound(BlockPos pos, SoundEvent sound, SoundSource source,
        float volume, float pitch, boolean distanceDelay, CallbackInfo ci) {
        long x = pos.getLongX();
        long z = pos.getLongZ();
        if (x == (long) (int) x && z == (long) (int) z) {
            return;
        }
        Level self = (Level) (Object) this;
        self.playLocalSound(x + 0.5, pos.getY() + 0.5, z + 0.5, sound, source, volume, pitch, distanceDelay);
        ci.cancel();
    }
}