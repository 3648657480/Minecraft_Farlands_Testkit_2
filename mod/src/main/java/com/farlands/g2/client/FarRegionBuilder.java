package com.farlands.g2.client;

import com.farlands.g2.runtime.section.SectionKey;
import com.scalarwide.mc.WideSectionKey2560;
import java.lang.reflect.Constructor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCopy;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelHeightAccessor;

/**
 * Builds the 3x3x3 compiler region from the wide client chunk store. The patched
 * SectionCopy constructor wraps a PalettedContainer directly (no LevelChunk).
 */
public final class FarRegionBuilder {

    private static Constructor<SectionCopy> ctor;
    private static boolean logged;
    private static int entered;

    private FarRegionBuilder() {
    }

    public static RenderSectionRegion create(ClientLevel level, long sectionNode) {
        SectionKey key = RenderKeys.keyOf(sectionNode);
        if (key == null || WideClientChunkStore.current() == null) {
            return null;
        }
        boolean far = key.lx != (long) (int) key.lx || key.ly != (long) (int) key.ly
            || key.lz != (long) (int) key.lz;
        if (far && entered++ < 12) {
            System.out.println("[FARREGION] enter node=" + sectionNode + " key=" + key
                + " far=" + far + " store=true");
            System.out.flush();
        }
        if (!far) {
            return null;
        }
        SectionCopy[] copies = new SectionCopy[27];
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int index = (dx + 1) + (dy + 1) * 3 + (dz + 1) * 9;
                    SectionKey cell = key.offset(dx, dy, dz);
                    LevelChunkSection section = WideClientChunkStore.section(cell.wide);
                    PalettedContainer<BlockState> states = section == null
                        || section.hasOnlyAir() ? null : section.getStates().copy();
                    copies[index] = copy(states, level);
                }
            }
        }
        return new RenderSectionRegion(level, -1, -1, -1, copies);
    }

    @SuppressWarnings("unchecked")
    private static SectionCopy copy(PalettedContainer<BlockState> states, LevelHeightAccessor level) {
        try {
            if (ctor == null) {
                ctor = (Constructor<SectionCopy>) SectionCopy.class.getDeclaredConstructor(
                    PalettedContainer.class, LevelHeightAccessor.class);
                ctor.setAccessible(true);
            }
            return ctor.newInstance(states, level);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("wide SectionCopy constructor missing", e);
        }
    }
}
