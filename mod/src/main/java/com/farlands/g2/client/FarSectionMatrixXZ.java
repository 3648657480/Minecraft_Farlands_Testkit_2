package com.farlands.g2.client;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Far origins are camera-relative on X/Z but absolute on ordinary Y. The
 * vanilla section modelView already contains the camera translation, so for a
 * far section only the X/Z translation may be cancelled; Y must stay.
 */
public final class FarSectionMatrixXZ {

    private static final ThreadLocal<Boolean> FAR = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> FAR_Y = ThreadLocal.withInitial(() -> false);

    private FarSectionMatrixXZ() {
    }

    public static void mark(boolean far, boolean farY) {
        FAR.set(far);
        FAR_Y.set(farY);
    }

    public static Matrix4fc adjust(Matrix4fc modelView) {
        if (!Boolean.TRUE.equals(FAR.get())) {
            return modelView;
        }
        FAR.set(false);
        boolean farY = Boolean.TRUE.equals(FAR_Y.get());
        FAR_Y.set(false);
        Matrix4f copy = new Matrix4f(modelView);
        copy.setTranslation(0.0f, farY ? 0.0f : copy.m31(), 0.0f);
        return copy;
    }
}
