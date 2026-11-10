package com.farlands.g2.mixin;

import com.farlands.g2.runtime.FarChunkFeeder;
import java.math.BigInteger;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Production far-chunk streaming.
 *
 * <p>The far renderer needs the chunks around the player on the <b>client</b>, and
 * beyond the int chunk range the normal tracking path cannot carry them. Until now
 * the feeder was only started from the test harness
 * ({@code -Dfarlands.diag.clientWideChunk}), so a normal install had no far data at
 * all and rendered nothing - the dev runs only worked because they passed that flag.</p>
 *
 * <p>Start it <b>once</b> when the player enters the far domain. The feeder's own
 * {@code ringTick} reads the player's chunk every tick and re-centres the ring, so
 * restarting it per chunk crossing reset the whole window every time: chunks kept
 * unloading/reloading ("Trying to schedule tick in not loaded position") and the
 * client re-keyed its whole grid on every tick.</p>
 *
 * <p>Disable with {@code -Dfarlands.far.stream=false}.</p>
 */
@Mixin(MinecraftServer.class)
public class ServerFarStreamMixin {

    @Unique private static boolean farlands$streaming;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void farlands$followFarPlayer(BooleanSupplier hasTime, CallbackInfo ci) {
        if (!Boolean.parseBoolean(System.getProperty("farlands.far.stream", "true"))) {
            return;
        }
        MinecraftServer self = (MinecraftServer) (Object) this;
        var players = self.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        ServerPlayer player = players.get(0);
        double x = player.getX();
        double z = player.getZ();
        boolean far = Math.abs(x) > 1.6777216E7 || Math.abs(z) > 1.6777216E7;
        if (!far) {
            farlands$streaming = false;
            return;
        }
        if (farlands$streaming) {
            return;
        }
        farlands$streaming = true;
        long cx = Math.floorDiv((long) Math.floor(x), 16L);
        long cz = Math.floorDiv((long) Math.floor(z), 16L);
        if (!(player.level() instanceof ServerLevel level)) {
            farlands$streaming = false;
            return;
        }
        int radius = Integer.getInteger("farlands.far.pushRadius", 5);
        FarChunkFeeder.startFollow(level,
            com.scalarwide.mc.WideChunkPos.of(BigInteger.valueOf(cx), BigInteger.valueOf(cz)),
            radius);
        System.out.println("[FARSTREAM] follow started at " + cx + "," + cz
            + " radius=" + radius);
        System.out.flush();
    }
}
