package com.farlands.g2.client;

import com.farlands.g2.runtime.section.WideOriginHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Patched getRenderOrigin target: per-section camera-relative int origin for far sections. */
public final class FarRenderOrigin {

    private static int logged;

    private FarRenderOrigin() {
    }

    public static BlockPos get(WideOriginHolder holder, BlockPos.MutableBlockPos fallback) {
        if (!holder.g2HasWideOrigin()) {
            if (Boolean.getBoolean("farlands.diag.origin") && logged++ < 20) {
                System.out.println("[WIDEORIGIN] no-wide class=" + holder.getClass().getName());
                System.out.flush();
            }
            FarSectionMatrixXZ.mark(false, false);
            return fallback;
        }
        if (Boolean.getBoolean("farlands.diag.origin") && logged++ < 20) {
            System.out.println("[WIDEORIGIN] get x=" + holder.g2WideX()
                + " y=" + holder.g2WideY() + " z=" + holder.g2WideZ());
            System.out.flush();
        }
        var camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        long y = holder.g2WideY();
        boolean farY = y != (long) (int) y;
        FarSectionMatrixXZ.mark(true, farY);
        int originY = farY ? (int) (y - camera.y) : (int) y;
        fallback.set((int) (holder.g2WideX() - camera.x),
            originY,
            (int) (holder.g2WideZ() - camera.z));
        return fallback;
    }
}
