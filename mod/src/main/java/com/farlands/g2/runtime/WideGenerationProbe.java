package com.farlands.g2.runtime;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;

/** Opt-in, read-only snapshot of a stalled wide generation request. */
public final class WideGenerationProbe {
    private WideGenerationProbe() {}
    public static void schedule(ChunkMap map, ChunkPos center) {
        if (!Boolean.getBoolean("farlands.testgen.probe")) return;
        CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS).execute(() -> dump(map, center));
    }
    private static void dump(ChunkMap map, ChunkPos center) {
        try {
            System.out.println("[WIDE-PROBE] pending=" + field(map, "pendingGenerationTasks"));
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                var key = com.scalarwide.mc.WideChunkPos.neighbor(center.widePos(), dx, dz);
                Object holder = map.farlands$getUpdatingChunkW(key);
                if (holder == null) { System.out.println("[WIDE-PROBE] missing " + dx + "," + dz); continue; }
                Object task = ((AtomicReference<?>)field(holder, "task")).get();
                StringBuilder s = new StringBuilder("[WIDE-PROBE] " + dx + "," + dz
                    + " allowed=" + field(holder,"highestAllowedStatus") + " started=" + field(holder,"startedWork")
                    + " task=" + task);
                if (task != null) {
                    s.append(" scheduled=").append(field(task,"scheduledStatus"))
                        .append(" target=").append(field(task,"targetStatus"))
                        .append(" canceled=").append(field(task,"markedForCancellation"))
                        .append(" generation=").append(field(task,"needsGeneration"))
                        .append(" layer=").append(field(task,"scheduledLayer"));
                    Object cache = field(task,"cache");
                    Object[] cells = (Object[])field(cache,"cache");
                    int missing = 0;
                    for (Object cell : cells) if (cell == null) missing++;
                    s.append(" cacheNull=").append(missing).append('/').append(cells.length);
                }
                AtomicReferenceArray<?> futures = (AtomicReferenceArray<?>)field(holder,"futures");
                s.append(" futures=");
                for(int i=0;i<futures.length();i++) {
                    CompletableFuture<?> f = (CompletableFuture<?>)futures.get(i);
                    if(f!=null) s.append(i).append(':').append(f.isDone() ? (f.isCompletedExceptionally()?"error":"done") : "pending").append(' ');
                }
                System.out.println(s);
            }
            System.out.flush();
        } catch (Throwable t) { t.printStackTrace(System.out); }
    }
    private static Object field(Object instance, String name) throws Exception {
        for(Class<?> c=instance.getClass();c!=null;c=c.getSuperclass()) {
            try { Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(instance); }
            catch(NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
}
