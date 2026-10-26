package com.farlands.g2.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.SectionUpdateTracker;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Far-domain compile gate + light fill.
 *
 * <p>Vanilla's neighbour/light predicate is int-domain and rejects far sections
 * whose chunks exist, leaving interior holes. For far sections the gate is
 * bypassed and missing light is filled with sky light 14 (same policy as the
 * far-Y band bridge), so terrain is lit instead of black. Normal coordinates
 * keep the vanilla predicate.</p>
 */
@Mixin(SectionUpdateTracker.class)
public class SectionUpdateTrackerGateMixin {

    private static final Set<Long> FARLANDS_FILLED = ConcurrentHashMap.newKeySet();

    private static boolean farlands$far(long node) {
        return Math.abs((long) SectionPos.x(node)) > 1_000_000L
            || Math.abs((long) SectionPos.z(node)) > 1_000_000L;
    }

    @Inject(method = "hasAllNeighbors", at = @At("HEAD"), cancellable = true)
    private void farlands$farNeighbors(ClientLevel level, long sectionNode,
        CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.getBoolean("farlands.diag.forceNeighbors")) {
            cir.setReturnValue(true);
            return;
        }
        if (!farlands$far(sectionNode)) {
            return;
        }
        if (FARLANDS_FILLED.add(sectionNode)) {
            var light = level.getChunkSource().getLightEngine();
            light.queueSectionData(LightLayer.SKY, SectionPos.of(sectionNode), new DataLayer(14));
            light.queueSectionData(LightLayer.BLOCK, SectionPos.of(sectionNode), new DataLayer(0));
        }
        cir.setReturnValue(true);
    }
}