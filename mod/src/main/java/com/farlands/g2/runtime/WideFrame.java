package com.farlands.g2.runtime;

/**
 * Immutable per-generation coordinate frame. Every far-domain consumer asks the
 * frame for values instead of reconstructing them from int views or thread-local
 * state. A frame travels with the generation context (NoiseChunk, surface
 * context, carving context, feature context) and is never global.
 */
public record WideFrame(long chunkX, long chunkZ, long minBlockX, long minBlockZ) {

    public static WideFrame forChunk(long chunkX, long chunkZ) {
        return new WideFrame(chunkX, chunkZ, chunkX << 4, chunkZ << 4);
    }

    /** Real block X of a column offset inside this chunk (0..15). */
    public long blockX(int localX) {
        return this.minBlockX + localX;
    }

    /** Real block Z of a column offset inside this chunk (0..15). */
    public long blockZ(int localZ) {
        return this.minBlockZ + localZ;
    }

    /** Real block X of an arbitrary offset (may exceed one chunk). */
    public long blockXOffset(long offset) {
        return this.minBlockX + offset;
    }

    public long blockZOffset(long offset) {
        return this.minBlockZ + offset;
    }

    public static long quartOfBlock(long block) {
        return block >> 2;
    }

    public static long chunkOfBlock(long block) {
        return block >> 4;
    }
}