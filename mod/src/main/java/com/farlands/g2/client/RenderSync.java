package com.farlands.g2.client;

/**
 * One-frame handshake between the view grid and the extractor.
 *
 * <p>The fork patch on {@code SectionOcclusionGraph.update} no longer schedules
 * vanilla frustum updates, so {@code LevelExtractor.extract} only rebuilt the
 * visible-section list when the camera <b>rotation</b> changed. Walking straight
 * therefore moved the grid without refreshing the list: the leading edge never
 * compiled and stale sections stayed visible until the player turned.</p>
 */
public final class RenderSync {

    private static volatile boolean centerChanged;

    private RenderSync() {
    }

    public static void centerChanged() {
        centerChanged = true;
    }

    public static boolean consumeCenterChanged() {
        boolean changed = centerChanged;
        centerChanged = false;
        return changed;
    }
}
