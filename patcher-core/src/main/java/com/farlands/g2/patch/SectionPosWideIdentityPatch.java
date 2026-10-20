package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Adds a 2560-bit X/Y/Z identity to {@code SectionPos}. The existing packed
 * {@code asLong} and int views remain for compatibility; new code should use
 * {@link #WIDE_ACCESSOR} or {@link #WIDE_FACTORY}. This is the SectionPos
 * half of the core identity bridge, not the final packed-key removal.
 */
public final class SectionPosWideIdentityPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/core/SectionPos";
    private static final String VEC3I = "net/minecraft/core/Vec3i";
    private static final String WIDE = "com/scalarwide/mc/WideSectionKey2560";
    private static final String XCOORD = "com/scalarwide/mc/WideChunkCoord1024";
    private static final String YCOORD = "com/scalarwide/mc/WideSectionCoord512";
    private static final String WIDE_DESC = "L" + WIDE + ";";
    private static final String X_DESC = "L" + XCOORD + ";";
    private static final String Y_DESC = "L" + YCOORD + ";";
    private static final String WIDE_ACCESSOR = "wideSection";
    private static final String WIDE_FACTORY = "of";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        if (node.fields.stream().anyMatch(f -> WIDE_ACCESSOR.equals(f.name))) {
            return original;
        }

        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
            WIDE_ACCESSOR, WIDE_DESC, null, null));

        for (MethodNode m : node.methods) {
            if ("<init>".equals(m.name) && "(III)V".equals(m.desc)) {
                initFromInts(m);
            }
        }

        addWideConstructor(node);
        addWideFactory(node);
        addWideAccessor(node);
        replaceEquals(node);
        replaceHashCode(node);

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static void initFromInts(MethodNode m) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, WIDE, "of",
            "(JJJ)L" + WIDE + ";", false));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, WIDE_ACCESSOR, WIDE_DESC));
        insertBeforeReturn(m, il);
    }

    private static void addWideConstructor(ClassNode node) {
        if (node.methods.stream().anyMatch(m -> "<init>".equals(m.name)
            && ("(" + WIDE_DESC + ")V").equals(m.desc))) {
            return;
        }
        MethodNode ctor = new MethodNode(Opcodes.ACC_PRIVATE, "<init>",
            "(" + WIDE_DESC + ")V", null, null);
        InsnList il = ctor.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        addAxisFromWide(il, WIDE, "x", XCOORD);
        addAxisFromWide(il, WIDE, "y", YCOORD);
        addAxisFromWide(il, WIDE, "z", XCOORD);
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, VEC3I, "<init>", "(JJJ)V", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, WIDE_ACCESSOR, WIDE_DESC));
        il.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(ctor);
    }

    private static void addAxisFromWide(InsnList il, String owner, String accessor, String coordType) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, accessor,
            "()L" + coordType + ";", false));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, coordType,
            "limb", "(I)J", false));
    }

    private static void addWideFactory(ClassNode node) {
        if (node.methods.stream().anyMatch(m -> WIDE_FACTORY.equals(m.name)
            && ("(" + WIDE_DESC + ")L" + TARGET + ";").equals(m.desc))) {
            return;
        }
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, WIDE_FACTORY,
            "(" + WIDE_DESC + ")L" + TARGET + ";", null, null);
        InsnList il = mn.instructions;
        il.add(new TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>",
            "(" + WIDE_DESC + ")V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        node.methods.add(mn);
    }

    private static void addWideAccessor(ClassNode node) {
        if (node.methods.stream().anyMatch(m -> WIDE_ACCESSOR.equals(m.name)
            && ("()" + WIDE_DESC).equals(m.desc))) {
            return;
        }
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, WIDE_ACCESSOR,
            "()" + WIDE_DESC, null, null);
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, WIDE_ACCESSOR, WIDE_DESC));
        mn.instructions.add(new InsnNode(Opcodes.ARETURN));
        node.methods.add(mn);
    }

    private static void replaceEquals(ClassNode node) {
        MethodNode m = findMethod(node, "equals", "(Ljava/lang/Object;)Z");
        if (m == null) {
            m = new MethodNode(Opcodes.ACC_PUBLIC, "equals", "(Ljava/lang/Object;)Z", null, null);
            node.methods.add(m);
        }
        LabelNode notSame = new LabelNode();
        LabelNode notInstance = new LabelNode();
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new JumpInsnNode(Opcodes.IF_ACMPNE, notSame));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(notSame);
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new TypeInsnNode(Opcodes.INSTANCEOF, TARGET));
        il.add(new JumpInsnNode(Opcodes.IFEQ, notInstance));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, WIDE_ACCESSOR, WIDE_DESC));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, TARGET));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, WIDE_ACCESSOR, WIDE_DESC));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Objects", "equals",
            "(Ljava/lang/Object;Ljava/lang/Object;)Z", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(notInstance);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));
        m.instructions.clear();
        m.localVariables = null;
        m.tryCatchBlocks.clear();
        m.instructions.add(il);
    }

    private static void replaceHashCode(ClassNode node) {
        MethodNode m = findMethod(node, "hashCode", "()I");
        if (m == null) {
            m = new MethodNode(Opcodes.ACC_PUBLIC, "hashCode", "()I", null, null);
            node.methods.add(m);
        }
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, WIDE_ACCESSOR, WIDE_DESC));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Objects", "hashCode",
            "(Ljava/lang/Object;)I", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        m.instructions.clear();
        m.localVariables = null;
        m.tryCatchBlocks.clear();
        m.instructions.add(il);
    }

    private static MethodNode findMethod(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) {
            if (name.equals(m.name) && desc.equals(m.desc)) {
                return m;
            }
        }
        return null;
    }

    private static void insertBeforeReturn(MethodNode m, InsnList addition) {
        AbstractInsnNode lastReturn = null;
        for (AbstractInsnNode n : m.instructions) {
            if (n.getOpcode() == Opcodes.RETURN) {
                lastReturn = n;
            }
        }
        if (lastReturn == null) {
            throw new IllegalStateException("constructor has no RETURN");
        }
        m.instructions.insertBefore(lastReturn, addition);
    }
}
