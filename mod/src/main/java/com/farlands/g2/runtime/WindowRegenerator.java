package com.farlands.g2.runtime;

import com.scalarwide.mc.WideSectionKey2560;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Y-B/B-step: regenerate one chunk's vertical window <b>in place</b>.
 *
 * <p>Instead of unloading the chunk (ticket/tracker churn, EXPERIMENTS §60), a
 * detached {@link ProtoChunk} is created at the target band, terrain noise is
 * run for it, and its sections are transplanted into the live chunk's
 * wide-keyed section map; the window is then rebuilt and the runtime heightmaps
 * re-primed.</p>
 *
 * <p>The noise future may be completed by the main-thread executor, so this is
 * asynchronous: the callback runs on the server thread after the transplant.</p>
 *
 * <p>Prototype scope: noise only (no surface rules / carvers / features yet).</p>
 */
public final class WindowRegenerator {

    private WindowRegenerator() {
    }

    public static void regenerate(LevelChunk chunk, int centerSectionY, Consumer<Boolean> done) {
        if (!(chunk.getLevel() instanceof ServerLevel level)) {
            done.accept(false);
            return;
        }
        ChunkPos pos = chunk.getPos();
        YBandOverrides.set(pos.widePos(), centerSectionY);
        ProtoChunk proto = new ProtoChunk(pos, UpgradeData.EMPTY, level,
            level.palettedContainerFactory(), null);
        RandomState randomState = level.getChunkSource().randomState();
        StructureManager structures = level.structureManager();
        level.getChunkSource().getGenerator()
            .fillFromNoise(Blender.empty(), randomState, structures, proto)
            .whenComplete((filled, error) -> level.getServer().execute(() -> {
                if (error != null) {
                    YBandOverrides.clear(pos.widePos());
                    System.out.println("[REGEN] fillFromNoise failed: " + error);
                    System.out.flush();
                    done.accept(false);
                    return;
                }
                transplant(chunk, (ChunkAccess) filled, centerSectionY);
                done.accept(true);
            }));
    }

    private static void transplant(LevelChunk chunk, ChunkAccess proto, int centerSectionY) {
        WindowedChunk windowed = (WindowedChunk) chunk;
        int len = chunk.getSections().length;
        int half = (len - 1) / 2;
        int minSy = centerSectionY - half;
        Map<WideSectionKey2560, LevelChunkSection> all = windowed.farlands$allSections();
        LevelChunkSection[] protoSections = proto.getSections();
        for (int i = 0; i < len && i < protoSections.length; i++) {
            all.put(windowed.farlands$key(minSy + i), protoSections[i]);
        }
        windowed.farlands$moveWindowTo(centerSectionY);
        Heightmap.primeHeightmaps(chunk, EnumSet.of(
            Heightmap.Types.WORLD_SURFACE,
            Heightmap.Types.MOTION_BLOCKING,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Heightmap.Types.OCEAN_FLOOR));
    }
}
