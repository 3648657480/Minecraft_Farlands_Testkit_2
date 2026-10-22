package com.farlands.g2.mixin;

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
 * Y-B step 1: make the chunk's section container wide-keyed.
 *
 * <p>Every section is also stored under its absolute {@link WideSectionKey2560}
 * (wide Y). For now the window equals the dimension band and the section array
 * keeps its original size, so behaviour is unchanged; the point of this step is
 * to prove the key identity and the map mirror on a normal world before the
 * window is allowed to move (Y-C).</p>
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

    @Inject(
        method = "<init>(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/chunk/PalettedContainerFactory;J[Lnet/minecraft/world/level/chunk/LevelChunkSection;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V",
        at = @At("RETURN"))
    private void farlands$initWindow(ChunkPos pos, UpgradeData upgradeData, LevelHeightAccessor heightAccessor,
        PalettedContainerFactory containerFactory, long inhabitedTime, LevelChunkSection[] sections,
        BlendingData blendingData, CallbackInfo ci) {
        this.farlands$minSy = heightAccessor.getMinSectionY();
        this.farlands$maxSy = heightAccessor.getMaxSectionY();
        this.farlands$centerY = WideSectionCoord512.of((long) (farlands$minSy + farlands$maxSy) / 2L);
        for (int i = 0; i < this.sections.length; i++) {
            int sy = farlands$minSy + i;
            this.farlands$all.put(farlands$key(sy), this.sections[i]);
        }
    }

    @Inject(method = "getSection", at = @At("HEAD"), cancellable = true)
    private void farlands$getSection(int sectionIndex, CallbackInfoReturnable<LevelChunkSection> cir) {
        if (!Boolean.getBoolean("farlands.y.window")) {
            return;
        }
        int sy = farlands$minSy + sectionIndex;
        LevelChunkSection section = farlands$all.get(farlands$key(sy));
        if (section != null) {
            cir.setReturnValue(section);
        }
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
