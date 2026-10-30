package com.farlands.g2.runtime.section;

import java.math.BigInteger;

/** S2b1: a render section grid that can carry an exact wide X/Z anchor. */
public interface WideSectionAnchor {

    /** @return true when the anchor actually changed. */
    boolean farlands$setWideAnchor(BigInteger sectionX, BigInteger sectionZ);

    BigInteger farlands$wideAnchorX();

    BigInteger farlands$wideAnchorZ();
}
