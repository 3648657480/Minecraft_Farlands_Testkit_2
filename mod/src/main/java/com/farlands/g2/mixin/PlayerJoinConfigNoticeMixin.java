package com.farlands.g2.mixin;

import com.farlands.g2.util.FarConfig;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In-game visibility for lenient-mode config warnings (UI/UX item): the
 * warnings used to go to the log only - the player never saw them. On join,
 * repeat them in chat (bounded to 5 lines + one summary).
 */
@Mixin(PlayerList.class)
public abstract class PlayerJoinConfigNoticeMixin {

    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void farlands$configNotice(Connection connection, ServerPlayer player,
                                       CommonListenerCookie cookie, CallbackInfo ci) {
        var warnings = FarConfig.warnings();
        if (warnings.isEmpty()) {
            return;
        }
        player.sendSystemMessage(Component.literal("[FarLands] lenient mode: "
            + warnings.size() + " invalid config value(s), reverted to defaults"), false);
        int shown = 0;
        for (String w : warnings) {
            player.sendSystemMessage(Component.literal("[FarLands] " + w), false);
            if (++shown >= 5) {
                break;
            }
        }
    }
}
