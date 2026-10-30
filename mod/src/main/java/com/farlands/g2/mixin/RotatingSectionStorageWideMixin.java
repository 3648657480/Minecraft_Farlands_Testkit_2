package com.farlands.g2.mixin;

import com.farlands.g2.runtime.section.WideSectionAnchor;
import java.math.BigInteger;
import java.util.Objects;
import net.minecraft.client.RotatingSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * S2b1: keep the exact section anchor on the grid itself. The vanilla int
 * centre remains a local layout view; the wide anchor is what keys cells.
 */
@Mixin(RotatingSectionStorage.class)
public class RotatingSectionStorageWideMixin implements WideSectionAnchor {

    @Unique private BigInteger farlands$anchorX;
    @Unique private BigInteger farlands$anchorZ;

    @Override
    public boolean farlands$setWideAnchor(BigInteger sectionX, BigInteger sectionZ) {
        if (Objects.equals(this.farlands$anchorX, sectionX)
            && Objects.equals(this.farlands$anchorZ, sectionZ)) {
            return false;
        }
        this.farlands$anchorX = sectionX;
        this.farlands$anchorZ = sectionZ;
        return true;
    }

    @Override
    public BigInteger farlands$wideAnchorX() {
        return this.farlands$anchorX;
    }

    @Override
    public BigInteger farlands$wideAnchorZ() {
        return this.farlands$anchorZ;
    }
}
