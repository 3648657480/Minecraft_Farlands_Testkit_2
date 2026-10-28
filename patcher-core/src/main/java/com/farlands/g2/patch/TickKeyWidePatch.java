package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Tick container keys from wide block positions.
 *
 * <p>{@code LevelTicks.schedule} and {@code SavedTick} key their chunk map with
 * {@code ChunkPos.pack(BlockPos)}, which goes through the int view. At or beyond
 * block 2^31 that wraps to the mirrored chunk, the container is not found and
 * the tick is dropped ("Trying to schedule tick in not loaded position"), which
 * is why fluids stop flowing east of the seam. Key from the wide X/Z instead.</p>
 */
public final class TickKeyWidePatch implements ClassPatch {

    @Override
    public boolean matches(String internalName) {
        return "net/minecraft/world/ticks/LevelTicks".equals(internalName)
            || "net/minecraft/world/ticks/SavedTick".equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        boolean changed = false;
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof MethodInsnNode mi
                    && "net/minecraft/world/level/ChunkPos".equals(mi.owner)
                    && "pack".equals(mi.name)
                    && "(Lnet/minecraft/core/BlockPos;)J".equals(mi.desc)) {
                    mi.owner = "com/farlands/g2/util/FarProjection";
                    mi.name = "wideChunkKey";
                    mi.desc = "(Lnet/minecraft/core/BlockPos;)J";
                    mi.itf = false;
                    changed = true;
                }
            }
        }
        if (!changed) {
            return original;
        }
        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }
}