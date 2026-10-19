package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Block shapes are built from int BlockPos, so east of 2^31 they live in the
 * negative int band while the camera/ray is at +2^31. Fold BOTH ray endpoints
 * only when they are on the same far side; folding just one endpoint makes the
 * ray span ~4.29G and traverseBlocks freezes the client. A ray that straddles
 * the seam is left in real coordinates (west blocks still hit; east blocks are
 * reached when standing on the east side).
 */
@Mixin(ClipContext.class)
public class ClipContextWideMixin implements com.farlands.g2.runtime.WideClipCoordinates {

    @Shadow private Vec3 from;
    @Shadow private Vec3 to;
    @Shadow @Final private ClipContext.Block block;
    @Shadow @Final private ClipContext.Fluid fluid;
    @Shadow @Final private CollisionContext collisionContext;

    @Override
    public Vec3 farlands$getRawFrom() {
        return this.from;
    }

    @Override
    public ClipContext.Block farlands$getBlockType() {
        return this.block;
    }

    @Override
    public ClipContext.Fluid farlands$getFluidType() {
        return this.fluid;
    }

    @Override
    public CollisionContext farlands$getCollisionContext() {
        return this.collisionContext;
    }

    private static long farlands$lastClipLog;
    private static long farlands$lastShapeLog;
    private static int farlands$shapeBurst;

    @Unique private static final boolean farlands$traceEnabled = Boolean.getBoolean("farlands.trace.seam");

    private static boolean farlands$nearSeam(double x) {
        return farlands$traceEnabled && (x > 2_147_483_600.0 || x < -2_147_483_600.0);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void farlands$traceClip(CallbackInfo ci) {
        if (!farlands$nearSeam(this.from.x) && !farlands$nearSeam(this.to.x)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - farlands$lastClipLog < 500L) {
            return;
        }
        farlands$lastClipLog = now;
        Vec3 f = this.getFrom();
        Vec3 t = this.getTo();
        System.out.println("[CLIP] rawFrom=" + this.from + " rawTo=" + this.to
            + " foldedFrom=" + f + " foldedTo=" + t
            + " floorX=" + Mth.floor(f.x) + " ceilX=" + Mth.ceil(t.x)
            + " floorZ=" + Mth.floor(f.z) + " ceilZ=" + Mth.ceil(t.z));
        System.out.flush();
    }

    @Inject(method = "getBlockShape", at = @At("RETURN"))
    private void farlands$traceShape(BlockState state, BlockGetter level, BlockPos pos,
        CallbackInfoReturnable<VoxelShape> cir) {
        int x = pos.getX();
        if (!farlands$nearSeam(x)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - farlands$lastShapeLog >= 1000L) {
            farlands$lastShapeLog = now;
            farlands$shapeBurst = 0;
        }
        if (farlands$shapeBurst++ >= 50) {
            return;
        }
        VoxelShape shape = cir.getReturnValue();
        String shapeText = shape == null ? "null"
            : (shape.isEmpty() ? "empty" : shape.bounds().toString());
        System.out.println("[SHAPE] pos=" + pos + " longX=" + pos.getLongX()
            + " state=" + state.getBlock().getDescriptionId()
            + " shape=" + shapeText);
        System.out.flush();
    }

    private double farlands$offsetX() {
        if (com.farlands.g2.runtime.WideClipCoordinates.BYPASS.get()) {
            return 0.0;
        }
        if (this.from.x >= 2_147_483_648.0 && this.to.x >= 2_147_483_648.0) {
            return -4_294_967_296.0;
        }
        if (this.from.x < -2_147_483_648.0 && this.to.x < -2_147_483_648.0) {
            return 4_294_967_296.0;
        }
        return 0.0;
    }

    private double farlands$offsetZ() {
        if (com.farlands.g2.runtime.WideClipCoordinates.BYPASS.get()) {
            return 0.0;
        }
        if (this.from.z >= 2_147_483_648.0 && this.to.z >= 2_147_483_648.0) {
            return -4_294_967_296.0;
        }
        if (this.from.z < -2_147_483_648.0 && this.to.z < -2_147_483_648.0) {
            return 4_294_967_296.0;
        }
        return 0.0;
    }

    @Overwrite
    public Vec3 getFrom() {
        double ox = farlands$offsetX();
        double oz = farlands$offsetZ();
        return ox == 0.0 && oz == 0.0 ? this.from
            : new Vec3(this.from.x + ox, this.from.y, this.from.z + oz);
    }

    @Overwrite
    public Vec3 getTo() {
        double ox = farlands$offsetX();
        double oz = farlands$offsetZ();
        return ox == 0.0 && oz == 0.0 ? this.to
            : new Vec3(this.to.x + ox, this.to.y, this.to.z + oz);
    }
}
