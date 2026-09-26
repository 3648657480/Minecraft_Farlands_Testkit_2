package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Consumer widening (W5): {@code OreFeature.place} builds its vein endpoints
 * from {@code BlockPos.getX()/getZ()} as int ({@code getX()I; i2d}), so at
 * block 2^31 the int view wraps to {@code Integer.MIN_VALUE} and the vein is
 * placed (and searched) around the wrong block - the ore step then reads far
 * chunks and spins. {@code BlockPos} already stores long; read the wide value
 * ({@code getLongX()J; l2d}) so the endpoint doubles are correct.
 *
 * <p>Y is left as int: the Y domain is world-height-bounded and participates in
 * int arithmetic at the call site. At normal coordinates {@code getLongX()}
 * equals {@code getX()}, so behavior is bit-identical.</p>
 */
public final class OreFeaturePatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/levelgen/feature/OreFeature";
    private static final String BLOCKPOS = "net/minecraft/core/BlockPos";

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
            if (!"place".equals(m.name)) {
                continue;
            }
            AbstractInsnNode insn = m.instructions.getFirst();
            while (insn != null) {
                AbstractInsnNode next = insn.getNext();
                if (insn instanceof MethodInsnNode mi && mi.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && BLOCKPOS.equals(mi.owner) && "()I".equals(mi.desc)
                    && ("getX".equals(mi.name) || "getZ".equals(mi.name))
                    && next instanceof InsnNode in && in.getOpcode() == Opcodes.I2D) {
                    mi.name = "getLong" + mi.name.substring(3);
                    mi.desc = "()J";
                    InsnNode repl = new InsnNode(Opcodes.L2D);
                    m.instructions.set(in, repl);
                    changed = true;
                    insn = repl.getNext();
                } else {
                    insn = next;
                }
            }
        }
        if (!changed) {
            throw new IllegalStateException(TARGET + "#place: no getX/getZ; i2d sites found");
        }

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }
}
