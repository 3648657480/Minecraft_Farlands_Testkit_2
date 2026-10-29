package com.farlands.g2.runtime;

/**
 * Session lifecycle for all mutable, world-scoped G2 state.
 *
 * <p>This is the anti-collapse fence: every cache that used to be a bare static
 * (prepared pages, band overrides, in-flight follow attempts, held players,
 * captured chunk steps) is reset here when a server starts or stops. Without
 * this, loading world A and then world B in the same JVM can move A's prepared
 * page into a B chunk at the same coordinates (EXPERIMENTS: world-switch stale
 * SectionCopy).</p>
 *
 * <p>Client-only state (RenderKeys, ClientBandBridge pending pushes, BandState)
 * is reset from the client level-change hook, not here: this class must stay
 * loadable on a dedicated server.</p>
 */
public final class G2Runtime {

    private G2Runtime() {
    }

    /** Clears every server-side world-scoped cache. Safe to call more than once. */
    public static void resetSession() {
        PageStore.clear();
        YBandOverrides.clearAll();
        WindowFollower.reset();
        StageSteps.clear();
    }
}
