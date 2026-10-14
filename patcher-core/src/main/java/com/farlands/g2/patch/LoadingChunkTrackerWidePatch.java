package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * W4-2 slice 3-C: wide hooks for {@code LoadingChunkTracker} - wide level
 * read/write routed through {@code DistanceManager}'s wide abstract accessors
 * (implemented by {@code ChunkMapWidePatch}), wide source lookup through
 * {@code TicketStorage.farlands$getTicketLevelAtW}, and wide run entry.</p>
 */
public final class LoadingChunkTrackerWidePatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/server/level/LoadingChunkTracker";
    private static final String CORE = "net/minecraft/world/level/lighting/DynamicGraphMinFixedPoint";
    private static final String DM = "net/minecraft/server/level/DistanceManager";
    private static final String DM_D = "Lnet/minecraft/server/level/DistanceManager;";
    private static final String HOLDER = "net/minecraft/server/level/ChunkHolder";
    private static final String HOLDER_D = "Lnet/minecraft/server/level/ChunkHolder;";
    private static final String W512_D = "Lcom/scalarwide/Wide512;";
    private static final String TICKET_STORAGE = "net/minecraft/world/level/TicketStorage";
    private static final String TICKET_STORAGE_D = "Lnet/minecraft/world/level/TicketStorage;";
    private static final String SET_D = "Ljava/util/Set;";

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
        if (node.methods.stream().anyMatch(mn -> "farlands$getLevelW".equals(mn.name))) {
            return original;
        }

        node.methods.add(getLevelW());
        node.methods.add(setLevelW());
        node.methods.add(getLevelFromSourceW());
        node.methods.add(runDistanceUpdatesW());
        injectCtor(node);

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static MethodNode m(int access, String name, String desc) {
        return new MethodNode(access, name, desc, null, null);
    }

    private static MethodNode getLevelW() {
        MethodNode mn = m(Opcodes.ACC_PUBLIC, "farlands$getLevelW", "(" + W512_D + ")I");
        InsnList il = mn.instructions;
        LabelNode fallback = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "distanceManager", DM_D));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, DM, "farlands$isChunkToRemoveW", "(" + W512_D + ")Z", false));
        il.add(new JumpInsnNode(Opcodes.IFNE, fallback));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "distanceManager", DM_D));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, DM, "farlands$getChunkW", "(" + W512_D + ")" + HOLDER_D, false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new JumpInsnNode(Opcodes.IFNULL, fallback));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, HOLDER, "getTicketLevel", "()I", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(fallback);
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, TARGET, "MAX_LEVEL", "I"));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    private static MethodNode setLevelW() {
        MethodNode mn = m(Opcodes.ACC_PUBLIC, "farlands$setLevelW", "(" + W512_D + "I)V");
        InsnList il = mn.instructions;
        LabelNode haveHolder = new LabelNode();
        LabelNode end = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "distanceManager", DM_D));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, DM, "farlands$getChunkW", "(" + W512_D + ")" + HOLDER_D, false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 3));
        LabelNode haveChunk = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new JumpInsnNode(Opcodes.IFNONNULL, haveChunk));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, TARGET, "MAX_LEVEL", "I"));
        il.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.GOTO, haveHolder));
        il.add(haveChunk);
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, HOLDER, "getTicketLevel", "()I", false));
        il.add(haveHolder);
        il.add(new VarInsnNode(Opcodes.ISTORE, 4));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, end));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "distanceManager", DM_D));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, DM, "farlands$updateChunkSchedulingW",
            "(" + W512_D + "I" + HOLDER_D + "I)" + HOLDER_D, false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 3));
        LabelNode skipAdd = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new JumpInsnNode(Opcodes.IFNULL, skipAdd));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "distanceManager", DM_D));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, DM, "chunksToUpdateFutures", SET_D));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/Set", "add", "(Ljava/lang/Object;)Z", true));
        il.add(new InsnNode(Opcodes.POP));
        il.add(skipAdd);
        il.add(end);
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    private static MethodNode getLevelFromSourceW() {
        MethodNode mn = m(Opcodes.ACC_PUBLIC, "farlands$getLevelFromSourceW", "(" + W512_D + ")I");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "ticketStorage", TICKET_STORAGE_D));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TICKET_STORAGE, "farlands$getTicketLevelAtW", "(" + W512_D + "Z)I", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    private static MethodNode runDistanceUpdatesW() {
        MethodNode mn = m(Opcodes.ACC_PUBLIC, "farlands$runDistanceUpdatesW", "(I)I");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CORE, "farlands$runUpdatesW", "(I)I", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    private static void injectCtor(ClassNode node) {
        MethodNode ctor = null;
        for (MethodNode mn : node.methods) {
            if ("<init>".equals(mn.name) && ("(" + DM_D + TICKET_STORAGE_D + ")V").equals(mn.desc)) {
                ctor = mn;
                break;
            }
        }
        if (ctor == null) {
            throw new IllegalStateException(TARGET + " constructor not found");
        }
        AbstractInsnNode ret = ctor.instructions.getLast();
        while (ret != null && !(ret instanceof InsnNode && ret.getOpcode() == Opcodes.RETURN)) {
            ret = ret.getPrevious();
        }
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "ticketStorage", TICKET_STORAGE_D));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TICKET_STORAGE, "farlands$setLoadingChunkUpdatedListenerW",
            "(Lnet/minecraft/server/level/ChunkTracker;)V", false));
        ctor.instructions.insertBefore(ret, il);
    }
}
