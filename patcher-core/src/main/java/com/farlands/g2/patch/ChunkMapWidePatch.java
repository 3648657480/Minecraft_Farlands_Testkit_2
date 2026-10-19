package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * W4-2 slice 3-E (part 1): real wide overrides in {@code ChunkMap$DistanceManager}
 * (the nested DistanceManager subclass that implements the long accessors).
 *
 * <ul>
 *   <li>{@code farlands$getChunkW} - exact: reads {@code ChunkMap.updatingChunkMap}
 *       (now a {@code Wide512FlatMap}) through {@code ChunkMap.farlands$cmGetW},
 *       preserving far-domain keys.</li>
 *   <li>{@code farlands$isChunkToRemoveW} - {@code toDrop} is still a 64-bit
 *       {@code LongSet} (recorded residual), so the wide key is truncated here.</li>
 * </ul>
 *
 * <p>{@code farlands$updateChunkSchedulingW} (far-domain holder creation) is the
 * remaining 3-E item: its holder-creation site needs the {@code this::onLevelChange}
 * callback handle, which is not expressible without cloning the outer method's
 * invokedynamic site - deferred to the next round.</p>
 */
public final class ChunkMapWidePatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/server/level/ChunkMap$DistanceManager";
    private static final String CHUNK_MAP = "net/minecraft/server/level/ChunkMap";
    private static final String CHUNK_MAP_D = "Lnet/minecraft/server/level/ChunkMap;";
    private static final String HOLDER = "net/minecraft/server/level/ChunkHolder";
    private static final String HOLDER_D = "Lnet/minecraft/server/level/ChunkHolder;";
    private static final String W512_D = "Lcom/scalarwide/Wide512;";
    private static final String WMAP = "com/scalarwide/mc/Wide512FlatMap";
    private static final String WMAP_D = "Lcom/scalarwide/mc/Wide512FlatMap;";
    private static final String LONG_SET = "it/unimi/dsi/fastutil/longs/LongSet";
    private static final String LONG_SET_D = "Lit/unimi/dsi/fastutil/longs/LongSet;";
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

        node.methods.add(getChunkW());
        node.methods.add(isChunkToRemoveW());
        node.methods.add(updateChunkSchedulingW());

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    /** Delegates to the wide scheduling method injected into the outer {@code ChunkMap}. */
    private static MethodNode updateChunkSchedulingW() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PROTECTED, "farlands$updateChunkSchedulingW",
            "(" + W512_D + "I" + HOLDER_D + "I)" + HOLDER_D, null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "this$0", CHUNK_MAP_D));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNK_MAP, "farlands$updateChunkSchedulingW",
            "(" + W512_D + "I" + HOLDER_D + "I)" + HOLDER_D, false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    private static MethodNode getChunkW() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PROTECTED, "farlands$getChunkW", "(" + W512_D + ")" + HOLDER_D, null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "this$0", CHUNK_MAP_D));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, CHUNK_MAP, "updatingChunkMap", WMAP_D));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CHUNK_MAP, "farlands$cmGetW",
            "(" + WMAP_D + W512_D + ")Ljava/lang/Object;", false));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, HOLDER));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    private static MethodNode isChunkToRemoveW() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PROTECTED, "farlands$isChunkToRemoveW", "(" + W512_D + ")Z", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "this$0", CHUNK_MAP_D));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, CHUNK_MAP, "toDrop", LONG_SET_D));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, WK, "x", "(" + W512_D + ")J", false));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, WK, "z", "(" + W512_D + ")J", false));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CHUNK_POS, "pack", "(II)J", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, LONG_SET, "contains", "(J)Z", true));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }
}
