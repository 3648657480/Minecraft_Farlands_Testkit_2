package com.farlands.g2.runtime;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * W-1 of {@code docs/WIDE-MIGRATION.md}: one-shot narrowing audit.
 *
 * <p>When {@code -Dfarlands.diag.narrow=true}, every distinct caller that hands
 * a coordinate to a lossy view API is reported once, together with the first
 * frames of its stack. This produces the real "still narrowed" list instead of
 * guessing from grep.</p>
 */
public final class Narrowing {

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private Narrowing() {
    }

    public static void warn(String api) {
        if (!Boolean.getBoolean("farlands.diag.narrow")) {
            return;
        }
        StackTraceElement[] frames = Thread.currentThread().getStackTrace();
        String site = api + " @ " + frame(frames, 2) + " <- " + frame(frames, 3);
        if (!WARNED.add(site)) {
            return;
        }
        System.out.println("[NARROW] " + site);
        for (int i = 2; i < frames.length && i < 9; i++) {
            System.out.println("[NARROW]    at " + frames[i]);
        }
        System.out.flush();
    }

    private static String frame(StackTraceElement[] frames, int index) {
        return index < frames.length ? frames[index].toString() : "?";
    }

    public static int count() {
        return WARNED.size();
    }
}
