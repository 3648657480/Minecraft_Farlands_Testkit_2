package com.farlands.g2.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fail-safe for the flaky player-spawn stall (UI/UX blocker A).
 *
 * <p>Vanilla {@code waitForEntities} loops until the 7x7 spawn chunks all report
 * {@code areEntitiesLoaded == true} - with no bound. Under the FarLands build
 * this wait occasionally never completes (observed hanging world entry twice),
 * blocking all in-world testing. This injection replaces the loop with a
 * bounded variant: same condition, but after 15s it logs which chunk is still
 * missing and lets the spawn proceed (chunks themselves are generated; only the
 * entity-load bookkeeping stalls, so entering is safe for testing).</p>
 */
@Mixin(ServerLevel.class)
public abstract class SpawnEntityWaitFailSafeMixin {

    @Shadow
    private PersistentEntitySectionManager<Entity> entityManager;

    @Inject(method = "waitForEntities", at = @At("HEAD"), cancellable = true)
    private void farlands$boundedWait(ChunkPos centerChunk, int radius, CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        java.util.List<ChunkPos> chunks = ChunkPos.rangeClosed(centerChunk, radius).toList();
        long deadline = System.currentTimeMillis() + 15000;
        self.getServer().managedBlock(() -> {
            this.entityManager.processPendingLoads();
            for (ChunkPos chunk : chunks) {
                if (!self.areEntitiesLoaded(chunk.pack())) {
                    if (System.currentTimeMillis() > deadline) {
                        System.out.println("[FarLands-G2] spawn entity wait timed out at " + chunk
                            + "; proceeding with the player spawn");
                        this.farlands$dumpSpawnStatuses(chunks);
                        System.out.flush();
                        return true;
                    }
                    return false;
                }
            }
            return true;
        });
        ci.cancel();
    }

    /**
     * Diagnostic dump (task c): for every spawn-area chunk that never reached
     * LOADED, print the entity manager's load status + visibility entry.
     * FRESH / visibility-absent = the holder never reached a non-HIDDEN full
     * status (scheduling side); PENDING = the entity load is stuck (I/O side).
     */
    private void farlands$dumpSpawnStatuses(java.util.List<ChunkPos> chunks) {
        var accessor = (PersistentEntitySectionManagerAccessor) (Object) this.entityManager;
        var statuses = accessor.farlands$chunkLoadStatuses();
        var visibility = accessor.farlands$chunkVisibility();
        int loaded = 0;
        StringBuilder missing = new StringBuilder();
        ServerLevel self = (ServerLevel) (Object) this;
        for (ChunkPos chunk : chunks) {
            long key = chunk.pack();
            if (self.areEntitiesLoaded(key)) {
                loaded++;
                continue;
            }
            Object status = statuses.get(key);
            Object vis = visibility.get(key);
            missing.append("[").append(chunk.x()).append(',').append(chunk.z()).append("]=")
                .append(status == null ? "FRESH(null)" : status)
                .append(", vis=").append(vis == null ? "absent" : vis).append("; ");
        }
        System.out.println("[FarLands-G2] spawn status dump: total=" + chunks.size()
            + " loaded=" + loaded + " (bookkeeping maps: statuses=" + statuses.size()
            + ", visibility=" + visibility.size() + ")");
        System.out.println("[FarLands-G2] not loaded: " + missing);
    }
}
