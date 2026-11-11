package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LayerLightSectionStorage.class)
public class LayerLightSectionStorageMixin {

    @Unique
    private static boolean farlands$isFar(int v) {
        // In the far domain the packed BlockPos long cannot be decoded: asLong keeps
        // X/Z in 26-bit fields, so at 2^31 blocks the unpacked value is garbage and
        // the threshold below stops matching. The far-domain flag is authoritative
        // there; the magnitude check stays as the normal-range fallback.
        if (com.farlands.g2.runtime.FarLightMode.active()) {
            return true;
        }
        return Math.abs((long) v) > 33_554_000L;
    }

    @Inject(method = "getStoredLevel", at = @At("HEAD"), cancellable = true)
    private void farlands$guardGet(long blockNode, CallbackInfoReturnable<Integer> cir) {
        if (farlands$isFar(BlockPos.getX(blockNode)) || farlands$isFar(BlockPos.getZ(blockNode))) {
            cir.setReturnValue(15);
        }
    }

    @Redirect(method = "getStoredLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/chunk/DataLayer;get(III)I"))
    private int farlands$safeGet(DataLayer layer, int x, int y, int z) {
        return layer == null ? 15 : layer.get(x, y, z);
    }

    @Inject(method = "setStoredLevel", at = @At("HEAD"), cancellable = true)
    private void farlands$guardSet(long blockNode, int level, CallbackInfo ci) {
        if (farlands$isFar(BlockPos.getX(blockNode)) || farlands$isFar(BlockPos.getZ(blockNode))) {
            ci.cancel();
        }
    }

    @Redirect(method = "setStoredLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/chunk/DataLayer;set(IIII)V"))
    private void farlands$safeSet(DataLayer layer, int x, int y, int z, int value) {
        if (layer != null) {
            layer.set(x, y, z, value);
        }
    }

    @Inject(method = "updateSectionStatus(JZ)V", at = @At("HEAD"), cancellable = true)
    private void farlands$guardUpdate(long sectionNode, boolean flag, CallbackInfo ci) {
        if (Math.abs((long) SectionPos.x(sectionNode)) >= 2_095_000L
            || Math.abs((long) SectionPos.z(sectionNode)) >= 2_095_000L) {
            ci.cancel();
        }
    }
}
