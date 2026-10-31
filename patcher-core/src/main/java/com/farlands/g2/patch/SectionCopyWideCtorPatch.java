package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Adds SectionCopy(PalettedContainer, LevelHeightAccessor) for wide regions. */
public final class SectionCopyWideCtorPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/client/renderer/chunk/SectionCopy";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        for (MethodNode m : node.methods) {
            if ("<init>".equals(m.name)
                && "(Lnet/minecraft/world/level/chunk/PalettedContainer;Lnet/minecraft/world/level/LevelHeightAccessor;)V"
                    .equals(m.desc)) {
                return original;
            }
        }
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "<init>",
            "(Lnet/minecraft/world/level/chunk/PalettedContainer;Lnet/minecraft/world/level/LevelHeightAccessor;)V",
            null, null);
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
            "java/lang/Object", "<init>", "()V", false));
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
            "com/google/common/collect/ImmutableMap", "of", "()Lcom/google/common/collect/ImmutableMap;",
            false));
        mn.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "blockEntities",
            "Ljava/util/Map;"));
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        mn.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "section",
            "Lnet/minecraft/world/level/chunk/PalettedContainer;"));
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new InsnNode(Opcodes.ICONST_0));
        mn.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "debug", "Z"));
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        mn.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "levelHeightAccessor",
            "Lnet/minecraft/world/level/LevelHeightAccessor;"));
        mn.instructions.add(new InsnNode(Opcodes.RETURN));
        mn.maxStack = 2;
        mn.maxLocals = 3;
        node.methods.add(mn);

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }
}
