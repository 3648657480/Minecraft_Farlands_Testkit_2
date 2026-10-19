package com.farlands.g2.mixin;

import com.farlands.g2.util.WidePosRemap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Restores the wide block position of a server block update before it is
 * applied to the client level. The packet carries the int view only.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerWideMixin {

    @Redirect(method = "handleBlockUpdate",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;"
                + "setServerVerifiedBlockState(Lnet/minecraft/core/BlockPos;"
                + "Lnet/minecraft/world/level/block/state/BlockState;I)V"))
    private void farlands$remapUpdate(ClientLevel level, BlockPos pos, BlockState state, int flags) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            pos = WidePosRemap.remap(mc.player, pos);
        }
        level.setServerVerifiedBlockState(pos, state, flags);
    }
}
