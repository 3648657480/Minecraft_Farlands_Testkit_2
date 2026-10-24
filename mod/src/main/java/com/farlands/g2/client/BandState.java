package com.farlands.g2.client;

/** Whether the client's current vertical window is a moved (far) band. */
public final class BandState {

    private static volatile boolean far;

    private BandState() {
    }

    public static void setFar(boolean value) {
        far = value;
    }

    public static boolean far() {
        return far;
    }
}
