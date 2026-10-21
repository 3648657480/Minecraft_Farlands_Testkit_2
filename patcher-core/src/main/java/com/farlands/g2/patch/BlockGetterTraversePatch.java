package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Replaces {@code BlockGetter.traverseBlocks} with a long-domain DDA.
 *
 * <p>Vanilla starts the ray at {@code Mth.floor(double)}, which returns int and
 * clamps to {@code Integer.MAX_VALUE}, so nothing past block 2^31 can ever be
 * hit. The replacement keeps the same descriptor and delegates to the mod-side
 * traversal using {@code Mth.lfloor} and long block coordinates.</p>
 */
public final class BlockGetterTraversePatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/BlockGetter";
    private static final String DESC =
        "(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ljava/lang/Object;"
            + "Ljava/util/function/BiFunction;Ljava/util/function/Function;)Ljava/lang/Object;";
    private static final String RUNTIME = "com/farlands/g2/runtime/WideBlockTraversal";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        MethodNode target = null;
        for (MethodNode m : node.methods) {
            if ("traverseBlocks".equals(m.name) && DESC.equals(m.desc)) {
                target = m;
                break;
            }
        }
        if (target == null) {
            throw new IllegalStateException(TARGET + "#traverseBlocks not found");
        }
        InsnList il = new InsnList();
        for (int i = 0; i < 5; i++) {
            il.add(new VarInsnNode(Opcodes.ALOAD, i));
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RUNTIME, "traverse", DESC, false));
        il.add(new InsnNode(Opcodes.ARETURN));
        target.instructions.clear();
        target.instructions.add(il);
        target.localVariables = null;
        target.tryCatchBlocks.clear();

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }
}
