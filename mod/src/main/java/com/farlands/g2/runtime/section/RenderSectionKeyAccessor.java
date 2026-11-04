package com.farlands.g2.runtime.section;



/** Duck interface for the wide key added to {@code RenderSection}. */
public interface RenderSectionKeyAccessor {

    SectionKey farlands$getKey();

    void farlands$setKey(SectionKey key);

    /** The sectionNode this key was last assigned for. */
    long farlands$keyNode();

    /** Wide section origin in blocks; not an int view. */
    long farlands$wideOriginX();

    long farlands$wideOriginY();

    long farlands$wideOriginZ();
}
