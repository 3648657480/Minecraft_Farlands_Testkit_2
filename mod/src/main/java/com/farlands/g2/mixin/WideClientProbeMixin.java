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

    @Inject(method = "runTick", at = @At("TAIL"))
    private void farlands$logWideChunks(boolean advanceGameTime, CallbackInfo ci) {
        if (!Boolean.getBoolean("farlands.diag.clientWideChunk")) {
            return;
        }
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.level == null || WideChunkPacketStore.size() == 0) {
            return;
        }
        if (farlands$ticks++ % 40 != 0) {
            return;
        }
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
            com.farlands.g2.client.WideClientChunkStore.set(
                new com.farlands.g2.client.WideClientChunkStore.Data(
                    packet.pos(), minSy, sections));
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
        System.out.flush();
    }
}
