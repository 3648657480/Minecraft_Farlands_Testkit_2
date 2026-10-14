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
 * W4-2 slice 3-D: wide accessors on {@code DistanceManager}.
 *
 * <p>The concrete implementations of the vanilla long accessors live in
 * {@code ChunkMap}'s inner DistanceManager subclass, so these wide accessors are
 * injected as <b>concrete defaults</b> that truncate the node and delegate to
 * the long accessors - exact in the int domain, a documented residual for far
 * nodes until the inner class overrides them with real wide implementations
 * (slice 3-E). Abstract methods here would break class loading for the
 * unpatched inner subclass.</p>
 */
public final class DistanceManagerWidePatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/server/level/DistanceManager";
    private static final String W512_D = "Lcom/scalarwide/Wide512;";
    private static final String HOLDER_D = "Lnet/minecraft/server/level/ChunkHolder;";
    private static final String WK = "com/scalarwide/mc/WideChunkKey";
    private static final String CHUNK_POS = "net/minecraft/world/level/ChunkPos";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        if (!TARGET.equals(node.name)) {
            return original;
        }
        if (node.methods.stream().anyMatch(mn -> "farlands$getChunkW".equals(mn.name))) {
            return original;
        }

        node.methods.add(packTrunc());
        node.methods.add(isChunkToRemoveW());
        node.methods.add(getChunkW());
        node.methods.add(updateChunkSchedulingW());

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static MethodNode m(int access, String name, String desc) {
        return new MethodNode(access, name, desc, null, null);
    }

    private static MethodNode packTrunc() {
        MethodNode mn = m(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "farlands$packTrunc", "(" + W512_D + ")J");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, WK, "x", "(" + W512_D + ")J", false));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, WK, "z", "(" + W512_D + ")J", false));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CHUNK_POS, "pack", "(II)J", false));
        il.add(new InsnNode(Opcodes.LRETURN));
        return mn;
    }

    private static MethodNode isChunkToRemoveW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED, "farlands$isChunkToRemoveW", "(" + W512_D + ")Z");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "farlands$packTrunc", "(" + W512_D + ")J", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "isChunkToRemove", "(J)Z", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    private static MethodNode getChunkW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED, "farlands$getChunkW", "(" + W512_D + ")" + HOLDER_D);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "farlands$packTrunc", "(" + W512_D + ")J", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "getChunk", "(J)" + HOLDER_D, false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    private static MethodNode updateChunkSchedulingW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED, "farlands$updateChunkSchedulingW",
            "(" + W512_D + "I" + HOLDER_D + "I)" + HOLDER_D);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "farlands$packTrunc", "(" + W512_D + ")J", false));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "updateChunkScheduling",
            "(JI" + HOLDER_D + "I)" + HOLDER_D, false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }
}
