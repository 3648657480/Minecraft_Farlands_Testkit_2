package com.farlands.g2.mixin;

import com.farlands.g2.runtime.FarChunkFeeder;
import java.math.BigInteger;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
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
 * <p>This hook makes the feeder follow the player whenever the player is in the far
 * domain, so a plain install behaves like the test runs. Disable with
 * {@code -Dfarlands.far.stream=false}.</p>
 */
@Mixin(MinecraftServer.class)
public class ServerFarStreamMixin {

    @Unique private static long farlands$lastChunkX = Long.MIN_VALUE;
    @Unique private static long farlands$lastChunkZ = Long.MIN_VALUE;

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
        if (Math.abs(x) <= 1.6777216E7 && Math.abs(z) <= 1.6777216E7) {
            farlands$lastChunkX = Long.MIN_VALUE;
            farlands$lastChunkZ = Long.MIN_VALUE;
            return;
        }
        long cx = Math.floorDiv((long) Math.floor(x), 16L);
        long cz = Math.floorDiv((long) Math.floor(z), 16L);
        if (cx == farlands$lastChunkX && cz == farlands$lastChunkZ) {
            return;
        }
        farlands$lastChunkX = cx;
        farlands$lastChunkZ = cz;
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        int radius = Integer.getInteger("farlands.far.pushRadius", 5);
        FarChunkFeeder.startFollow(level,
            com.scalarwide.mc.WideChunkPos.of(BigInteger.valueOf(cx), BigInteger.valueOf(cz)),
            radius);
        System.out.println("[FARSTREAM] follow player chunk " + cx + "," + cz
            + " radius=" + radius);
        System.out.flush();
    }
}
