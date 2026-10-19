package com.farlands.g2.mixin;

import com.farlands.g2.util.WidePosRemap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Restores the wide target position of a block-action packet before the
 * server mutates the level. The packet only carries the int view, so the
 * block is reconstructed from the acting player's chunk and the low bits.
 */
@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeWideMixin {

    @Shadow
    @Final
    protected ServerPlayer player;

    @ModifyVariable(method = "destroyBlock", at = @At("HEAD"), argsOnly = true)
    private BlockPos farlands$remapDestroy(BlockPos pos) {
        return WidePosRemap.remap(this.player, pos);
    }

    @ModifyVariable(method = "handleBlockBreakAction", at = @At("HEAD"), argsOnly = true)
    private BlockPos farlands$remapBreakAction(BlockPos pos) {
        return WidePosRemap.remap(this.player, pos);
    }

    @ModifyVariable(method = "useItemOn", at = @At("HEAD"), argsOnly = true)
    private BlockHitResult farlands$remapUseOn(BlockHitResult hit) {
        BlockPos remapped = WidePosRemap.remap(this.player, hit.getBlockPos());
        if (remapped == hit.getBlockPos()) {
            return hit;
        }
        return new BlockHitResult(hit.getLocation(), hit.getDirection(), remapped, hit.isInside());
    }
}
