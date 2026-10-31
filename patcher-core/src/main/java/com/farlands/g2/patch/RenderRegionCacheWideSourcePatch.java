package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Routes far render regions to the wide client store before the vanilla 3x3 build. */
public final class RenderRegionCacheWideSourcePatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/client/renderer/chunk/RenderRegionCache";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        MethodNode mn = null;
        for (MethodNode m : node.methods) {
            if ("createRegion".equals(m.name)
                && "(Lnet/minecraft/client/multiplayer/ClientLevel;J)Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;"
                    .equals(m.desc)) {
                mn = m;
                break;
            }
        }
        if (mn == null) {
            throw new IllegalStateException(TARGET + ": createRegion not found");
        }
        for (var insn : mn.instructions) {
            if (insn instanceof MethodInsnNode call
                && "com/farlands/g2/client/FarRegionBuilder".equals(call.owner)) {
                return original;
            }
        }
        LabelNode fallthrough = new LabelNode();
        InsnList prefix = new InsnList();
        prefix.add(new VarInsnNode(Opcodes.ALOAD, 1));
        prefix.add(new VarInsnNode(Opcodes.LLOAD, 2));
        prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
            "com/farlands/g2/client/FarRegionBuilder", "create",
            "(Lnet/minecraft/client/multiplayer/ClientLevel;J)Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;",
            false));
        prefix.add(new InsnNode(Opcodes.DUP));
        prefix.add(new JumpInsnNode(Opcodes.IFNULL, fallthrough));
        prefix.add(new InsnNode(Opcodes.ARETURN));
        prefix.add(fallthrough);
        prefix.add(new InsnNode(Opcodes.POP));
        mn.instructions.insert(prefix);
        mn.maxStack = Math.max(mn.maxStack, 3);

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }
}
