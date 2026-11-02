package com.farlands.g2.mixin;

import com.farlands.g2.client.FarSectionMatrixXZ;
import net.minecraft.client.renderer.DynamicUniforms;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Cancels only the X/Z camera translation for far sections (Y stays absolute). */
@Mixin(DynamicUniforms.ChunkSectionInfo.class)
public class ChunkSectionInfoXZMatrixMixin {

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, index = 1)
    private static Matrix4fc farlands$farXZMatrix(Matrix4fc modelView) {
        return FarSectionMatrixXZ.adjust(modelView);
    }
}
