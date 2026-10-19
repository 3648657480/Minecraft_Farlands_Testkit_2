package com.farlands.g2.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelRenderer.class)
public class LevelRendererCamWrapMixin {

    @Unique private static final boolean farlands$traceEnabled = Boolean.getBoolean("farlands.trace.seam");

    @Unique private static long farlands$lastOutlineLog;

    @Unique
    private static double farlands$wrap(double v) {
        if (com.farlands.g2.util.FarProjection.isFarEngine()) {
            return v; // real coordinates must not be wrapped back
        }
        double floor = Math.floor(v);
        return (double)(int)(long)floor + (v - floor);
    }

    @Unique
    private static double farlands$wrapOutline(double v) {
        if (!com.farlands.g2.util.FarProjection.isFarEngine()) {
            return farlands$wrap(v);
        }
        if (v >= 2_147_483_648.0) {
            return v - 4_294_967_296.0;
        }
        if (v < -2_147_483_648.0) {
            return v + 4_294_967_296.0;
        }
        return v;
    }

    @ModifyVariable(method = "submitBlockOutline", at = @At("STORE"), ordinal = 0)
    private Vec3 farlands$wrapOutlineCam(Vec3 cameraPos) {
        Vec3 out = new Vec3(farlands$wrapOutline(cameraPos.x), farlands$wrapOutline(cameraPos.y),
            farlands$wrapOutline(cameraPos.z));
        long now = System.currentTimeMillis();
        if (farlands$traceEnabled && now - farlands$lastOutlineLog >= 1000L) {
            farlands$lastOutlineLog = now;
            System.out.println("[G2OUT] cam=" + cameraPos + " -> " + out);
            System.out.flush();
        }
        return out;
    }

    @ModifyVariable(method = "submitBlockEntities", at = @At("STORE"), ordinal = 0)
    private double farlands$wrapBlockCamX(double camX) {
        return farlands$wrap(camX);
    }

    @ModifyVariable(method = "submitBlockEntities", at = @At("STORE"), ordinal = 1)
    private double farlands$wrapBlockCamY(double camY) {
        return farlands$wrap(camY);
    }

    @ModifyVariable(method = "submitBlockEntities", at = @At("STORE"), ordinal = 2)
    private double farlands$wrapBlockCamZ(double camZ) {
        return farlands$wrap(camZ);
    }

    @ModifyVariable(method = "submitBlockDestroyAnimation", at = @At("STORE"), ordinal = 0)
    private double farlands$wrapDestroyCamX(double camX) {
        return farlands$wrap(camX);
    }

    @ModifyVariable(method = "submitBlockDestroyAnimation", at = @At("STORE"), ordinal = 1)
    private double farlands$wrapDestroyCamY(double camY) {
        return farlands$wrap(camY);
    }

    @ModifyVariable(method = "submitBlockDestroyAnimation", at = @At("STORE"), ordinal = 2)
    private double farlands$wrapDestroyCamZ(double camZ) {
        return farlands$wrap(camZ);
    }
}
