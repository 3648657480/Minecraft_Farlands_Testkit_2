package com.farlands.g2.mixin;

import com.farlands.g2.runtime.WindowedChunk;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.NoiseSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Y-B: noise settings must follow the chunk window.
 *
 * <p>Vanilla {@code clampToHeightAccessor} intersects the dimension band with the
 * accessor band. Once a chunk's window is moved (the band is disjoint from the
 * dimension band), that intersection has a negative height
 * ({@code min(settings.maxY, chunk.maxY+1) - max(settings.minY, chunk.minY)}),
 * which surfaces as {@code NegativeArraySizeException} inside
 * {@code NoiseChunk.NoiseInterpolator.allocateSlice}.</p>
 *
 * <p>When the accessor is a {@link WindowedChunk} and the intersection is
 * non-positive, rebase the settings onto the window band instead.</p>
 */
@Mixin(NoiseSettings.class)
public class NoiseSettingsWindowMixin {

    @Inject(method = "clampToHeightAccessor", at = @At("HEAD"), cancellable = true)
    private void farlands$windowClamp(LevelHeightAccessor heightAccessor,
        CallbackInfoReturnable<NoiseSettings> cir) {
        if (!Boolean.getBoolean("farlands.y.window")) {
            return;
        }
        if (Boolean.getBoolean("farlands.y.diag")) {
            System.out.println("[NSWIN] clamp accessor=" + heightAccessor.getClass().getSimpleName()
                + " windowed=" + (heightAccessor instanceof WindowedChunk)
                + " accessorMinY=" + heightAccessor.getMinY()
                + " settingsMinY=" + ((NoiseSettings) (Object) this).minY());
            System.out.flush();
        }
        if (!(heightAccessor instanceof WindowedChunk)) {
            return;
        }
        NoiseSettings self = (NoiseSettings) (Object) this;
        int chunkMin = heightAccessor.getMinY();
        int chunkHeight = heightAccessor.getHeight();
        int newMin = Math.max(self.minY(), chunkMin);
        int newHeight = Math.min(self.minY() + self.height(), chunkMin + chunkHeight) - newMin;
        if (newHeight <= 0) {
            cir.setReturnValue(new NoiseSettings(chunkMin, chunkHeight,
                self.noiseSizeHorizontal(), self.noiseSizeVertical()));
        }
    }
}
