package com.farlands.g2.runtime;

/**
 * "The player is in the far domain right now" flag, shared by both sides.
 *
 * <p>The far lighting strategy is a guard, not a widened light engine: far blocks
 * read as fully lit. That guard used to be decided by decoding the packed
 * {@code BlockPos} long, which is exactly what breaks at 2^31 blocks (the 26-bit
 * X/Z fields no longer hold the coordinate), so lighting fell through to the real
 * storage, which has no far data - the "messy shadows" symptom.</p>
 *
 * <p>Both the client (local frame established) and the server (far streaming
 * active) set this flag, and the light guard simply consults it.</p>
 */
public final class FarLightMode {

    private static volatile boolean active;

    private FarLightMode() {
    }

    public static boolean active() {
        return active;
    }

    public static void set(boolean value) {
        if (active != value) {
            active = value;
            System.out.println("[FARLIGHT] active=" + value);
            System.out.flush();
        }
    }
}
