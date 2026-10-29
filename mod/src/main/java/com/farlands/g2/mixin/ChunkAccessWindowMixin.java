package com.farlands.g2.mixin;

import com.farlands.g2.runtime.VerticalBounds;
import com.farlands.g2.runtime.WindowedChunk;
import com.scalarwide.mc.WideSectionCoord512;
import com.scalarwide.mc.WideSectionKey2560;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Y-B: wide-keyed, windowed section container.
 *
 * <p>Every section is stored under its absolute {@link WideSectionKey2560}
 * (wide Y). The {@code sections} array is only the current window (same length as
 * the dimension band, so no reallocation is needed yet); the window anchor is a
 * {@link WideSectionCoord512}. The initial window is the dimension band unless
 * {@code -Dfarlands.y.center=<sectionY>} is set (test hook).</p>
 *
 * <p>Enabled by {@code -Dfarlands.y.window=true} until Y-C lands.</p>
 */
@Mixin(ChunkAccess.class)
public abstract class ChunkAccessWindowMixin implements WindowedChunk {

    @Shadow @Final protected LevelHeightAccessor levelHeightAccessor;
    @Shadow @Final protected ChunkPos chunkPos;
    @Shadow @Final protected LevelChunkSection[] sections;

    @Unique private Map<WideSectionKey2560, LevelChunkSection> farlands$all = new LinkedHashMap<>();
    @Unique private WideSectionCoord512 farlands$centerY = WideSectionCoord512.ZERO;
    @Unique private int farlands$minSy;
    @Unique private int farlands$maxSy;
    @Unique private boolean farlands$windowReady;
    @Unique private boolean farlands$heightmapsPrimed;
    @Unique private PalettedContainerFactory farlands$factory;
    /** Shared empty section returned by out-of-view reads (never inserted). */
    @Unique private LevelChunkSection farlands$emptyView;
    /** Far pages kept in memory: current window + up to N-1 pages of history. */
    @Unique private static final int farlands$MAX_SECTIONS =
        Integer.getInteger("farlands.y.maxSections", 24 * 32);
    @Unique private boolean farlands$evictLogged;

    /**
     * Saved heightmaps are band-relative; when the window is not the dimension
     * band, the first height query must re-derive them from the loaded blocks.
     */
    @Inject(method = "getHeight", at = @At("HEAD"))
    private void farlands$primeHeightmapsOnBandChange(net.minecraft.world.level.levelgen.Heightmap.Types type,
        int x, int z, CallbackInfoReturnable<Integer> cir) {
        if (!Boolean.getBoolean("farlands.y.window") || this.farlands$heightmapsPrimed) {
            return;
        }
        this.farlands$heightmapsPrimed = true;
        if (this.farlands$minSy == this.levelHeightAccessor.getMinSectionY()) {
            return; // window equals the dimension band: saved heightmaps are valid
        }
        com.farlands.g2.runtime.Heightmaps.primeFully((ChunkAccess) (Object) this,
            java.util.EnumSet.of(
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR));
    }

    @Inject(
        method = "<init>(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/chunk/PalettedContainerFactory;J[Lnet/minecraft/world/level/chunk/LevelChunkSection;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V",
        at = @At("RETURN"))
    private void farlands$initWindow(ChunkPos pos, UpgradeData upgradeData, LevelHeightAccessor heightAccessor,
        PalettedContainerFactory containerFactory, long inhabitedTime, LevelChunkSection[] sections,
        BlendingData blendingData, CallbackInfo ci) {
        this.farlands$factory = containerFactory;
        // Mixin field-initialiser ordering is not guaranteed for every constructor
        // path (ChunkMap.createEmptyChunk calls the 7-arg ctor directly); never rely
        // on farlands$all being non-null here.
        farlands$allSections();
        int dimensionMin = heightAccessor.getMinSectionY();
        int dimensionMax = heightAccessor.getMaxSectionY();
        int len = this.sections.length;
        int center = (dimensionMin + dimensionMax) / 2;
        String override = System.getProperty("farlands.y.center");
        Integer perChunk = com.farlands.g2.runtime.YBandOverrides.get(chunkPos.widePos());
        Integer savedMinSection = com.farlands.g2.runtime.SavedBandContext.get();
        if (perChunk != null) {
            center = perChunk;
            if (Boolean.getBoolean("farlands.y.diag")) {
                System.out.println("[YWIN] ctor pos=" + pos + " perChunk=" + perChunk);
                System.out.flush();
            }
        } else if (savedMinSection != null) {
            // Loaded from disk: keep the band the chunk was saved with, otherwise
            // every saved section falls outside the window and the chunk is empty.
            center = savedMinSection + (len - 1) / 2;
            if (Boolean.getBoolean("farlands.y.diag")) {
                System.out.println("[SBCTX] ctor pos=" + pos + " use savedMin="
                    + savedMinSection + " center=" + center);
                System.out.flush();
            }
        } else if (override != null && !override.isEmpty()) {
            center = Integer.parseInt(override);
        }
        // Seed the wide map with the incoming sections under the DIMENSION band.
        // For a fresh ProtoChunk the incoming array is the dimension band; for a
        // loaded far chunk the sidecar restore below puts the far sections back.
        // Labelling dimension sections with far keys loses the normal band (and
        // with it block entities at y=64).
        int start = dimensionMin;
        for (int i = 0; i < len; i++) {
            this.farlands$allSections().put(farlands$key(start + i), this.sections[i]);
        }
        farlands$applyWindow(center);
        // Sidecar restore on every server-side construction (ProtoChunk and the
        // LevelChunk promoted from it). The window has just been rebuilt; load the
        // persisted sections on top so the chunk the world finally reads has them.
        if (heightAccessor instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            com.farlands.g2.runtime.FarSectionStore.load(serverLevel, pos,
                (ChunkAccess) (Object) this);
        }
        if (Boolean.getBoolean("farlands.y.diag") && pos.x() == 0 && pos.z() == 0) {
            var probeKey = farlands$key(134217714);
            var fromMap = farlands$allSections().get(probeKey);
            int idx = 134217714 - farlands$minSy;
            var fromArr = idx >= 0 && idx < this.sections.length ? this.sections[idx] : null;
            System.out.println("[YWIN2] ctor start=" + start + " center=" + center
                + " minSy=" + farlands$minSy + " idx=" + idx
                + " map=" + (fromMap == null ? "null" : fromMap.getBlockState(8, 8, 8))
                + " arr=" + (fromArr == null ? "null" : fromArr.getBlockState(8, 8, 8)));
            System.out.flush();
        }
    }

    /**
     * Rebuild the page containing {@code centerSectionY} (dimension band for
     * normal Y). Pages are page-aligned 384-block pages; the very top page is
     * clamped so the sections above the last aligned page are still covered.
     */
    @Unique
    private void farlands$applyWindow(int centerSectionY) {
        int len = this.sections.length;
        int dimensionMin = this.levelHeightAccessor.getMinSectionY();
        int dimensionMax = this.levelHeightAccessor.getMaxSectionY();
        if (len == dimensionMax - dimensionMin + 1
            && centerSectionY >= dimensionMin && centerSectionY <= dimensionMax) {
            // Normal world: the page is the dimension band itself.
            farlands$applyWindowStart(dimensionMin);
            return;
        }
        farlands$applyWindowStart(VerticalBounds.pageStart(centerSectionY, len));
    }

    /** Rebuild the window at an explicit (already clamped) page start. */
    @Unique
    private void farlands$applyWindowStart(int minSy) {
        farlands$allSections();
        int len = this.sections.length;
        int half = (len - 1) / 2;
        int maxStart = VerticalBounds.MAX_PLAYABLE_SECTION - len + 1;
        if (minSy > maxStart) {
            minSy = maxStart;
        }
        if (minSy < VerticalBounds.MIN_PLAYABLE_SECTION) {
            minSy = VerticalBounds.MIN_PLAYABLE_SECTION;
        }
        int maxSy = minSy + len - 1;
        this.farlands$minSy = minSy;
        this.farlands$maxSy = maxSy;
        this.farlands$centerY = WideSectionCoord512.of((long) minSy + half);
        this.farlands$windowReady = true;
        if (Boolean.getBoolean("farlands.y.diag")) {
            System.out.println("[YWIN] page pos=" + chunkPos.x() + "," + chunkPos.z()
                + " band=" + minSy + ".." + maxSy + " arrayLen=" + this.sections.length);
            System.out.flush();
        }
        for (int i = 0; i < len; i++) {
            int sy = minSy + i;
            WideSectionKey2560 key = farlands$key(sy);
            LevelChunkSection section = farlands$allSections().get(key);
            if (section == null) {
                section = new LevelChunkSection(this.farlands$factory);
                this.farlands$allSections().put(key, section);
            }
            this.sections[i] = section;
        }
        farlands$evictFarPages();
    }

    /**
     * Bound the far-page memory. Normal-band sections are never evicted (block
     * entities/player builds live there); only far pages outside the current
     * window are dropped, oldest insertion first. A sidecar save keeps whatever
     * is still in the map; evicted pages are gone until regenerated.
     */
    @Unique
    private void farlands$evictFarPages() {
        Map<WideSectionKey2560, LevelChunkSection> all = farlands$allSections();
        if (all.size() <= farlands$MAX_SECTIONS) {
            return;
        }
        int dimensionMin = this.levelHeightAccessor.getMinSectionY();
        int dimensionMax = this.levelHeightAccessor.getMaxSectionY();
        int before = all.size();
        java.util.Iterator<Map.Entry<WideSectionKey2560, LevelChunkSection>> it =
            all.entrySet().iterator();
        while (it.hasNext() && all.size() > farlands$MAX_SECTIONS) {
            Map.Entry<WideSectionKey2560, LevelChunkSection> entry = it.next();
            int sy = (int) entry.getKey().y().toBigInteger().longValueExact();
            if (sy >= this.farlands$minSy && sy <= this.farlands$maxSy) {
                continue; // current window
            }
            if (sy >= dimensionMin && sy <= dimensionMax) {
                continue; // normal band: block entities/builds
            }
            it.remove();
        }
        int evicted = before - all.size();
        if (evicted > 0 && !this.farlands$evictLogged) {
            this.farlands$evictLogged = true;
            System.out.println("[WSECT] evicted=" + evicted + " size="
                + all.size() + " max=" + farlands$MAX_SECTIONS
                + " band=" + this.farlands$minSy + ".." + this.farlands$maxSy);
            System.out.flush();
        }
    }

    @Inject(method = "getMinY", at = @At("HEAD"), cancellable = true)
    private void farlands$windowMinY(CallbackInfoReturnable<Integer> cir) {
        if (farlands$windowReady && Boolean.getBoolean("farlands.y.window")) {
            cir.setReturnValue(farlands$minSy * 16);
        }
    }

    @Inject(method = "getSection", at = @At("HEAD"), cancellable = true)
    private void farlands$getSection(int sectionIndex, CallbackInfoReturnable<LevelChunkSection> cir) {
        if (!Boolean.getBoolean("farlands.y.window")
            && !Boolean.getBoolean("farlands.y.abs")) {
            return;
        }
        // Called from ChunkAccess/ProtoChunk constructors before our mixin fields
        // are initialised (ChunkMap.createEmptyChunk). Let vanilla answer then.
        if (this.farlands$all == null || this.farlands$factory == null) {
            return;
        }
        if (sectionIndex < 0 || sectionIndex >= this.sections.length) {
            int absoluteSectionY = farlands$minSy + sectionIndex;
            // Any absolute section that exists in the wide map is readable, not
            // only the far ones: after a band move the normal-band sections (block
            // entities, player builds) are still in the map and must resolve.
            LevelChunkSection existing = farlands$allSections().get(
                farlands$key(WideSectionCoord512.of((long) absoluteSectionY)));
            cir.setReturnValue(existing != null ? existing : farlands$emptyViewSection());
            return;
        }
        int sy = farlands$minSy + sectionIndex;
        LevelChunkSection section = farlands$allSections().get(farlands$key(sy));
        if (Boolean.getBoolean("farlands.y.diag") && sy == 134217714) {
            System.out.println("[YSEC] index=" + sectionIndex + " sy=" + sy
                + " map=" + (section == null ? "null" : section.getBlockState(8, 8, 8))
                + " arr=" + (sectionIndex >= 0 && sectionIndex < this.sections.length
                    && this.sections[sectionIndex] != null
                    ? this.sections[sectionIndex].getBlockState(8, 8, 8) : "null"));
            System.out.flush();
        }
        if (section != null) {
            cir.setReturnValue(section);
        }
    }

    @Unique
    private LevelChunkSection farlands$emptyViewSection() {
        if (this.farlands$emptyView == null) {
            this.farlands$emptyView = new LevelChunkSection(this.farlands$factory);
        }
        return this.farlands$emptyView;
    }

    @Override
    public void farlands$moveWindowTo(int centerSectionY) {
        farlands$applyWindow(centerSectionY);
    }

    @Override
    public void farlands$moveWindowToStart(int startSectionY) {
        farlands$applyWindowStart(startSectionY);
    }

    @Override
    public Map<WideSectionKey2560, LevelChunkSection> farlands$allSections() {
        if (this.farlands$all == null) {
            this.farlands$all = new LinkedHashMap<>();
        }
        return farlands$all;
    }

    @Override
    public WideSectionCoord512 farlands$windowCenterY() {
        return farlands$centerY;
    }

    @Override
    public int farlands$windowMinSectionY() {
        return farlands$minSy;
    }

    @Override
    public int farlands$windowMaxSectionY() {
        return farlands$maxSy;
    }

    @Override
    public WideSectionKey2560 farlands$key(int sectionY) {
        return farlands$key(WideSectionCoord512.of((long) sectionY));
    }

    @Override
    public WideSectionKey2560 farlands$key(WideSectionCoord512 sectionY) {
        // 512-bit Y identity. The scalar here is exact for every reachable value
        // (block Y is bounded by double precision), so limb(0) is lossless.
        return WideSectionKey2560.of(chunkPos.xLong(),
            sectionY.toBigInteger().longValueExact(), chunkPos.zLong());
    }

    @Override
    public LevelChunkSection farlands$sectionAt(int absoluteSectionY) {
        return farlands$sectionAt(WideSectionCoord512.of((long) absoluteSectionY));
    }

    @Override
    public LevelChunkSection farlands$sectionAt(WideSectionCoord512 absoluteSectionY) {
        return farlands$allSections().computeIfAbsent(farlands$key(absoluteSectionY),
            ignored -> new LevelChunkSection(this.farlands$factory));
    }
}
