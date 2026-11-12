package com.farlands.g2.runtime;

import java.util.Map;

/**
 * Stall watchdog: when the render thread stops producing frames for longer than
 * {@code farlands.stall.ms} (default 5000), dump every thread's stack once.
 *
 * <p>Long stalls are the one class of problem that normal logging cannot see - the
 * log simply stops and the cause is whatever the threads were doing at that moment.
 * This keeps the evidence for free: no JVM flag, no attaching jstack during a
 * freeze that may already be over.</p>
 */
public final class StallWatchdog {

    private static volatile long lastFrameNanos = System.nanoTime();
    private static volatile boolean started;

    private StallWatchdog() {
    }

    /** Called once per rendered frame (cheap: a single volatile write). */
    public static void frame() {
        lastFrameNanos = System.nanoTime();
    }

    public static void start() {
        if (started) {
            return;
        }
        started = true;
        Thread t = new Thread(StallWatchdog::loop, "FarLands-StallWatchdog");
        t.setDaemon(true);
        t.start();
    }

    private static void loop() {
        int dumps = 0;
        long limit = Long.getLong("farlands.stall.ms", 5000L);
        while (true) {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                return;
            }
            long idleMs = (System.nanoTime() - lastFrameNanos) / 1_000_000L;
            if (idleMs < limit || dumps >= 5) {
                continue;
            }
            dumps++;
            dump(idleMs, dumps);
            try {
                // Do not flood: one dump per stall is enough to read the cause.
                Thread.sleep(10_000L);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void dump(long idleMs, int index) {
        StringBuilder sb = new StringBuilder();
        sb.append("[STALL] no frame for ").append(idleMs).append(" ms (dump ")
            .append(index).append("/5)\n");
        Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
        for (var e : all.entrySet()) {
            Thread th = e.getKey();
            StackTraceElement[] st = e.getValue();
            if (st.length == 0) {
                continue;
            }
            sb.append("[STALL] thread \"").append(th.getName()).append("\" ")
                .append(th.getState()).append('\n');
            int n = Math.min(st.length, 12);
            for (int i = 0; i < n; i++) {
                sb.append("[STALL]     at ").append(st[i]).append('\n');
            }
        }
        System.out.print(sb);
        System.out.flush();
    }
}
