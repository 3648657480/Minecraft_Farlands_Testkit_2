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
 * Adds an authoritative 1024-bit X/Z identity to {@code ChunkPos}.
 *
 * <p>The vanilla/record fields remain the local int view used by existing
 * call sites. New code must use {@link #WIDE_ACCESSOR} and the
 * {@code (WideChunkPos)} constructor; this patch does not yet remove the
 * legacy long bridge. It is the first step of the full migration ledger.</p>
 */
public final class ChunkPosWideIdentityPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/ChunkPos";
    private static final String WIDE = "com/scalarwide/mc/WideChunkPos";
    private static final String COORD = "com/scalarwide/mc/WideChunkCoord1024";
    private static final String WIDE_DESC = "L" + WIDE + ";";
    private static final String COORD_DESC = "L" + COORD + ";";
    private static final String WIDE_ACCESSOR = "widePos";

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
            if ("<init>".equals(m.name) && "(II)V".equals(m.desc)) {
                initFromInts(m);
            } else if ("<init>".equals(m.name) && "(JJ)V".equals(m.desc)) {
                initFromLongs(m);
            }
        }

        addWideConstructor(node);
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
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, WIDE, "of",
            "(JJ)L" + WIDE + ";", false));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, WIDE_ACCESSOR, WIDE_DESC));
        insertBeforeReturn(m, il);
    }

    private static void initFromLongs(MethodNode m) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "x", "J"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "z", "J"));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, WIDE, "of",
            "(JJ)L" + WIDE + ";", false));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, WIDE_ACCESSOR, WIDE_DESC));
        insertBeforeReturn(m, il);
    }

    private static void addWideConstructor(ClassNode node) {
        if (node.methods.stream().anyMatch(m -> "<init>".equals(m.name)
            && ("(" + WIDE_DESC + ")V").equals(m.desc))) {
            return;
        }
        MethodNode ctor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>",
            "(" + WIDE_DESC + ")V", null, null);
        InsnList il = ctor.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Record", "<init>", "()V", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, WIDE_ACCESSOR, WIDE_DESC));
        addAxisFromWide(il, "x", "x");
        addAxisFromWide(il, "z", "z");
        il.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(ctor);
    }

    private static void addAxisFromWide(InsnList il, String field, String accessor) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WIDE, accessor,
            "()" + COORD_DESC, false));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, COORD, "limb", "(I)J", false));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, field, "J"));
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
            throw new IllegalStateException(TARGET + "#equals not found");
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
