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
        int relX = (int) (holder.g2WideX() - camera.x);
        int relZ = (int) (holder.g2WideZ() - camera.z);
        if (Boolean.getBoolean("farlands.trace.farorigin")
            && Math.abs(camera.x) < 3.0E9 && logged++ < 8) {
            System.out.println("[FORIG] wide=" + holder.g2WideX() + "," + y + ","
                + holder.g2WideZ() + " cam=" + (long) camera.x + "," + (long) camera.z
                + " rel=" + relX + "," + originY + "," + relZ);
            System.out.flush();
        }
        fallback.set(relX, originY, relZ);
        return fallback;
    }
}
