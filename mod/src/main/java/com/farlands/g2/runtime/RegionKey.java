package com.farlands.g2.runtime;

/** Exact region coordinate key (no digest truncation, no collision alias). */
public record RegionKey(long rx, long rz) {
}
