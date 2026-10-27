package com.farlands.g2.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.RenderRegionCache;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Diagnostic ({@code -Dfarlands.diag.seccmp=true}): does the region the section
 * compiler sees return the client chunk's own blocks?
 *
 * <p>For every section of the player's chunk column it builds a fresh
 * {@code RenderSectionRegion} (the same object {@code SectionCompiler} gets) and
 * compares 64 sampled block states against {@code level.getBlockState}. A
 * non-zero mismatch means the mesh is compiled from another section's copy -
 * the "mesh written to the wrong section" symptom - independent of whether a
 * mesh exists.</p>
 */
@Mixin(LevelRenderer.class)
public class RenderRegionCmpDiagMixin {

    /**
     * Wall-clock gate, not a frame counter: {@code RenderDiagMixin} also merges a
     * static {@code farlands$frames} into LevelRenderer, so a per-frame counter
     * here shares that value and may never hit the modulo.
     */
    @Unique private static long farlands$lastCompare;

    @Inject(method = "endFrame", at = @At("HEAD"))
    private void farlands$compare(CallbackInfo ci) {
        if (!Boolean.getBoolean("farlands.diag.seccmp")) {
            return;
        }
        long now = net.minecraft.util.Util.getMillis();
        if (now - farlands$lastCompare < 10000L) {
            return;
        }
        farlands$lastCompare = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        if (Math.abs(px) < 1.0E9 && Math.abs(pz) < 1.0E9) {
            return;
        }
        int cx = (int) Math.floorDiv((long) Math.floor(px), 16L);
        int cz = (int) Math.floorDiv((long) Math.floor(pz), 16L);
        var chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
        if (chunk == null) {
            return;
        }
        RenderRegionCache cache = new RenderRegionCache();
        int totalSamples = 0;
        int totalMismatch = 0;
        int totalNonAir = 0;
        int sectionsWithMismatch = 0;
        StringBuilder detail = new StringBuilder();
        int minY = chunk.getMinSectionY();
        var chunkSections = chunk.getSections();
        for (int i = 0; i < chunkSections.length; i++) {
            if (chunkSections[i] == null) {
                continue;
            }
            int sy = minY + i;
            long node = SectionPos.asLong(cx, sy, cz);
            if (SectionPos.x(node) != cx || SectionPos.z(node) != cz) {
                continue; // packed key cannot express this column
            }
            var region = cache.createRegion(mc.level, node);
            int mismatch = 0;
            int nonAir = 0;
            for (int t = 0; t < 64; t++) {
                long wx = (long) cx * 16L + (t * 7L & 15L);
                long wy = (long) sy * 16L + (t * 3L & 15L);
                long wz = (long) cz * 16L + (t * 11L & 15L);
                BlockPos pos = new BlockPos(wx, wy, wz);
                BlockState fromRegion = region.getBlockState(pos);
                BlockState fromChunk = mc.level.getBlockState(pos);
                totalSamples++;
                if (!fromRegion.equals(fromChunk)) {
                    mismatch++;
                    if (mismatch == 1) {
                        detail.append(" first=").append(pos.getLongX()).append(",")
                            .append(pos.getLongY()).append(",").append(pos.getLongZ())
                            .append(" region=").append(fromRegion).append(" chunk=").append(fromChunk);
                    }
                }
                if (!fromChunk.isAir()) {
                    nonAir++;
                }
            }
            totalMismatch += mismatch;
            totalNonAir += nonAir;
            if (mismatch > 0) {
                sectionsWithMismatch++;
                detail.append(" [sy=").append(sy).append(" mismatch=").append(mismatch)
                    .append("/64 nonAir=").append(nonAir).append("]");
            }
        }
        System.out.println("[SECCMP] chunk=" + cx + "," + cz
            + " samples=" + totalSamples + " nonAir=" + totalNonAir
            + " mismatch=" + totalMismatch + " sectionsWithMismatch=" + sectionsWithMismatch
            + detail);
        System.out.flush();
    }
}
