package com.farlands.g2.mixin;

import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Diagnostic (-Dfarlands.y.diag): surface the swallowed biomes-stage failure. */
@Mixin(ChunkStatusTasks.class)
public class ChunkBiomesDiagMixin {

    @Inject(method = "generateBiomes", at = @At("RETURN"))
    private static void farlands$logBiomes(WorldGenContext context, ChunkStep step,
        StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk,
        CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (!Boolean.getBoolean("farlands.y.diag")) {
            return;
        }
        CompletableFuture<ChunkAccess> future = cir.getReturnValue();
        future.whenComplete((result, error) -> {
            if (error != null) {
                System.out.println("[YBIOME-ERR] pos=" + chunk.getPos()
                    + " minY=" + chunk.getMinY() + " -> " + error);
                error.printStackTrace(System.out);
                System.out.flush();
            }
        });
    }
}
