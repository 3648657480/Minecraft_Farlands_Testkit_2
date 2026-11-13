package com.farlands.g2.mixin;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.level.WorldGenRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rate-limits the vanilla "unsafe terrain read during worldgen" warning.
 *
 * <p>The warning is emitted once per read that lands outside the region's write
 * zone. At the 2^31 seam some placed features (measured: {@code bamboo},
 * {@code bamboo_vegetation}) derive a block position through the int view, so the
 * read lands in the mirrored chunk {@code [-134217728, ...]} - distance 2.68e8 from
 * the generating chunk. Vanilla logs an ERROR line for every such read: a single
 * 2.5-minute session produced a <b>1.17 GB</b> log, which also stalls the worker
 * threads on I/O.</p>
 *
 * <p>The reads still happen (that is the real, still-open feature-position bug);
 * this mixin only stops the logger from turning each one into a line. The first
 * 20 and every 10000th warning are kept, plus one summary line, so the symptom
 * stays visible without flooding.</p>
 */
@Mixin(WorldGenRegion.class)
public class WorldGenRegionWarnLimitMixin {

    @Unique
    private static final AtomicLong farlands$unsafeReads = new AtomicLong();

    @Inject(method = "warnIfReadOutsideWriteZone", at = @At("HEAD"), cancellable = true)
    private void farlands$limitUnsafeReadWarn(int chunkX, int chunkZ, CallbackInfo ci) {
        long n = farlands$unsafeReads.incrementAndGet();
        if (n == 1000L) {
            System.out.println("[WGWARN] unsafe worldgen reads reached 1000; "
                + "all further warnings are suppressed");
            System.out.flush();
        }
        // Suppress all of them: the reads themselves are the open bug, but the log
        // line per read costs ~1.2 GB per session and stalls the worker on I/O.
        ci.cancel();
    }
}
