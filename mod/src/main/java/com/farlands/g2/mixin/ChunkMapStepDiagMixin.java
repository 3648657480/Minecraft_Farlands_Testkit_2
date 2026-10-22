package com.farlands.g2.mixin;

import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Diagnostic (-Dfarlands.y.diag): print generation step + parent presence. */
@Mixin(ChunkMap.class)
public class ChunkMapStepDiagMixin {

    private static int farlands$n;

    @Inject(method = "applyStep", at = @At("HEAD"))
    private void farlands$step(GenerationChunkHolder holder, ChunkStep step,
        StaticCache2D<GenerationChunkHolder> cache,
        CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (!Boolean.getBoolean("farlands.y.diag")) {
            return;
        }
        if (step.targetStatus() == ChunkStatus.EMPTY) {
            return; // EMPTY short-circuits to scheduleChunkLoad; parent is itself
        }
        farlands$n++;
        ChunkStatus parent = step.targetStatus().getParent();
        ChunkAccess p = parent == null ? null : holder.getChunkIfPresentUnchecked(parent);
        if (p != null) {
            return; // only the fatal shape is interesting
        }
        System.out.println("[YSTEP-MISS] " + step.targetStatus().getName()
            + " pos=" + holder.getPos().x() + "," + holder.getPos().z()
            + " parent=" + (parent == null ? "null" : parent.getName())
            + " holderLevel=" + holder.getTicketLevel());
        System.out.flush();
    }
}
