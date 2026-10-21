package com.farlands.g2.mixin;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips the generateSpawn step (initial mob population) for chunks whose wide
 * chunk coordinate lies outside the int-safe band.
 *
 * <p>At chunk 2^31 the WorldGenRegion's StaticCache2D window (center +/- radius)
 * wraps its int bounds — minX+sizeX goes past Integer.MAX_VALUE — and the spawn
 * path asks it for coordinates that then fall out of range:
 * {@code IllegalArgumentException: Requested out of range value ... from
 * StaticCache2D[...]}, which aborts chunk generation. Far-domain spawn is
 * explicitly out of scope (WIDE-MIGRATION §4: "远域 spawn step 可跳过（不承诺刷怪）"),
 * so skip it. Normal coordinates are untouched: the guard is 2^31 - 64 chunks,
 * where the int window cannot wrap.</p>
 */
@Mixin(NoiseBasedChunkGenerator.class)
public class NoiseBasedChunkGeneratorFarSpawnMixin {

    /** Wide chunk coordinate at or beyond which an int generation window can wrap. */
    private static final long FAR_GUARD = 2_147_483_648L - 64L;

    @Inject(method = "spawnOriginalMobs", at = @At("HEAD"), cancellable = true)
    private void farlands$skipFarSpawn(WorldGenRegion region, CallbackInfo ci) {
        ChunkPos center = region.getCenter();
        if (Math.abs(center.xLong()) > FAR_GUARD || Math.abs(center.zLong()) > FAR_GUARD) {
            ci.cancel();
        }
    }
}
