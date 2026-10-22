package com.farlands.g2.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Y-F: make the client's section grid cover the chunk window instead of the
 * dimension band.
 *
 * <p>{@code ViewArea} is built from {@code ClientLevel.getMinY()/getMaxY()/
 * getMinSectionY()/getMaxSectionY()}. Once chunks are windowed at a band away
 * from the dimension band, those values no longer describe the chunks the client
 * holds, so the view area would contain no sections for them.</p>
 *
 * <p>Enabled by {@code -Dfarlands.y.window=true} together with a fixed
 * {@code -Dfarlands.y.center=<sectionY>} (the same band the chunks are created
 * at); dynamic follow is Y-D's job.</p>
 */
@Mixin(LevelRenderer.class)
public class ViewAreaWindowMixin {

    @Unique private static final int HALF = 11;

    @Unique
    private static boolean farlands$on() {
        return Boolean.getBoolean("farlands.y.window")
            && System.getProperty("farlands.y.center") != null
            && !System.getProperty("farlands.y.center").isEmpty();
    }

    @Unique
    private static int farlands$minSection() {
        return Integer.getInteger("farlands.y.center") - HALF;
    }

    @Redirect(method = "invalidateCompiledGeometry",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMinY()I"))
    private int farlands$minY(ClientLevel level) {
        return farlands$on() ? farlands$minSection() * 16 : level.getMinY();
    }

    @Redirect(method = "invalidateCompiledGeometry",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMaxY()I"))
    private int farlands$maxY(ClientLevel level) {
        return farlands$on() ? (farlands$minSection() + 23) * 16 + 15 : level.getMaxY();
    }

    @Redirect(method = "invalidateCompiledGeometry",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMinSectionY()I"))
    private int farlands$minSectionY(ClientLevel level) {
        return farlands$on() ? farlands$minSection() : level.getMinSectionY();
    }

    @Redirect(method = "invalidateCompiledGeometry",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMaxSectionY()I"))
    private int farlands$maxSectionY(ClientLevel level) {
        return farlands$on() ? farlands$minSection() + 23 : level.getMaxSectionY();
    }
}
