package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Disables the world border for the far-domain engine.
 *
 * <p>The vanilla border is a 30M mechanic: beyond it block breaking/placing is
 * refused in {@code MultiPlayerGameMode}, chunks are not ticked, and entities
 * are clamped. G2 must be playable at 2^31, so every border query answers
 * "inside" and clamping is identity. The border still exists as a data object;
 * only its gameplay effect is removed.</p>
 */
@Mixin(WorldBorder.class)
public class WorldBorderDisabledMixin {

    @Inject(method = "isWithinBounds(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void farlands$inPos(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }

    @Inject(method = "isWithinBounds(Lnet/minecraft/world/phys/Vec3;)Z", at = @At("HEAD"), cancellable = true)
    private void farlands$inVec(Vec3 pos, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }

    @Inject(method = "isWithinBounds(Lnet/minecraft/world/level/ChunkPos;)Z", at = @At("HEAD"), cancellable = true)
    private void farlands$inChunk(ChunkPos pos, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }

    @Inject(method = "isWithinBounds(Lnet/minecraft/world/phys/AABB;)Z", at = @At("HEAD"), cancellable = true)
    private void farlands$inAabb(AABB box, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }

    @Inject(method = "isWithinBounds(DD)Z", at = @At("HEAD"), cancellable = true)
    private void farlands$inXZ(double x, double z, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }

    @Inject(method = "isWithinBounds(DDD)Z", at = @At("HEAD"), cancellable = true)
    private void farlands$inXYZ(double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }

    @Inject(method = "clampToBounds(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;",
        at = @At("HEAD"), cancellable = true)
    private void farlands$clampPos(BlockPos pos, CallbackInfoReturnable<BlockPos> cir) {
        cir.setReturnValue(pos);
    }

    @Inject(method = "clampToBounds(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/core/BlockPos;",
        at = @At("HEAD"), cancellable = true)
    private void farlands$clampVec(Vec3 pos, CallbackInfoReturnable<BlockPos> cir) {
        cir.setReturnValue(BlockPos.containing(pos.x, pos.y, pos.z));
    }

    @Inject(method = "clampToBounds(DDD)Lnet/minecraft/core/BlockPos;", at = @At("HEAD"), cancellable = true)
    private void farlands$clampXYZ(double x, double y, double z, CallbackInfoReturnable<BlockPos> cir) {
        cir.setReturnValue(BlockPos.containing(x, y, z));
    }

    @Inject(method = "getDistanceToBorder(Lnet/minecraft/world/entity/Entity;)D",
        at = @At("HEAD"), cancellable = true)
    private void farlands$distEntity(Entity entity, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(Double.MAX_VALUE);
    }

    @Inject(method = "getDistanceToBorder(DD)D", at = @At("HEAD"), cancellable = true)
    private void farlands$distXZ(double x, double z, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(Double.MAX_VALUE);
    }
}
