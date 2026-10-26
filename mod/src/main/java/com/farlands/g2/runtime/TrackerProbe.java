package com.farlands.g2.runtime;

import net.minecraft.client.SectionUpdateTracker;

/** Diagnostic holder: latest extractor dirty tracker (set from a mixin). */
public final class TrackerProbe {

    public static SectionUpdateTracker LAST;

    private TrackerProbe() {
    }
}