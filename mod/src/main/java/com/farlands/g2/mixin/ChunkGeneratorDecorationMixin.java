package com.farlands.g2.mixin;

import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Biome decoration (features) seed.
 *
 * <p>{@code ChunkGenerator.applyBiomeDecoration} derives the feature RNG from
 * {@code setDecorationSeed(levelSeed, originX, originZ)} where the origin comes
 * from the int views of the section origin: at block 2^31 the origin wraps and
 * features at the real column reuse a mirrored seed. Capture the long chunk
 * origin at method entry and reproduce {@code setDecorationSeed} in the long
 * domain. At normal coordinates the inputs are identical, so vanilla behaviour
 * is bit-for-bit unchanged.</p>
 */
@Mixin(ChunkGenerator.class)
public class ChunkGeneratorDecorationMixin {

    @Unique
    private static final ThreadLocal<long[]> FARLANDS_ORIGIN = new ThreadLocal<>();

    @org.spongepowered.asm.mixin.injection.Inject(method = "applyBiomeDecoration", at = @At("HEAD"), cancellable = true)
    private void farlands$maybeSkip(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (Boolean.getBoolean("farlands.diag.noFeatures")) {
            ci.cancel();
        }
    }

    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void farlands$captureOrigin(WorldGenLevel level, ChunkAccess chunk,
        StructureManager structureManager, CallbackInfo ci) {
        FARLANDS_ORIGIN.set(new long[]{(long) chunk.getPos().x() << 4, (long) chunk.getPos().z() << 4});
    }

    @Redirect(method = "applyBiomeDecoration",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setDecorationSeed(JII)J"))
    private long farlands$decorationSeed(WorldgenRandom random, long seed, int x, int z) {
        long[] origin = FARLANDS_ORIGIN.get();
        long lx = origin != null ? origin[0] : x;
        long lz = origin != null ? origin[1] : z;
        random.setSeed(seed);
        long xScale = random.nextLong() | 1L;
        long zScale = random.nextLong() | 1L;
        long result = lx * xScale + lz * zScale ^ seed;
        random.setSeed(result);
        return result;
    }
}
