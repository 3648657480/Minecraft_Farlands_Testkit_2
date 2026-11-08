package com.farlands.g2.client;

import com.farlands.g2.runtime.WideChunkPacketStore;
import java.math.BigInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Runtime bridge from the integrated-server wide packet store to the client
 * render data store. This is production code, not a diagnostic probe.
 */
public final class WideClientBridge {

    private static long lastVersion = -1;
    /** Mark only the arriving chunk's own sections (fewer competing compiles). */
    private static final boolean SELF_DIRTY = Boolean.getBoolean("farlands.render.selfDirty");

    private WideClientBridge() {
    }

    public static void apply(Minecraft mc) {
        if (!Boolean.getBoolean("farlands.diag.clientWideChunk")
            || mc.level == null || WideChunkPacketStore.size() == 0) {
            return;
        }
        repairVisibleFar(mc);
        long packetVersion = WideChunkPacketStore.version();
        if (packetVersion == lastVersion) {
            return;
        }
        lastVersion = packetVersion;

        boolean firstFill = WideClientChunkStore.all().isEmpty();
        boolean changed = false;
        for (var packet : WideChunkPacketStore.all()) {
            var buffer = new net.minecraft.network.FriendlyByteBuf(
                io.netty.buffer.Unpooled.wrappedBuffer(packet.data()));
            int count = buffer.readVarInt();
            int minSy = buffer.readVarInt();
            var sections = new LevelChunkSection[count];
            for (int i = 0; i < count; i++) {
                var section = new LevelChunkSection(mc.level.palettedContainerFactory());
                section.read(buffer);
                sections[i] = section;
            }
            long before = WideClientChunkStore.version();
            WideClientChunkStore.set(new WideClientChunkStore.Data(
                packet.pos(), minSy, sections));
            boolean newlyStored = WideClientChunkStore.version() != before;
            if (newlyStored) {
                changed = true;
                if (!firstFill) {
                    int sx = packet.pos().x().toBigInteger().intValue();
                    int sz = packet.pos().z().toBigInteger().intValue();
                    for (int i = 0; i < count; i++) {
                        if (SELF_DIRTY) {
                            // Only the sections of the chunk itself. Marking the
                            // 3x3x3 neighbourhood here adds ~300 extra compiles per
                            // window move, which competes with chunk generation for
                            // CPU; the cost is a little border overdraw until the
                            // neighbour rotates/recompiles.
                            mc.level.setSectionRangeDirty(sx, minSy + i, sz,
                                sx, minSy + i, sz);
                        } else {
                            mc.level.setSectionDirtyWithNeighbors(sx, minSy + i, sz);
                        }
                    }
                }
            }
        }

        if (mc.player != null) {
            BigInteger cx = BigInteger.valueOf(
                Math.floorDiv((long) Math.floor(mc.player.getX()), 16L));
            BigInteger cz = BigInteger.valueOf(
                Math.floorDiv((long) Math.floor(mc.player.getZ()), 16L));
            var center = com.scalarwide.mc.WideChunkPos.of(cx, cz);
            int dataRadius = Integer.getInteger("farlands.far.dataRadius", 5);
            // Prune only when every chunk of the new ring is present; a count
            // can be satisfied by stale trailing chunks while the leading edge
            // is still missing.
            if (WideClientChunkStore.hasAllAround(center, dataRadius)) {
                var removed = WideClientChunkStore.retainAround(center, dataRadius);
                if (!removed.isEmpty()) {
                    for (var data : removed) {
                        int sx = data.pos().x().toBigInteger().intValue();
                        int sz = data.pos().z().toBigInteger().intValue();
                        for (int i = 0; i < data.sections().length; i++) {
                            mc.level.setSectionDirtyWithNeighbors(sx, data.minSectionY() + i, sz);
                        }
                    }
                    FarRegionBuilder.clearCache();
                }
            }
        }

        if (changed && firstFill) {
            for (var section : RenderKeys.all()) {
                if (section instanceof com.farlands.g2.runtime.section.RenderSectionKeyAccessor accessor) {
                    var key = accessor.farlands$getKey();
                    if (key != null && (key.lx != (long) (int) key.lx
                        || key.ly != (long) (int) key.ly || key.lz != (long) (int) key.lz)) {
                        mc.level.setSectionDirtyWithNeighbors(key.x, key.y, key.z);
                    }
                }
            }
            mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options,
                mc.gameRenderer.mainCamera(), mc.getBlockColors());
        }
    }

    /** Re-queue far sections that are visible but were missed by the dirty tracker. */
    private static void repairVisibleFar(Minecraft mc) {
        int budget = 8;
        for (var section : mc.levelRenderer.visibleSections()) {
            if (section.getSectionMesh()
                != net.minecraft.client.renderer.chunk.CompiledSectionMesh.UNCOMPILED) {
                continue;
            }
            var key = section instanceof com.farlands.g2.runtime.section.RenderSectionKeyAccessor a
                ? a.farlands$getKey() : null;
            if (key == null || key.lx == (long) (int) key.lx
                && key.ly == (long) (int) key.ly && key.lz == (long) (int) key.lz) {
                continue;
            }
            if (WideClientChunkStore.section(key.wide) == null) {
                continue;
            }
            mc.level.setSectionDirtyWithNeighbors(key.x, key.y, key.z);
            if (--budget <= 0) {
                return;
            }
        }
    }
}
