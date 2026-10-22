package com.farlands.g2.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

/**
 * Captures the real {@link ChunkStep} instances (they are built privately by the
 * chunk-status pipeline) so the window regenerator can construct a
 * {@link net.minecraft.server.level.WorldGenRegion} with the same write radius
 * and dependencies as vanilla.
 */
public final class StageSteps {

    private static final Map<ChunkStatus, ChunkStep> STEPS = new ConcurrentHashMap<>();

    private StageSteps() {
    }

    public static void put(ChunkStep step) {
        STEPS.put(step.targetStatus(), step);
    }

    public static ChunkStep get(ChunkStatus status) {
        return STEPS.get(status);
    }
}
