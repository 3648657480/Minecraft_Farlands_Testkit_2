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

    @Unique private final Map<WideSectionKey2560, LevelChunkSection> farlands$all = new LinkedHashMap<>();
    @Unique private WideSectionCoord512 farlands$centerY = WideSectionCoord512.ZERO;
    @Unique private int farlands$minSy;
    @Unique private int farlands$maxSy;
    @Unique private boolean farlands$windowReady;
    @Unique private PalettedContainerFactory farlands$factory;

    @Inject(
        method = "<init>(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/chunk/PalettedContainerFactory;J[Lnet/minecraft/world/level/chunk/LevelChunkSection;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V",
        at = @At("RETURN"))
    private void farlands$initWindow(ChunkPos pos, UpgradeData upgradeData, LevelHeightAccessor heightAccessor,
        PalettedContainerFactory containerFactory, long inhabitedTime, LevelChunkSection[] sections,
        BlendingData blendingData, CallbackInfo ci) {
        this.farlands$factory = containerFactory;
        int dimensionMin = heightAccessor.getMinSectionY();
        int dimensionMax = heightAccessor.getMaxSectionY();
        int center = (dimensionMin + dimensionMax) / 2;
        // Seed the wide map with the freshly allocated dimension band so a
        // same-band window reuses those section objects.
        for (int i = 0; i < this.sections.length; i++) {
            this.farlands$all.put(farlands$key(dimensionMin + i), this.sections[i]);
        }
        String override = System.getProperty("farlands.y.center");
        Integer perChunk = com.farlands.g2.runtime.YBandOverrides.get(chunkPos.widePos());
        if (perChunk != null) {
            center = perChunk;
        } else if (override != null && !override.isEmpty()) {
            center = Integer.parseInt(override);
        }
        farlands$applyWindow(center);
    }

    /** Rebuild the window (array + map entries) around an absolute int section Y. */
    @Unique
    private void farlands$applyWindow(int centerSectionY) {
        int len = this.sections.length;
        int half = (len - 1) / 2;
        int minSy = centerSectionY - half;
        int maxSy = minSy + len - 1;
        if (maxSy > VerticalBounds.MAX_PLAYABLE_SECTION) {
            maxSy = VerticalBounds.MAX_PLAYABLE_SECTION;
            minSy = maxSy - len + 1;
        }
        if (minSy < VerticalBounds.MIN_PLAYABLE_SECTION) {
            minSy = VerticalBounds.MIN_PLAYABLE_SECTION;
            maxSy = minSy + len - 1;
        }
        this.farlands$minSy = minSy;
        this.farlands$maxSy = maxSy;
        this.farlands$centerY = WideSectionCoord512.of((long) minSy + half);
        this.farlands$windowReady = true;
        if (Boolean.getBoolean("farlands.y.diag")) {
            System.out.println("[YWIN] init pos=" + chunkPos.x() + "," + chunkPos.z()
                + " dimSec=" + levelHeightAccessor.getMinSectionY() + ".." + levelHeightAccessor.getMaxSectionY()
                + " band=" + minSy + ".." + maxSy + " center=" + centerSectionY
                + " arrayLen=" + this.sections.length);
            System.out.flush();
        }
        for (int i = 0; i < len; i++) {
            int sy = minSy + i;
            WideSectionKey2560 key = farlands$key(sy);
            LevelChunkSection section = this.farlands$all.get(key);
            if (section == null) {
                section = new LevelChunkSection(this.farlands$factory);
                this.farlands$all.put(key, section);
            }
            this.sections[i] = section;
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
        if (!Boolean.getBoolean("farlands.y.window")) {
            return;
        }
        if (sectionIndex < 0 || sectionIndex >= this.sections.length) {
            // Outside the window: no section. Vanilla callers must see air, not
            // an ArrayIndexOutOfBoundsException.
            cir.setReturnValue(farlands$emptySection());
            return;
        }
        int sy = farlands$minSy + sectionIndex;
        LevelChunkSection section = farlands$all.get(farlands$key(sy));
        if (section != null) {
            cir.setReturnValue(section);
        }
    }

    @Unique
    private LevelChunkSection farlands$emptySection() {
        return new LevelChunkSection(this.farlands$factory);
    }

    @Override
    public void farlands$moveWindowTo(int centerSectionY) {
        farlands$applyWindow(centerSectionY);
    }

    @Override
    public Map<WideSectionKey2560, LevelChunkSection> farlands$allSections() {
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
        return WideSectionKey2560.of(chunkPos.xLong(), (long) sectionY, chunkPos.zLong());
    }
}
