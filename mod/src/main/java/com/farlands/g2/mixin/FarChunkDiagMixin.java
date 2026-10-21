package com.farlands.g2.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Diagnostic (default off, {@code -Dfarlands.diag.holdercreate=true}): logs the
 * full call chain when a far chunk is about to be prepared for access, i.e.
 * when a ChunkHolder whose wide coordinate lies in the int-boundary band
 * reaches {@code prepareAccessibleChunk}. Used to find who creates the
 * int-view "ghost" holder at chunk 2^31.
 */
@Mixin(ChunkMap.class)
public class FarChunkDiagMixin {

    private static final boolean ENABLED = Boolean.getBoolean("farlands.diag.holdercreate");
    private static int farlands$logged;

    @Inject(method = "prepareAccessibleChunk", at = @At("HEAD"))
    private void farlands$logFarPrepare(ChunkHolder chunk, CallbackInfoReturnable<?> cir) {
        if (!ENABLED || farlands$logged >= 6 || chunk == null) {
            return;
        }
        long wx = chunk.getPos().xLong();
        long wz = chunk.getPos().zLong();
        if (Math.abs(wx) < 2147483584L && Math.abs(wz) < 2147483584L) {
            return;
        }
        // Ghost signature: the wide coordinate equals its own int view, i.e. the
        // holder was built by an int-only path at the int boundary.
        if (wx != (long) chunk.getPos().x() && wz != (long) chunk.getPos().z()) {
            return;
        }
        farlands$logged++;
        System.out.println("[FARPREP] holder=" + System.identityHashCode(chunk)
            + " pos=" + chunk.getPos().x() + "," + chunk.getPos().z()
            + " wide=" + wx + "," + wz);
        StackTraceElement[] st = new Throwable().getStackTrace();
        for (int i = 1; i < Math.min(st.length, 12); i++) {
            System.out.println("   at " + st[i]);
        }
        System.out.flush();
    }
}