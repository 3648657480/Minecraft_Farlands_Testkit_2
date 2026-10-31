package com.farlands.g2.client;

import com.farlands.g2.runtime.section.WideOriginHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Patched getRenderOrigin target: per-section camera-relative int origin for far sections. */
public final class FarRenderOrigin {

    private FarRenderOrigin() {
    }

    public static BlockPos get(WideOriginHolder holder, BlockPos.MutableBlockPos fallback) {
        if (!holder.g2HasWideOrigin()) {
            return fallback;
        }
        var camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        fallback.set((int) (holder.g2WideX() - camera.x),
            (int) (holder.g2WideY() - camera.y),
            (int) (holder.g2WideZ() - camera.z));
        return fallback;
    }
}
