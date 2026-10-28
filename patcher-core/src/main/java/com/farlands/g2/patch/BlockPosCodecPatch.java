package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

/**
 * Replaces {@code BlockPos.CODEC} (persistence) with the wide long-stream codec.
 * The vanilla codec stores three ints, truncating every saved position beyond
 * 2^31 (entity home/sleep/leash points, POIs, raid omen, saved ticks).
 */
public final class BlockPosCodecPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/core/BlockPos";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        MethodNode clinit = null;
        for (MethodNode m : node.methods) {
            if ("<clinit>".equals(m.name)) {
                clinit = m;
            }
        }
        if (clinit == null) {
            throw new IllegalStateException(TARGET + " <clinit> not found");
        }
        for (AbstractInsnNode insn : clinit.instructions) {
            if (insn instanceof FieldInsnNode fi && fi.getOpcode() == Opcodes.GETSTATIC
                && "com/farlands/g2/runtime/WideBlockPosCodec".equals(fi.owner)) {
                return original; // already patched
            }
        }
        FieldInsnNode putCodec = null;
        for (AbstractInsnNode insn : clinit.instructions) {
            if (insn instanceof FieldInsnNode fi && fi.getOpcode() == Opcodes.PUTSTATIC
                && TARGET.equals(fi.owner) && "CODEC".equals(fi.name)) {
                putCodec = fi;
            }
        }
        if (putCodec == null) {
            throw new IllegalStateException(TARGET + "#CODEC putstatic not found");
        }
        InsnList il = new InsnList();
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/farlands/g2/runtime/WideBlockPosCodec",
            "CODEC", "Lcom/mojang/serialization/Codec;"));
        il.add(new FieldInsnNode(Opcodes.PUTSTATIC, TARGET, "CODEC",
            "Lcom/mojang/serialization/Codec;"));
        clinit.instructions.insert(putCodec, il);
        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }
}