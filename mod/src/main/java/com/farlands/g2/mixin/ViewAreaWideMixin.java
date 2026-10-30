package com.farlands.g2.mixin;

import com.farlands.g2.runtime.section.WideSectionAnchor;
import com.farlands.g2.runtime.section.WideViewArea;
import java.math.BigInteger;
import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** S2b1: route a wide anchor from ViewArea to its RotatingSectionStorage. */
@Mixin(ViewArea.class)
public class ViewAreaWideMixin implements WideViewArea {

    @Shadow private RotatingSectionStorage<SectionRenderDispatcher.RenderSection> sections;

    @Override
    public boolean farlands$setWideAnchor(BigInteger sectionX, BigInteger sectionZ) {
        if (this.sections instanceof WideSectionAnchor anchor) {
            return anchor.farlands$setWideAnchor(sectionX, sectionZ);
        }
        return false;
    }
}
