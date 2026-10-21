package com.farlands.g2.mixin;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Diagnostic (default off, -Dfarlands.diag.genbump=true): logs every status bump
 * whose current startedWork is not the status parent — the exact condition that
 * throws "Unexpected last startedWork status" at far chunk coordinates.
 */
@Mixin(GenerationChunkHolder.class)
public class GenerationBumpDiagMixin {

    private static final boolean ENABLED = Boolean.getBoolean("farlands.diag.genbump");

    @Shadow protected ChunkPos pos;
    @Shadow private AtomicReference<ChunkStatus> startedWork;

    @Inject(method = "acquireStatusBump", at = @At("HEAD"))
    private void farlands$logBump(ChunkStatus status, CallbackInfoReturnable<Boolean> cir) {
        if (!ENABLED) {
            return;
        }
        ChunkStatus parent = status == ChunkStatus.EMPTY ? null : status.getParent();
        ChunkStatus prev = this.startedWork.get();
        // Only the fatal shape is interesting: a fresh bump target that expects a
        // parent (i.e. a holder that never ran the previous step).
        if (prev == null && parent != null) {
            System.out.println("[GBUMP] holder=" + System.identityHashCode(this)
                + " pos=" + this.pos.x() + "," + this.pos.z()
                + " wide=" + this.pos.xLong() + "," + this.pos.zLong()
                + " want=" + status.getName() + " parent=" + (parent == null ? "null" : parent.getName())
                + " prev=" + (prev == null ? "null" : prev.getName()));
            System.out.flush();
        }
    }
}