package com.farlands.g2.mixin;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientChunkCache.class)
public class ClientChunkCacheDiagMixin {

    @Unique private static final boolean farlands$traceEnabled = Boolean.getBoolean("farlands.trace.seam");

    @Unique private static long farlands$lastRecv;
    @Unique private static long farlands$lastGet;
    @Unique private static long farlands$lastView;

    @Unique
    private static boolean farlands$near(int x) {
        return farlands$traceEnabled && (Math.abs((long) x - 134_217_728L) <= 2
            || Math.abs((long) x + 134_217_728L) <= 2);
    }

    @Unique
    private static String farlands$playerChunk() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? "null" : mc.player.chunkPosition().toString();
    }

    @Inject(method = "updateViewCenter", at = @At("HEAD"))
    private void farlands$view(int x, int z, CallbackInfo ci) {
        if (!farlands$near(x)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - farlands$lastView < 500L) {
            return;
        }
        farlands$lastView = now;
        System.out.println("[CCH] viewCenter=" + x + "," + z + " playerChunk=" + farlands$playerChunk());
        System.out.flush();
    }

    @Inject(method = "replaceWithPacketData", at = @At("HEAD"))
    private void farlands$recv(int x, int z, FriendlyByteBuf buf,
        Map<Heightmap.Types, long[]> heightmaps,
        Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer,
        CallbackInfoReturnable<LevelChunk> cir) {
        if (!farlands$near(x)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - farlands$lastRecv < 500L) {
            return;
        }
        farlands$lastRecv = now;
        System.out.println("[CCH] recv=" + x + "," + z + " playerChunk=" + farlands$playerChunk());
        System.out.flush();
    }

    @Inject(method = "replaceWithPacketData", at = @At("RETURN"))
    private void farlands$recvDone(int x, int z, FriendlyByteBuf buf,
        Map<Heightmap.Types, long[]> heightmaps,
        Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer,
        CallbackInfoReturnable<LevelChunk> cir) {
        if (!farlands$near(x)) {
            return;
        }
        LevelChunk c = cir.getReturnValue();
        BlockPos p = new BlockPos(-2_147_483_648, 67, 0);
        System.out.println("[CCH] recvDone=" + x + "," + z + " -> "
            + (c == null ? "null" : c.getPos() + " state="
                + c.getBlockState(p).getBlock().getDescriptionId()));
        System.out.flush();
    }

    @Inject(method = "getChunk", at = @At("RETURN"))
    private void farlands$get(int x, int z, ChunkStatus status, boolean load,
        CallbackInfoReturnable<LevelChunk> cir) {
        if (!farlands$near(x)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - farlands$lastGet < 250L) {
            return;
        }
        farlands$lastGet = now;
        LevelChunk c = cir.getReturnValue();
        BlockPos p = new BlockPos(-2_147_483_648, 67, 0);
        System.out.println("[CCH] get=" + x + "," + z + " -> "
            + (c == null ? "null" : (c.getPos() + " empty=" + c.isEmpty()
                + " state=" + c.getBlockState(p).getBlock().getDescriptionId()))
            + " playerChunk=" + farlands$playerChunk());
        System.out.flush();
    }
}
