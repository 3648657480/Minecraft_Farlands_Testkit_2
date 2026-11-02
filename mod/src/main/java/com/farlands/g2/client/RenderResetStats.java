package com.farlands.g2.client;

/** Counts render-section key assignments for the boundary/reload probe. */
public final class RenderResetStats {

    private static long keySets;

    private RenderResetStats() {
    }

    public static void keySet() {
        keySets++;
    }

    public static long keySets() {
        return keySets;
    }
}
