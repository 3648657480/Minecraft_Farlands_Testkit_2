package com.farlands.g2.client;

import com.scalarwide.mc.WideChunkPos;
import com.scalarwide.mc.WideSectionKey2560;
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

    /** Section for an exact 2560 key, or null. */
    public static LevelChunkSection section(WideSectionKey2560 key) {
        Data data = current;
        if (data == null || key == null) {
            return null;
        }
        // This store currently holds one chunk. A 3x3x3 region must not copy its
        // sections into every neighbour chunk; that is the repeated-terrain bug.
        java.math.BigInteger baseX = data.pos().x().toBigInteger();
        java.math.BigInteger baseZ = data.pos().z().toBigInteger();
        java.math.BigInteger keyX = key.x().toBigInteger();
        java.math.BigInteger keyZ = key.z().toBigInteger();
        if (keyX.compareTo(baseX) < 0
            || keyX.compareTo(baseX.add(java.math.BigInteger.valueOf(16))) >= 0
            || keyZ.compareTo(baseZ) < 0
            || keyZ.compareTo(baseZ.add(java.math.BigInteger.valueOf(16))) >= 0) {
            return null;
        }
        long sectionY;
        try {
            sectionY = key.y().toBigInteger().longValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
        int index = (int) (sectionY - data.minSectionY());
        if (index < 0 || index >= data.sections().length) {
            return null;
        }
        return data.sections()[index];
    }

    public static void clear() {
        current = null;
    }
}
