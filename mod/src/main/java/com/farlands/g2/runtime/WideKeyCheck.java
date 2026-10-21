package com.farlands.g2.runtime;

import com.scalarwide.mc.WideChunkPos;
import net.minecraft.server.level.ChunkHolder;

/** Temporary mismatch guard for wide chunk-holder scheduling. */
public final class WideKeyCheck {
    private static long last;
    private WideKeyCheck() {}

    public static void check(ChunkHolder holder, WideChunkPos key) {
        if (holder == null || key == null) {
            return;
        }
        if (!holder.getPos().widePos().equals(key)) {
            long now = System.currentTimeMillis();
            if (now - last < 3000L) {
                return;
            }
            last = now;
            System.out.println("[WIDEKEY] requested=" + key + " holder=" + holder.getPos().widePos());
            new Throwable("wide key mismatch").printStackTrace(System.out);
            System.out.flush();
        }
    }
}
