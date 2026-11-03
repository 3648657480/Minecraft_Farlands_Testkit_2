package com.farlands.g2.mixin;

import com.farlands.g2.runtime.WideChunkPacketStore;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * W1-4a probe: the integrated server pushes a far chunk's serialised sections
 * with its wide ChunkPos; this logs that the client side can read the wide key
 * without the int-only ClientChunkCache. No rendering.
 */
@Mixin(Minecraft.class)
public class WideClientProbeMixin {

    @Unique private static int farlands$ticks;
    @Unique private static long farlands$lastPacketVersion = -1;

    @Inject(method = "runTick", at = @At("TAIL"))
    private void farlands$logWideChunks(boolean advanceGameTime, CallbackInfo ci) {
        if (!Boolean.getBoolean("farlands.diag.clientWideChunk")) {
            return;
        }
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.level == null || WideChunkPacketStore.size() == 0) {
            return;
        }
        long packetVersion = WideChunkPacketStore.version();
        if (packetVersion == farlands$lastPacketVersion) {
            return;
        }
        if (farlands$ticks++ % 40 != 0) {
            return;
        }
        farlands$lastPacketVersion = packetVersion;
        boolean firstFill = com.farlands.g2.client.WideClientChunkStore.all().isEmpty();
        boolean changed = false;
        for (var packet : WideChunkPacketStore.all()) {
            var buffer = new net.minecraft.network.FriendlyByteBuf(
                io.netty.buffer.Unpooled.wrappedBuffer(packet.data()));
            int count = buffer.readVarInt();
            int minSy = buffer.readVarInt();
            var sections = new net.minecraft.world.level.chunk.LevelChunkSection[count];
            int nonAir = 0;
            for (int i = 0; i < count; i++) {
                var section = new net.minecraft.world.level.chunk.LevelChunkSection(
                    mc.level.palettedContainerFactory());
                section.read(buffer);
                sections[i] = section;
                if (!section.hasOnlyAir()) {
                    nonAir++;
                }
            }
            long beforeVersion = com.farlands.g2.client.WideClientChunkStore.version();
            com.farlands.g2.client.WideClientChunkStore.set(
                new com.farlands.g2.client.WideClientChunkStore.Data(
                    packet.pos(), minSy, sections));
            boolean newlyStored =
                com.farlands.g2.client.WideClientChunkStore.version() != beforeVersion;
            if (newlyStored) {
                changed = true;
            }
            if (newlyStored && !firstFill) {
                int sx = packet.pos().x().toBigInteger().intValue();
                int sz = packet.pos().z().toBigInteger().intValue();
                for (int i = 0; i < count; i++) {
                    mc.level.setSectionDirtyWithNeighbors(sx, minSy + i, sz);
                }
                System.out.println("[WIDECLIENT] dirty new window chunk " + packet.pos());
                System.out.flush();
            }
            System.out.println("[WIDECLIENT] pos=" + packet.pos()
                + " minSy=" + packet.minSectionY()
                + " sections=" + packet.sections()
                + " bytes=" + packet.data().length
                + " decodedSections=" + count + " minSyRead=" + minSy
                + " nonAirSections=" + nonAir);
            for (int i = 0; i < sections.length; i++) {
                if (!sections[i].hasOnlyAir()) {
                    System.out.println("[WIDESEC] y=" + (minSy + i)
                        + " center=" + sections[i].getBlockState(8, 8, 8));
                }
            }
        }
        if (mc.player != null) {
            java.math.BigInteger cx = java.math.BigInteger.valueOf(
                Math.floorDiv((long) Math.floor(mc.player.getX()), 16L));
            java.math.BigInteger cz = java.math.BigInteger.valueOf(
                Math.floorDiv((long) Math.floor(mc.player.getZ()), 16L));
            var removed = com.farlands.g2.client.WideClientChunkStore.retainAround(
                com.scalarwide.mc.WideChunkPos.of(cx, cz), 4);
            if (!removed.isEmpty()) {
                for (var data : removed) {
                    int sx = data.pos().x().toBigInteger().intValue();
                    int sz = data.pos().z().toBigInteger().intValue();
                    for (int i = 0; i < data.sections().length; i++) {
                        mc.level.setSectionDirtyWithNeighbors(sx, data.minSectionY() + i, sz);
                    }
                }
                System.out.println("[WIDECLIENT] pruned old window chunks=" + removed.size());
                System.out.flush();
            }
        }
        if (changed && firstFill) {
            mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options,
                mc.gameRenderer.mainCamera(), mc.getBlockColors());
            System.out.println("[WIDECLIENT] initial invalidateCompiledGeometry after store update");
            System.out.flush();
        }
        System.out.flush();
    }
}
