package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.LinearCongruentialGenerator;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** W-2c: biome zoom uses the long block coordinates instead of the int views. */
@Mixin(BiomeManager.class)
public abstract class BiomeManagerWideMixin {

    @Shadow private long biomeZoomSeed;
    @Shadow private BiomeManager.NoiseBiomeSource noiseBiomeSource;

    @Shadow
    private static double getFiddledDistance(long seed, int xRandom, int yRandom, int zRandom,
        double distanceX, double distanceY, double distanceZ) {
        throw new AssertionError();
    }

    @Shadow
    private static double getFiddle(long rval) {
        throw new AssertionError();
    }

    @org.spongepowered.asm.mixin.Unique
    private static long farlands$mix(long rval, long value) {
        // Fold the high 32 bits through the same LCG alongside the low half.
        rval = LinearCongruentialGenerator.next(rval, (int) value);
        rval = LinearCongruentialGenerator.next(rval, (int) (value >>> 32));
        return rval;
    }

    @org.spongepowered.asm.mixin.Unique
    private static double farlands$fiddledDistanceWide(long seed, long xRandom, long yRandom,
        long zRandom, double distanceX, double distanceY, double distanceZ) {
        if (xRandom == (long) (int) xRandom && yRandom == (long) (int) yRandom
            && zRandom == (long) (int) zRandom) {
            return getFiddledDistance(seed, (int) xRandom, (int) yRandom, (int) zRandom,
                distanceX, distanceY, distanceZ);
        }
        long rval = seed;
        rval = farlands$mix(rval, xRandom);
        rval = farlands$mix(rval, yRandom);
        rval = farlands$mix(rval, zRandom);
        rval = farlands$mix(rval, xRandom);
        rval = farlands$mix(rval, yRandom);
        rval = farlands$mix(rval, zRandom);
        double fiddleX = getFiddle(rval);
        rval = LinearCongruentialGenerator.next(rval, (int) seed);
        double fiddleY = getFiddle(rval);
        rval = LinearCongruentialGenerator.next(rval, (int) seed);
        double fiddleZ = getFiddle(rval);
        return Mth.square(distanceZ + fiddleZ) + Mth.square(distanceY + fiddleY)
            + Mth.square(distanceX + fiddleX);
    }

    @Inject(method = "getBiome", at = @At("HEAD"), cancellable = true)
    private void farlands$wideGetBiome(BlockPos pos, CallbackInfoReturnable<Holder<Biome>> cir) {
        long absX = pos.getLongX() - 2L;
        long absY = pos.getLongY() - 2L;
        long absZ = pos.getLongZ() - 2L;
        if (absX == (long) (int) absX && absY == (long) (int) absY && absZ == (long) (int) absZ) {
            return; // int views exact: vanilla path
        }
        long parentX = absX >> 2;
        long parentY = absY >> 2;
        long parentZ = absZ >> 2;
        double fractX = (absX & 3L) / 4.0;
        double fractY = (absY & 3L) / 4.0;
        double fractZ = (absZ & 3L) / 4.0;
        int minI = 0;
        double minFiddledDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            boolean xEven = (i & 4) == 0;
            boolean yEven = (i & 2) == 0;
            boolean zEven = (i & 1) == 0;
            long cornerX = xEven ? parentX : parentX + 1L;
            long cornerY = yEven ? parentY : parentY + 1L;
            long cornerZ = zEven ? parentZ : parentZ + 1L;
            double distanceX = xEven ? fractX : fractX - 1.0;
            double distanceY = yEven ? fractY : fractY - 1.0;
            double distanceZ = zEven ? fractZ : fractZ - 1.0;
            double next = farlands$fiddledDistanceWide(biomeZoomSeed, cornerX, cornerY,
                cornerZ, distanceX, distanceY, distanceZ);
            if (minFiddledDistance > next) {
                minI = i;
                minFiddledDistance = next;
            }
        }
        long biomeX = (minI & 4) == 0 ? parentX : parentX + 1L;
        long biomeY = (minI & 2) == 0 ? parentY : parentY + 1L;
        long biomeZ = (minI & 1) == 0 ? parentZ : parentZ + 1L;
        if (Boolean.getBoolean("farlands.biome.wide")
            && noiseBiomeSource instanceof net.minecraft.server.level.ServerLevel server) {
            var sampler = server.getChunkSource().randomState().sampler();
            var source = server.getChunkSource().getGenerator().getBiomeSource();
            if (source instanceof com.farlands.g2.runtime.WideBiomeSource wide) {
                cir.setReturnValue(wide.getNoiseBiomeWide(biomeX, biomeY, biomeZ, sampler));
                return;
            }
        }
        cir.setReturnValue(noiseBiomeSource.getNoiseBiome((int) biomeX, (int) biomeY,
            (int) biomeZ));
    }
}