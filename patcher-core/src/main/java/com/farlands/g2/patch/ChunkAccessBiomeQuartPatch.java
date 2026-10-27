package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * {@code ChunkAccess.fillBiomesFromNoise} derives the biome quart origin from
 * {@code ChunkPos.getMinBlockX()/Z()} (int views). At chunk 2^27 the min block
 * is 2^31, which wraps to -2^31, so the chunk samples the biome field of the
 * negative mirror chunk. Compute the quart origin from the long min block.
 */
public final class ChunkAccessBiomeQuartPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/chunk/ChunkAccess";
    private static final String CHUNKPOS = "net/minecraft/world/level/ChunkPos";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        boolean changed = false;
        for (MethodNode m : node.methods) {
            if (!"fillBiomesFromNoise".equals(m.name)) {
                continue;
            }
            changed |= widenPair(m, "getMinBlockX", "getMinBlockXLong");
            changed |= widenPair(m, "getMinBlockZ", "getMinBlockZLong");
        }
        if (!changed) {
            return original;
        }
        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    /** Replaces {@code [ChunkPos.getMinBlockX()I, QuartPos.fromBlock(I)I]} with long shift. */
    private static boolean widenPair(MethodNode m, String intGetter, String longGetter) {
        for (AbstractInsnNode insn : m.instructions) {
            if (!(insn instanceof MethodInsnNode getter)
                || getter.getOpcode() != Opcodes.INVOKEVIRTUAL
                || !CHUNKPOS.equals(getter.owner)
                || !intGetter.equals(getter.name)
                || !"()I".equals(getter.desc)) {
                continue;
            }
            AbstractInsnNode next = getter.getNext();
            if (!(next instanceof MethodInsnNode fromBlock)
                || fromBlock.getOpcode() != Opcodes.INVOKESTATIC
                || !"net/minecraft/core/QuartPos".equals(fromBlock.owner)
                || !"fromBlock".equals(fromBlock.name)
                || !"(I)I".equals(fromBlock.desc)) {
                continue;
            }
            InsnList replacement = new InsnList();
            replacement.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNKPOS, longGetter, "()J", false));
            replacement.add(new LdcInsnNode(2));
            replacement.add(new InsnNode(Opcodes.LUSHR));
            replacement.add(new InsnNode(Opcodes.L2I));
            m.instructions.insert(getter, replacement);
            m.instructions.remove(getter);
            m.instructions.remove(fromBlock);
            return true;
        }
        return false;
    }

    @Override
    public String describe(String internalName) {
        return "ChunkAccessBiomeQuartPatch (long biome quart origin)";
    }
}