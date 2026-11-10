package com.farlands.g2.mixin;

import com.farlands.g2.client.WideRenderBase;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Whole-frame local space for the far domain.
 *
 * <p>{@code terrain.vsh} computes {@code Position + (ChunkPosition - CameraBlockPos)
 * + CameraOffset}, where {@code CameraBlockPos} is an {@code ivec3} built from
 * {@code Mth.floor(cameraPos)}. Once the camera passes 2^31 blocks that int no
 * longer holds the camera, so the whole frame has to work relative to it.</p>
 *
 * <p>Shifting the camera position handed to the uniform by its own block corner
 * makes {@code Mth.floor} produce 0 while {@code CameraOffset = block - cameraPos}
 * keeps exactly the same sub-block fraction. With {@code CameraBlockPos} pinned at
 * zero, every section's {@code ChunkPosition} becomes {@code worldOrigin - cameraBlock}
 * (see {@code FarRenderOrigin}), which always fits an int for sections inside the
 * render grid.</p>
 *
 * <p>Only the terrain shader reads {@code CameraBlockPos} in 26.2, so sky, clouds,
 * entities and particles are unaffected.</p>
 */
@Mixin(GlobalSettingsUniform.class)
public class GlobalSettingsFarCameraMixin {

    @ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true)
    private static Vec3 farlands$localCamera(Vec3 cameraPos) {
        if (cameraPos == null || !WideRenderBase.established()) {
            return cameraPos;
        }
        return new Vec3(cameraPos.x - Math.floor(cameraPos.x),
            cameraPos.y - Math.floor(cameraPos.y),
            cameraPos.z - Math.floor(cameraPos.z));
    }
}
