package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Key-consumer widening (W1/W4): {@code BulkSectionAccess.getSection} resolves
 * the owning chunk with {@code SectionPos.blockToSectionCoord(pos.getX())}.
 *
 * <p>At block 2^31 the int view wraps, so a feature reach that crosses the
 * boundary (oil/disk/… placement reading a neighbour) resolves a chunk that is
 * not in the {@code WorldGenRegion}; the WGR fallback then returns the center
 * chunk and {@code getSection} re-acquires a section the current generation
 * already holds - {@code ThreadingDetector.checkAndLock} self-deadlock.</p>
 *
 * <p>The lookup is reworked to read the widened {@code getLongX/Z} through the
 * new {@code SectionPos.blockToSectionCoord(long)}; the cache key itself
 * ({@code SectionPos.asLong(BlockPos)}) is already wide via
 * {@link SectionPosPatch}. At normal coordinates every long equals the int it
 * replaced.</p>
 */
public final class BulkSectionAccessPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/chunk/BulkSectionAccess";
    private static final String BLOCKPOS = "net/minecraft/core/BlockPos";
    private static final String SECTIONPOS = "net/minecraft/core/SectionPos";

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
            if (!"lambda$getSection$0".equals(m.name)) {
                continue;
            }
            for (AbstractInsnNode insn : m.instructions) {
                if (!(insn instanceof MethodInsnNode mi)) {
                    continue;
                }
                if (BLOCKPOS.equals(mi.owner) && "getX".equals(mi.name) && "()I".equals(mi.desc)) {
                    mi.name = "getLongX";
                    mi.desc = "()J";
                    changed = true;
                } else if (BLOCKPOS.equals(mi.owner) && "getZ".equals(mi.name) && "()I".equals(mi.desc)) {
                    mi.name = "getLongZ";
                    mi.desc = "()J";
                    changed = true;
                } else if (SECTIONPOS.equals(mi.owner) && "blockToSectionCoord".equals(mi.name)
                    && "(I)I".equals(mi.desc)) {
                    mi.desc = "(J)I";
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
