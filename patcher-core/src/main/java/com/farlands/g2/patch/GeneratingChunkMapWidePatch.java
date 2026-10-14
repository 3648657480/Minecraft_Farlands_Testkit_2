package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * W4-2 slice 3-G: wide generation-holder acquisition on the
 * {@code GeneratingChunkMap} interface (only {@code ChunkMap} implements it).
 */
public final class GeneratingChunkMapWidePatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/server/level/GeneratingChunkMap";
    private static final String GEN_HOLDER_D = "Lnet/minecraft/server/level/GenerationChunkHolder;";

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
        if (node.methods.stream().anyMatch(mn -> "farlands$acquireGenerationW".equals(mn.name))) {
            return original;
        }
        node.methods.add(new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
            "farlands$acquireGenerationW", "(Lcom/scalarwide/Wide512;)" + GEN_HOLDER_D, null, null));
        node.methods.add(new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
            "farlands$hasGenerationW", "(Lcom/scalarwide/Wide512;)Z", null, null));
        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }
}
