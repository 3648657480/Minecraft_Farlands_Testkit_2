package com.farlands.g2.mixin;

import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Opt-in trace for far-domain generation layer progression (-Dfarlands.gen.trace=true). */
@Mixin(ChunkGenerationTask.class)
public class GenerationTaskTraceMixin {

    private static final boolean TRACE = Boolean.getBoolean("farlands.gen.trace");

    private static boolean far(GenerationChunkHolder holder) {
        return holder != null && holder.getPos() != null && holder.getPos().xLong() >= 2_147_483_648L;
    }

    @Inject(method = "scheduleNextLayer", at = @At("HEAD"))
    private void farlands$next(CallbackInfo ci) {
        if (!TRACE) return;
        ChunkGenerationTask self = (ChunkGenerationTask) (Object) this;
        GenerationChunkHolder center = self.getCenter();
        if (far(center)) {
            System.out.println("[GEN] next center=" + center.getPos().xLong() + "," + center.getPos().zLong()
                + " scheduled=" + field(self, "scheduledStatus") + " target=" + field(self, "targetStatus")
                + " needsGeneration=" + field(self, "needsGeneration"));
        }
    }

    @Inject(method = "scheduleLayer", at = @At("HEAD"))
    private void farlands$layerHead(ChunkStatus status, boolean needsGeneration, CallbackInfo ci) {
        if (!TRACE) return;
        ChunkGenerationTask self = (ChunkGenerationTask) (Object) this;
        GenerationChunkHolder center = self.getCenter();
        if (far(center)) {
            System.out.println("[GEN] layerHead " + center.getPos().xLong() + "," + center.getPos().zLong()
                + " status=" + status.getName() + " gen=" + needsGeneration);
        }
    }

    @Inject(method = "scheduleLayer", at = @At("RETURN"))
    private void farlands$layerDone(ChunkStatus status, boolean needsGeneration, CallbackInfo ci) {
        if (!TRACE) return;
        ChunkGenerationTask self = (ChunkGenerationTask) (Object) this;
        GenerationChunkHolder center = self.getCenter();
        if (far(center)) {
            System.out.println("[GEN] layerDone " + center.getPos().xLong() + "," + center.getPos().zLong()
                + " status=" + status.getName());
        }
    }

    @Inject(method = "scheduleChunkInLayer", at = @At("HEAD"))
    private void farlands$chunkHead(ChunkStatus status, boolean needsGeneration, GenerationChunkHolder holder,
        CallbackInfoReturnable<Boolean> cir) {
        if (!TRACE) return;
        ChunkGenerationTask self = (ChunkGenerationTask) (Object) this;
        if (far(self.getCenter())) {
            System.out.println("[GEN] chunkHead center=" + self.getCenter().getPos().xLong()
                + " status=" + status.getName() + " cell=" + holder.getPos().xLong() + "," + holder.getPos().zLong()
                + " persisted=" + holder.getPersistedStatus() + " allowed=" + field(holder, "highestAllowedStatus"));
        }
    }

    @Inject(method = "scheduleChunkInLayer", at = @At("RETURN"))
    private void farlands$chunkDone(ChunkStatus status, boolean needsGeneration, GenerationChunkHolder holder,
        CallbackInfoReturnable<Boolean> cir) {
        if (!TRACE) return;
        ChunkGenerationTask self = (ChunkGenerationTask) (Object) this;
        if (far(self.getCenter())) {
            System.out.println("[GEN] chunkDone " + holder.getPos().xLong() + "," + holder.getPos().zLong()
                + " status=" + status.getName() + " -> " + cir.getReturnValue());
        }
    }

    private static Object field(Object o, String name) {
        try {
            var f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        } catch (Throwable t) {
            return "?";
        }
    }
}
