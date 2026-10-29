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

/**
 * Heightmaps are 9-bit storages over the dimension band. A block placed at a
 * far Y (windowed generation, e.g. vines/features at y=2^31) cannot be encoded
 * and {@code SimpleBitStorage.set} throws "not in the specified inclusive
 * range". Skip heightmap updates entirely for far Y; in-band heightmaps keep
 * their exact behaviour.
 */
public final class HeightmapFarYPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/levelgen/Heightmap";
    private static final String HELPER = "com/farlands/g2/util/FarProjection";

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
            if ("update".equals(m.name) && "(IIILnet/minecraft/world/level/block/state/BlockState;)Z".equals(m.desc)
                && !alreadyGuarded(m)) {
                guardBoolean(m);
                changed = true;
            } else if ("setHeight".equals(m.name) && "(III)V".equals(m.desc) && !alreadyGuarded(m)) {
                guardVoid(m);
                changed = true;
            }
        }
        if (!changed) {
            return original;
        }
        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static boolean alreadyGuarded(MethodNode m) {
        for (var insn : m.instructions) {
            if (insn instanceof MethodInsnNode mi && HELPER.equals(mi.owner)
                && "skipHeightmap".equals(mi.name)) {
                return true;
            }
        }
        return false;
    }

    private static void guardBoolean(MethodNode m) {
        LabelNode cont = new LabelNode();
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "skipHeightmap", "(I)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, cont));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(cont);
        m.instructions.insert(il);
    }

    private static void guardVoid(MethodNode m) {
        LabelNode cont = new LabelNode();
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "skipHeightmap", "(I)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, cont));
        il.add(new InsnNode(Opcodes.RETURN));
        il.add(cont);
        m.instructions.insert(il);
    }
}
