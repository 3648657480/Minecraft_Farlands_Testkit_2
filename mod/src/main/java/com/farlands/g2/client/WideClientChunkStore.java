package com.farlands.g2.client;

import com.scalarwide.mc.WideChunkPos;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** W1-4b2a: decoded far chunk kept on the client for the HUD wide view. */
public final class WideClientChunkStore {

    public record Data(WideChunkPos pos, int minSectionY, LevelChunkSection[] sections) {
    }

    private static volatile Data current;

    private WideClientChunkStore() {
    }

    public static void set(Data data) {
        current = data;
    }

    public static Data current() {
        return current;
    }

    public static void clear() {
        current = null;
    }
}
