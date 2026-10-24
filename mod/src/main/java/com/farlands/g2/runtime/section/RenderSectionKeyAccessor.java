package com.farlands.g2.runtime.section;



/** Duck interface for the wide key added to {@code RenderSection}. */
public interface RenderSectionKeyAccessor {

    SectionKey farlands$getKey();

    void farlands$setKey(SectionKey key);
}
