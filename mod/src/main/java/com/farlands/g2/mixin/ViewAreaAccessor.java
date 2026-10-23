package com.farlands.g2.mixin;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Accessor for ViewArea's protected section lookup. */
@Mixin(ViewArea.class)
public interface ViewAreaAccessor {

    @Invoker("getRenderSection")
    SectionRenderDispatcher.RenderSection farlands$getRenderSection(long sectionNode);
}
