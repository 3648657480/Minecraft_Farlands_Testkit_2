package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Adds a per-section wide origin to RenderSection and makes getRenderOrigin
 * return a camera-relative int origin for far sections. This is a patcher
 * change because Mixin redirects on the inner RenderSection class fail their
 * target check in this dev environment.
 */
public final class RenderSectionWideOriginPatch implements ClassPatch {

    private static final String TARGET =
        "net/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection";
    private static final String HOLDER = "com/farlands/g2/runtime/section/WideOriginHolder";
    private static final String HELPER = "com/farlands/g2/client/FarRenderOrigin";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        if (hasField(node, "g2HasWide")) {
            return original;
        }
        node.interfaces.add(HOLDER);
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "g2WideX", "J", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "g2WideY", "J", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "g2WideZ", "J", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "g2HasWide", "Z", null, null));

        node.methods.add(setter());
        node.methods.add(boolGetter("g2HasWideOrigin", "g2HasWide"));
        node.methods.add(longGetter("g2WideX", "g2WideX"));
        node.methods.add(longGetter("g2WideY", "g2WideY"));
        node.methods.add(longGetter("g2WideZ", "g2WideZ"));

        MethodNode origin = find(node, "getRenderOrigin", "()Lnet/minecraft/core/BlockPos;");
        if (origin == null) {
            throw new IllegalStateException(TARGET + ": getRenderOrigin not found");
        }
        origin.instructions.clear();
        origin.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        origin.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        origin.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "renderOrigin",
            "Lnet/minecraft/core/BlockPos$MutableBlockPos;"));
        origin.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "get",
            "(L" + HOLDER + ";Lnet/minecraft/core/BlockPos$MutableBlockPos;)Lnet/minecraft/core/BlockPos;",
            false));
        origin.instructions.add(new InsnNode(Opcodes.ARETURN));
        origin.maxStack = 3;
        origin.maxLocals = 1;

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static MethodNode setter() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "g2SetWideOrigin", "(JJJ)V", null, null);
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new VarInsnNode(Opcodes.LLOAD, 1));
        mn.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "g2WideX", "J"));
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new VarInsnNode(Opcodes.LLOAD, 3));
        mn.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "g2WideY", "J"));
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new VarInsnNode(Opcodes.LLOAD, 5));
        mn.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "g2WideZ", "J"));
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new InsnNode(Opcodes.ICONST_1));
        mn.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "g2HasWide", "Z"));
        mn.instructions.add(new InsnNode(Opcodes.RETURN));
        mn.maxStack = 4;
        mn.maxLocals = 7;
        return mn;
    }

    private static MethodNode boolGetter(String name, String field) {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, name, "()Z", null, null);
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, field, "Z"));
        mn.instructions.add(new InsnNode(Opcodes.IRETURN));
        mn.maxStack = 1;
        mn.maxLocals = 1;
        return mn;
    }

    private static MethodNode longGetter(String name, String field) {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, name, "()J", null, null);
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, field, "J"));
        mn.instructions.add(new InsnNode(Opcodes.LRETURN));
        mn.maxStack = 2;
        mn.maxLocals = 1;
        return mn;
    }

    private static boolean hasField(ClassNode node, String name) {
        for (FieldNode f : node.fields) {
            if (name.equals(f.name)) {
                return true;
            }
        }
        return false;
    }

    private static MethodNode find(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) {
            if (name.equals(m.name) && desc.equals(m.desc)) {
                return m;
            }
        }
        return null;
    }
}
