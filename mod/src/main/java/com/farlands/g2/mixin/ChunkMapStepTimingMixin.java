package com.farlands.g2.mixin;

import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Diagnostic ({@code -Dfarlands.gen.timing=true}): how long each pipeline step
 * actually took for a far chunk, measured from the step being applied to its
 * future completing. Used to find where the ~1.6 s cold far chunk goes.
 */
@Mixin(ChunkMap.class)
public class ChunkMapStepTimingMixin {

    private static final ThreadLocal<Long> farlands$t0 = new ThreadLocal<>();

    @Inject(method = "applyStep", at = @At("HEAD"))
    private void farlands$stepStart(GenerationChunkHolder holder, ChunkStep step,
        StaticCache2D<GenerationChunkHolder> cache,
        CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (Boolean.getBoolean("farlands.gen.timing")) {
            farlands$t0.set(System.nanoTime());
        }
    }

    @Inject(method = "applyStep", at = @At("RETURN"))
    private void farlands$stepEnd(GenerationChunkHolder holder, ChunkStep step,
        StaticCache2D<GenerationChunkHolder> cache,
        CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        Long t0 = farlands$t0.get();
        farlands$t0.remove();
        if (t0 == null || holder == null || holder.getPos() == null) {
            return;
        }
        long x = holder.getPos().xLong();
        if (x < 2_147_483_648L && !Boolean.getBoolean("farlands.gen.timing.all")) {
            return; // normal coordinates: only logged when the control group is on
        }
        CompletableFuture<ChunkAccess> future = cir.getReturnValue();
        String where = x + "," + holder.getPos().zLong();
        if (future == null) {
            return;
        }
        boolean doneNow = future.isDone();
        long syncMs = (System.nanoTime() - t0) / 1_000_000L;
        future.whenComplete((chunk, error) -> {
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            System.out.println("[GENSTEP] " + step.targetStatus() + " chunk=" + where
                + " ms=" + ms + " sync=" + syncMs + " done=" + doneNow
                + (error == null ? "" : " err=" + error));
            System.out.flush();
        });
    }
}
