package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Iterates a bounded cache by local offsets, even when its int coordinate view wraps. */
public final class StaticCache2DIterationPatch implements ClassPatch {
    private static final String TARGET = "net/minecraft/util/StaticCache2D";
    private static final String INITIALIZER = TARGET + "$Initializer";

    @Override public boolean matches(String name) { return TARGET.equals(name); }

    @Override public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        MethodNode ctor = node.methods.stream().filter(m -> m.name.equals("<init>")
            && m.desc.equals("(IIIIL" + INITIALIZER + ";)V")).findFirst()
            .orElseThrow(() -> new IllegalStateException("StaticCache2D constructor missing"));
        ctor.instructions.clear();
        ctor.tryCatchBlocks.clear();
        ctor.localVariables = null;
        InsnList il = ctor.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        String[] fields = {"minX", "minZ", "sizeX", "sizeZ"};
        for (int i = 0; i < fields.length; i++) {
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new VarInsnNode(Opcodes.ILOAD, i + 1));
            il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, fields[i], "I"));
        }
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new InsnNode(Opcodes.IMUL));
        il.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "cache", "[Ljava/lang/Object;"));
        LabelNode xLoop = new LabelNode(), zLoop = new LabelNode(), nextX = new LabelNode(), done = new LabelNode();
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 6));
        il.add(xLoop);
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGE, done));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 7));
        il.add(zLoop);
        il.add(new VarInsnNode(Opcodes.ILOAD, 7));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGE, nextX));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "cache", "[Ljava/lang/Object;"));
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new InsnNode(Opcodes.IMUL));
        il.add(new VarInsnNode(Opcodes.ILOAD, 7));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 7));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, INITIALIZER, "get", "(II)Ljava/lang/Object;", true));
        il.add(new InsnNode(Opcodes.AASTORE));
        il.add(new IincInsnNode(7, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, zLoop));
        il.add(nextX);
        il.add(new IincInsnNode(6, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, xLoop));
        il.add(done);
        il.add(new InsnNode(Opcodes.RETURN));
        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }
}
