package com.farlands.g2.runtime;

import com.scalarwide.mc.WideSectionCoord512;
import com.scalarwide.mc.WideSectionKey2560;
import java.util.Map;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Duck interface implemented on {@code ChunkAccess} by
 * {@code ChunkAccessWindowMixin}.
 *
 * <p>The window is a <b>view</b>: absolute section identity is the wide
 * {@link WideSectionKey2560} (X 1024 + Y 512 + Z 1024), and the section window is
 * anchored on a {@link WideSectionCoord512}. The int section index is only local
 * addressing inside the window and must never be used as a persistent key.</p>
 */
public interface WindowedChunk {

    /** Absolute wide-keyed section container. */
    Map<WideSectionKey2560, LevelChunkSection> farlands$allSections();

    /** Window anchor (absolute, wide Y). */
    WideSectionCoord512 farlands$windowCenterY();

    /** Window bounds as absolute (int) section numbers; valid while the anchor fits int. */
    int farlands$windowMinSectionY();

    int farlands$windowMaxSectionY();

    /** Wide key of an absolute section Y in this chunk's column. */
    WideSectionKey2560 farlands$key(int sectionY);
}
