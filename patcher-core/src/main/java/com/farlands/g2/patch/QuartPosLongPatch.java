package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Adds long overloads to QuartPos. Vanilla only has int conversions, which is
 * where chunk 2^27 wraps; new long-domain call sites use these instead.
 */
public final class QuartPosLongPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/core/QuartPos";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        if (has(node, "fromBlock", "(J)J")) {
            return original;
        }
        node.methods.add(shift("fromBlock"));
        node.methods.add(shift("toBlock"));
        node.methods.add(shift("fromSection"));
        node.methods.add(shift("toSection"));
        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static MethodNode shift(String name) {
        boolean right = name.startsWith("from");
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            name, "(J)J", null, null);
        m.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.LLOAD, 0));
        m.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_2));
        m.instructions.add(new org.objectweb.asm.tree.InsnNode(right ? Opcodes.LSHR : Opcodes.LSHL));
        m.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.LRETURN));
        m.maxStack = 3;
        m.maxLocals = 2;
        return m;
    }

    private static boolean has(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) {
            if (name.equals(m.name) && desc.equals(m.desc)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String describe(String internalName) {
        return "QuartPosLongPatch (long quart conversions)";
    }
}