package com.farlands.g2.client;

import com.farlands.g2.runtime.section.SectionKey;
import com.scalarwide.mc.WideSectionKey2560;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
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
    private static final Map<SectionKey, SectionCopy> CACHE = new HashMap<>();
    private static SectionCopy empty;
    private static boolean logged;
    private static int entered;
    private static int nonAirLogged;
    private static int farlands$diag;
    private static int farlands$wsecLogs;
    private static int farlands$calls;
    private static int farlands$built;
    private static int farlands$nullKeys;
    private static long farlands$lastStat;
    private static final java.util.Set<String> farlands$distinct =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    private FarRegionBuilder() {
    }

    public static RenderSectionRegion create(ClientLevel level, long sectionNode) {
        SectionKey key = RenderKeys.keyOf(sectionNode);
        farlands$calls++;
        if (Boolean.getBoolean("farlands.trace.farstats")) {
            long now = System.currentTimeMillis();
            if (now - farlands$lastStat > 2000L) {
                farlands$lastStat = now;
                System.out.println("[FARSTAT] calls=" + farlands$calls + " built="
                    + farlands$built + " distinctKeys=" + farlands$distinct.size()
                    + " nullKey=" + farlands$nullKeys);
                System.out.flush();
            }
        }
        if (Boolean.getBoolean("farlands.trace.farregion")
            && WideRenderBase.established() && farlands$diag++ < 24) {
            System.out.println("[FARRD] node=" + sectionNode + " key=" + key
                + " regNodes=" + RenderKeys.nodeCount()
                + " hasNode=" + RenderKeys.hasNode(sectionNode));
            System.out.flush();
        }
        if (key == null) {
            farlands$nullKeys++;
            return null;
        }
        // "Far" is decided by the frame, not by the key magnitude: in the local
        // frame the key is base + local, which stays inside int for world
        // coordinates below 2^31 blocks even though the renderer is already local.
        boolean far = WideRenderBase.established()
            || key.lx != (long) (int) key.lx || key.ly != (long) (int) key.ly
            || key.lz != (long) (int) key.lz;
        boolean log = far && entered++ < 12;
        if (log) {
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
                    if (section == null) {
                        // Coordinates inside the int chunk range get their data through
                        // the normal chunk packets, not the wide store; read the real
                        // world chunk (local key already carries base + local).
                        section = worldSection(level, cell);
                    }
                    copies[index] = copyCached(cell, section, level);
                }
            }
        }
        LevelChunkSection center = WideClientChunkStore.section(key.wide);
        if (center != null && !center.hasOnlyAir() && nonAirLogged++ < 12) {
            System.out.println("[FARREGION] nonair key=" + key
                + " center=" + center.getBlockState(8, 8, 8));
            System.out.flush();
        }
        if (log) {
            int nonNull = 0;
            for (SectionCopy copy : copies) {
                if (copy != null) {
                    nonNull++;
                }
            }
            String centerState = center == null ? "null"
                : center.getBlockState(8, 8, 8).toString();
            System.out.println("[FARREGION] built key=" + key + " copiesNonNull=" + nonNull
                + " center=" + centerState);
            System.out.flush();
        }
        farlands$built++;
        farlands$distinct.add(key.toString());
        return new FarRenderSectionRegion(level, key, copies);
    }

    /** Air section used for a 3x3x3 neighbour that the far store does not cover. */
    public static SectionCopy emptyCopy(LevelHeightAccessor level) {
        if (empty == null) {
            empty = copy(null, level);
        }
        return empty;
    }

    /** Section read from the real client chunk (world coords = base + local key). */
    private static LevelChunkSection worldSection(ClientLevel level, SectionKey cell) {
        boolean log = Boolean.getBoolean("farlands.trace.regiondata") && farlands$wsecLogs++ < 12;
        if (cell.lx != (long) (int) cell.lx || cell.lz != (long) (int) cell.lz
            || cell.ly != (long) (int) cell.ly) {
            if (log) {
                System.out.println("[WSEC] out-of-int cell=" + cell.lx + "," + cell.ly + "," + cell.lz);
            }
            return null;
        }
        var chunk = level.getChunkSource().getChunk((int) cell.lx, (int) cell.lz,
            net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
        if (chunk == null) {
            if (log) {
                // Distinguish "client has no data there" from "data lives under the
                // local node instead of the world key": probe the same cell minus the
                // render base.
                long bx = com.farlands.g2.client.WideRenderBase.baseX() == null ? 0L
                    : com.farlands.g2.client.WideRenderBase.baseX().longValue();
                long bz = com.farlands.g2.client.WideRenderBase.baseZ() == null ? 0L
                    : com.farlands.g2.client.WideRenderBase.baseZ().longValue();
                long lx = cell.lx - bx;
                long lz = cell.lz - bz;
                boolean localHas = Math.abs(lx) < Integer.MAX_VALUE
                    && Math.abs(lz) < Integer.MAX_VALUE
                    && level.getChunkSource().getChunk((int) lx, (int) lz,
                        net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) != null;
                long pcx = (long) Math.floorDiv((long) Math.floor(
                    net.minecraft.client.Minecraft.getInstance().player.getX()), 16L);
                long pcz = (long) Math.floorDiv((long) Math.floor(
                    net.minecraft.client.Minecraft.getInstance().player.getZ()), 16L);
                System.out.println("[WSEC] no-chunk cell=" + cell.lx + "," + cell.ly + ","
                    + cell.lz + " base=" + bx + "," + bz + " local=" + lx + "," + lz
                    + " localHas=" + localHas + " playerChunk=" + pcx + "," + pcz);
            }
            return null;
        }
        int idx = (int) cell.ly - level.getMinSectionY();
        LevelChunkSection[] sections = chunk.getSections();
        if (idx < 0 || idx >= sections.length) {
            if (log) {
                System.out.println("[WSEC] idx-out cell=" + cell.lx + "," + cell.ly + ","
                    + cell.lz + " idx=" + idx + " len=" + sections.length
                    + " minSec=" + level.getMinSectionY());
            }
            return null;
        }
        LevelChunkSection sec = sections[idx];
        if (log) {
            System.out.println("[WSEC] cell=" + cell.lx + "," + cell.ly + "," + cell.lz
                + " chunk=" + chunk.getPos() + " idx=" + idx
                + " sec=" + (sec == null ? "null" : (sec.hasOnlyAir() ? "air"
                    : sec.getBlockState(8, 8, 8).toString())));
        }
        return sec;
    }

    private static SectionCopy copyCached(SectionKey key, LevelChunkSection section,
        LevelHeightAccessor level) {
        if (section == null || section.hasOnlyAir()) {
            return emptyCopy(level);
        }
        return CACHE.computeIfAbsent(key, ignored -> copy(section.getStates(), level));
    }

    /** Drop cached copies when the sliding window evicts chunks. */
    public static void clearCache() {
        CACHE.clear();
        empty = null;
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
