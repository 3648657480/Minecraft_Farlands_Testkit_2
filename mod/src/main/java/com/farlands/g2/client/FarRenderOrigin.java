package com.farlands.g2.client;

import com.farlands.g2.runtime.section.WideOriginHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Patched {@code getRenderOrigin} target.
 *
 * <p>{@code terrain.vsh} computes</p>
 * <pre>
 * vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
 * </pre>
 * <p>so {@code ChunkPosition} is the <b>world</b> section origin and the shader
 * subtracts the camera itself. Returning a camera-relative origin here subtracts
 * the camera twice and shifts everything by the camera position (observed: not a
 * single section on screen at 2^31-1 even with 579 non-empty meshes).</p>
 *
 * <p>Below 2^31 the world origin fits the {@code ivec3} uniform, so the vanilla
 * value is handed back unchanged. Above it the int field cannot hold the origin;
 * that range still needs a shader-side solution and stays on the relative path.</p>
 */
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
        long x = holder.g2WideX();
        long y = holder.g2WideY();
        long z = holder.g2WideZ();
        var camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        // Whole-frame local space: GlobalSettingsFarCameraMixin pins CameraBlockPos
        // at 0, so ChunkPosition is measured from the camera's block corner.
        if (WideRenderBase.established()) {
            long cbx = (long) Math.floor(camera.x);
            long cby = (long) Math.floor(camera.y);
            long cbz = (long) Math.floor(camera.z);
            FarSectionMatrixXZ.mark(false, false);
            fallback.set((int) (x - cbx), (int) (y - cby), (int) (z - cbz));
            if (Boolean.getBoolean("farlands.trace.farorigin") && logged++ < 8) {
                System.out.println("[FORIG] wide=" + x + "," + y + "," + z
                    + " relBlock=" + (x - cbx) + "," + (y - cby) + "," + (z - cbz)
                    + " mode=frame");
                System.out.flush();
            }
            return fallback;
        }
        boolean fitsInt = x == (long) (int) x && y == (long) (int) y
            && z == (long) (int) z;
        if (fitsInt) {
            FarSectionMatrixXZ.mark(false, false);
            fallback.set((int) x, (int) y, (int) z);
            if (Boolean.getBoolean("farlands.trace.farorigin") && logged++ < 8) {
                System.out.println("[FORIG] world=" + x + "," + y + "," + z
                    + " cam=" + (long) camera.x + "," + (long) camera.z + " mode=world");
                System.out.flush();
            }
            return fallback;
        }
        boolean farY = true;
        FarSectionMatrixXZ.mark(true, farY);
        int relX = (int) (x - camera.x);
        int originY = (int) Math.floor(y - camera.y);
        int relZ = (int) (z - camera.z);
        if (Boolean.getBoolean("farlands.trace.farorigin") && logged++ < 8) {
            System.out.println("[FORIG] wide=" + x + "," + y + "," + z
                + " cam=" + (long) camera.x + "," + (long) camera.z
                + " rel=" + relX + "," + originY + "," + relZ + " mode=rel");
            System.out.flush();
        }
        fallback.set(relX, originY, relZ);
        return fallback;
    }
}
