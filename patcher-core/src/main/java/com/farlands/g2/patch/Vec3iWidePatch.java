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
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * B-line (farlands.wide): widens Vec3i x/y/z storage from int to long,
 * adds getLongX/Y/Z, and moves position-deriving arithmetic to the long
 * domain. Int accessors keep wrap semantics (invisible at normal coords).
 *
 * <p>{@link Vec3iInline128Patch} runs immediately after this patch and splits
 * each widened field into inline lo/hi limbs; the long accessors above remain
 * the exact 64-bit (low-limb) view they define here.</p>
 */
public final class Vec3iWidePatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/core/Vec3i";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        boolean widened = false;
        for (FieldNode f : node.fields) {
            if ((f.name.equals("x") || f.name.equals("y") || f.name.equals("z")) && "I".equals(f.desc)) {
                f.desc = "J";
                widened = true;
            }
        }
        if (!widened) {
            return original;
        }

        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof FieldInsnNode fi && TARGET.equals(fi.owner)
                    && (fi.name.equals("x") || fi.name.equals("y") || fi.name.equals("z"))) {
                    if (fi.getOpcode() == Opcodes.GETFIELD && "I".equals(fi.desc)) {
                        fi.desc = "J";
                        m.instructions.insert(insn, new MethodInsnNode(Opcodes.INVOKESTATIC,
                            "com/farlands/g2/util/FarProjection", "narrowView", "(J)I", false));
                    } else if (fi.getOpcode() == Opcodes.PUTFIELD && "I".equals(fi.desc)) {
                        fi.desc = "J";
                        m.instructions.insertBefore(insn, new InsnNode(Opcodes.I2L));
                    }
                }
            }
        }

        addLongAccessors(node);
        addLongSetters(node);
        widenArithmetic(node);

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static void addLongAccessors(ClassNode node) {
        if (node.methods.stream().anyMatch(m -> "getLongX".equals(m.name))) {
            return;
        }
        for (String axis : new String[]{"X", "Y", "Z"}) {
            MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "getLong" + axis, "()J", null, null);
            mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            mn.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, axis.toLowerCase(), "J"));
            mn.instructions.add(new InsnNode(Opcodes.LRETURN));
            node.methods.add(mn);
        }
    }

    /**
     * Long-domain setters for the widened private fields. These must be added
     * after the field-widening pass so their {@code PUTFIELD} already carries
     * the {@code J} descriptor and is not rewritten again. Consumers that must
     * store a coordinate that does not fit an int (e.g. the ore vein loop at
     * block 2^31) use these to write the wide value directly.
     */
    private static void addLongSetters(ClassNode node) {
        if (node.methods.stream().anyMatch(m -> "setLongX".equals(m.name))) {
            return;
        }
        for (String axis : new String[]{"X", "Y", "Z"}) {
            MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "setLong" + axis, "(J)Lnet/minecraft/core/Vec3i;", null, null);
            mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            mn.instructions.add(new VarInsnNode(Opcodes.LLOAD, 1));
            mn.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, axis.toLowerCase(), "J"));
            mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            mn.instructions.add(new InsnNode(Opcodes.ARETURN));
            node.methods.add(mn);
        }
    }

    private static void widenArithmetic(ClassNode node) {
        if (find(node, "<init>", "(JJJ)V") != null) {
            return;
        }
        node.methods.add(longConstructor());
        rewrite(node, "offset", "(III)Lnet/minecraft/core/Vec3i;", newInstance("offset", new int[]{0, 1, 2}));
        // offset(Vec3i) is the common path used by feature code (e.g. disk/column
        // helpers): route it through the wide fields instead of the int getters.
        rewrite(node, "offset", "(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/core/Vec3i;", offsetVecBody());
        rewrite(node, "multiply", "(I)Lnet/minecraft/core/Vec3i;", newInstance("multiply", new int[]{0, 1, 2}));
        rewrite(node, "subtract", "(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/core/Vec3i;", subtractBody());
        rewrite(node, "relative", "(Lnet/minecraft/core/Direction;I)Lnet/minecraft/core/Vec3i;", relativeBody());
        rewrite(node, "cross", "(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/core/Vec3i;", crossBody());
    }

    /** {@code new Vec3i(getLongX()+v.getLongX(), getLongY()+v.getLongY(), getLongZ()+v.getLongZ())} */
    private static InsnList offsetVecBody() {
        InsnList il = new InsnList();
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        for (int i = 0; i < 3; i++) {
            String axis = "XYZ".substring(i, i + 1);
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "getLong" + axis, "()J", false));
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "getLong" + axis, "()J", false));
            il.add(new InsnNode(Opcodes.LADD));
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static MethodNode longConstructor() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(JJJ)V", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        for (int i = 0; i < 3; i++) {
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new VarInsnNode(Opcodes.LLOAD, 1 + 2 * i));
            il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "xyz".substring(i, i + 1), "J"));
        }
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    private static InsnList newInstance(String op, int[] axes) {
        InsnList il = new InsnList();
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        for (int i = 0; i < 3; i++) {
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET,
                "getLong" + "XYZ".substring(axes[i], axes[i] + 1), "()J", false));
            if ("multiply".equals(op)) {
                il.add(new VarInsnNode(Opcodes.ILOAD, 1));
                il.add(new InsnNode(Opcodes.I2L));
                il.add(new InsnNode(Opcodes.LMUL));
            } else {
                il.add(new VarInsnNode(Opcodes.ILOAD, i + 1));
                il.add(new InsnNode(Opcodes.I2L));
                il.add(new InsnNode(Opcodes.LADD));
            }
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static InsnList subtractBody() {
        InsnList il = new InsnList();
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        for (int i = 0; i < 3; i++) {
            String axis = "XYZ".substring(i, i + 1);
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "getLong" + axis, "()J", false));
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "getLong" + axis, "()J", false));
            il.add(new InsnNode(Opcodes.LSUB));
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static InsnList relativeBody() {
        InsnList il = new InsnList();
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        for (int i = 0; i < 3; i++) {
            String axis = "XYZ".substring(i, i + 1);
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "getLong" + axis, "()J", false));
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Direction",
                "getStep" + axis, "()I", false));
            il.add(new InsnNode(Opcodes.I2L));
            il.add(new VarInsnNode(Opcodes.ILOAD, 2));
            il.add(new InsnNode(Opcodes.I2L));
            il.add(new InsnNode(Opcodes.LMUL));
            il.add(new InsnNode(Opcodes.LADD));
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static InsnList crossBody() {
        String[] selfA = {"Y", "Z", "X"};
        String[] selfB = {"Z", "X", "Y"};
        String[] otherA = {"Z", "X", "Y"};
        String[] otherB = {"Y", "Z", "X"};
        InsnList il = new InsnList();
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        for (int i = 0; i < 3; i++) {
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "getLong" + selfA[i], "()J", false));
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "getLong" + otherA[i], "()J", false));
            il.add(new InsnNode(Opcodes.LMUL));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "getLong" + selfB[i], "()J", false));
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "getLong" + otherB[i], "()J", false));
            il.add(new InsnNode(Opcodes.LMUL));
            il.add(new InsnNode(Opcodes.LSUB));
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static MethodNode find(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) {
            if (name.equals(m.name) && desc.equals(m.desc)) {
                return m;
            }
        }
        return null;
    }

    private static void rewrite(ClassNode node, String name, String desc, InsnList body) {
        MethodNode m = find(node, name, desc);
        if (m == null) {
            throw new IllegalStateException(TARGET + "#" + name + desc + " not found");
        }
        m.instructions.clear();
        m.localVariables = null;
        m.tryCatchBlocks.clear();
        m.instructions.add(body);
    }
}