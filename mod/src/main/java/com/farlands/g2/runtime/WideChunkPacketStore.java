package com.farlands.g2.runtime;

import com.scalarwide.mc.WideChunkPos;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * W1-4a: same-JVM wide chunk data bridge for the single-player client. The
 * server serialises a far chunk's sections with its wide ChunkPos; the client
 * probe reads them without going through the int-only ClientChunkCache. This is
 * a data-arrival step, not a renderer.
 */
public final class WideChunkPacketStore {

    public record Packet(WideChunkPos pos, int minSectionY, int sections, byte[] data) {
    }

    private static final Map<WideChunkPos, Packet> PACKETS = new ConcurrentHashMap<>();
    private static volatile long version;

    private WideChunkPacketStore() {
    }

    public static void put(Packet packet) {
        PACKETS.put(packet.pos(), packet);
        version++;
    }

    public static long version() {
        return version;
    }

    public static Packet get(WideChunkPos pos) {
        return PACKETS.get(pos);
    }

    public static int size() {
        return PACKETS.size();
    }

    public static Collection<Packet> all() {
        return PACKETS.values();
    }

    public static void clear() {
        PACKETS.clear();
        version++;
    }
}
