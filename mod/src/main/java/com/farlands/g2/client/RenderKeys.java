package com.farlands.g2.client;

import com.farlands.g2.runtime.section.SectionKey;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/**
 * B-render of {@code docs/RENDER-WIDE-KEYS.md}: render sections carry their
 * 2560-bit {@link SectionKey} identity, and the render pipeline's Y decoding
 * goes through this table instead of decoding the packed {@code long}.
 *
 * <p>The packed long is still used as an <b>equality key</b> by vanilla render
 * bookkeeping (dirty trackers, waiting lists). Within the loaded vertical window
 * (24 sections) that packing is injective, so equality works; only decoding the
 * absolute Y was wrong, and that is what this table fixes.</p>
 *
 * <p>Every method falls back to the vanilla {@code SectionPos} decode for longs
 * that were not produced by the render pipeline (normal worlds keep working
 * bit-identically).</p>
 */
public final class RenderKeys {

    private static final Long2ObjectOpenHashMap<SectionRenderDispatcher.RenderSection> BY_NODE =
        new Long2ObjectOpenHashMap<>();
    private static final Map<SectionKey, SectionRenderDispatcher.RenderSection> BY_KEY =
        new HashMap<>();

    private RenderKeys() {
    }

    public static void put(SectionKey key, SectionRenderDispatcher.RenderSection section) {
        if (key == null || section == null) {
            return;
        }
        BY_KEY.put(key, section);
        long node = section.getSectionNode();
        if (node != 0L) {
            BY_NODE.put(node, section);
        }
    }

    public static void clear() {
        BY_NODE.clear();
        BY_KEY.clear();
    }

    public static SectionRenderDispatcher.RenderSection get(long node) {
        return BY_NODE.get(node);
    }

    public static SectionRenderDispatcher.RenderSection get(SectionKey key) {
        return BY_KEY.get(key);
    }

    private static SectionKey keyOf(long node) {
        SectionRenderDispatcher.RenderSection section = BY_NODE.get(node);
        if (section instanceof com.farlands.g2.runtime.section.RenderSectionKeyAccessor accessor) {
            return accessor.farlands$getKey();
        }
        return null;
    }

    public static int x(long node) {
        SectionKey key = keyOf(node);
        return key != null ? key.x : SectionPos.x(node);
    }

    public static int y(long node) {
        SectionKey key = keyOf(node);
        return key != null ? key.y : SectionPos.y(node);
    }

    public static int z(long node) {
        SectionKey key = keyOf(node);
        return key != null ? key.z : SectionPos.z(node);
    }

    public static SectionPos pos(long node) {
        SectionKey key = keyOf(node);
        return key != null ? SectionPos.of(key.x, key.y, key.z) : SectionPos.of(node);
    }

    public static BlockPos center(long node) {
        SectionKey key = keyOf(node);
        if (key != null) {
            return key.centerBlock();
        }
        return SectionPos.of(node).center();
    }
}
