package com.farlands.g2.mixin;

import com.farlands.g2.client.RenderKeys;
import com.farlands.g2.runtime.section.SectionKey;
import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes {@code ViewArea} lookups through the wide-key table so a section at a
 * far Y can be found even though its packed long has only 6 Y bits. Lookups for
 * longs that never went through the render pipeline fall back to the vanilla
 * storage, keeping normal worlds unchanged.
 */
@Mixin(ViewArea.class)
public abstract class ViewAreaKeyMixin {

    @Shadow @Final private RotatingSectionStorage<SectionRenderDispatcher.RenderSection> sections;

    /**
     * A new ViewArea owns a new set of {@code RenderSection} instances. Drop the
     * previous world's registrations so a lookup can never return a stale section
     * (already compiled) from an earlier ViewArea.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void farlands$resetKeys(CallbackInfo ci) {
        if (!Boolean.getBoolean("farlands.render.reentryFix")) {
            return;
        }
        RenderKeys.clear();
        // The old visible list points at this (replaced) ViewArea's predecessors.
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && mc.levelRenderer != null) {
            mc.levelRenderer.clearVisibleSections();
        }
        System.out.println("[YF-VA] new ViewArea, RenderKeys cleared");
        System.out.flush();
    }

    @Overwrite
    public SectionRenderDispatcher.RenderSection getRenderSection(long sectionNode) {
        SectionRenderDispatcher.RenderSection wide = RenderKeys.get(sectionNode);
        return wide != null ? wide : this.sections.getValue(sectionNode);
    }

    @Overwrite
    public SectionRenderDispatcher.RenderSection getRenderSectionAt(BlockPos pos) {
        SectionKey key = SectionKey.of(pos.getX() >> 4,
            (int) Math.floorDiv(pos.getLongY(), 16L), pos.getZ() >> 4);
        SectionRenderDispatcher.RenderSection wide = RenderKeys.get(key);
        return wide != null ? wide : this.sections.getValueAt(pos);
    }
}
