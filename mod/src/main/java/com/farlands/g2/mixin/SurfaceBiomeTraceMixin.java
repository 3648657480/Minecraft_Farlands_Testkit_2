package com.farlands.g2.mixin;

import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Opt-in trace for the far-domain surface biome-collection window (-Dfarlands.surface.trace=true). */
@Mixin(NoiseBasedChunkGenerator.class)
public class SurfaceBiomeTraceMixin {

    private static final boolean TRACE = Boolean.getBoolean("farlands.surface.trace");

    @Inject(method = "collectPossibleBiomes", at = @At("HEAD"))
    private static void farlands$head(WorldGenRegion region, int radius, CallbackInfoReturnable<Set<Holder<Biome>>> cir) {
        if (!TRACE) return;
        var c = region.getCenter();
        System.out.println("[SURF] begin centerLong=" + c.xLong() + "," + c.zLong()
            + " centerInt=" + c.x() + "," + c.z() + " radius=" + radius
            + " expectedCells=" + ((long) (2 * radius + 1) * (2 * radius + 1)));
        System.out.flush();
    }

    @Inject(method = "collectPossibleBiomes", at = @At("RETURN"))
    private static void farlands$done(WorldGenRegion region, int radius, CallbackInfoReturnable<Set<Holder<Biome>>> cir) {
        if (!TRACE) return;
        System.out.println("[SURF] done centerLong=" + region.getCenter().xLong() + "," + region.getCenter().zLong()
            + " biomes=" + (cir.getReturnValue() == null ? "null" : cir.getReturnValue().size()));
        System.out.flush();
    }
}
