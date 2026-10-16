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
 * W4-2 slice 3-C: wide hooks for {@code SimulationChunkTracker}.
 *
 * <p>Wide level storage ({@code Wide512ByteMap}, default 33) plus a wide source
 * lookup through {@code TicketStorage.farlands$getTicketLevelAtW}; reads fall
 * back to the vanilla long map (exact in the int domain) when the wide map has
 * no entry, mirroring the TicketStorage design. The constructor tail registers
 * this tracker as the wide simulation listener.</p>
 */
public final class SimulationChunkTrackerWidePatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/server/level/SimulationChunkTracker";
    private static final String CORE = "net/minecraft/world/level/lighting/DynamicGraphMinFixedPoint";
    private static final String W512 = "com/scalarwide/Wide512";
    private static final String W512_D = "Lcom/scalarwide/Wide512;";
    private static final String MAP = "com/scalarwide/mc/Wide512ByteMap";
    private static final String MAP_D = "Lcom/scalarwide/mc/Wide512ByteMap;";
    private static final String WK = "com/scalarwide/mc/WideChunkKey";
    private static final String CHUNK_POS = "net/minecraft/world/level/ChunkPos";
    private static final String TICKET_STORAGE = "net/minecraft/world/level/TicketStorage";
    private static final String TICKET_STORAGE_D = "Lnet/minecraft/world/level/TicketStorage;";

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
        if (node.fields.stream().anyMatch(f -> "farlands$chunksW".equals(f.name))) {
            return original;
        }

        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "farlands$chunksW", MAP_D, null, null));
        node.methods.add(packTrunc());
        node.methods.add(isIntW());
        node.methods.add(mapHelper());
        node.methods.add(getLevelW());
        node.methods.add(setLevelW());
        node.methods.add(getLevelFromSourceW());
        node.methods.add(runAllUpdatesW());
        injectCtor(node);

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

    /** True when the wide key's coordinates fit int (long store authoritative there). */
    private static MethodNode isIntW() {
        MethodNode mn = m(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "farlands$isIntW", "(" + W512_D + ")Z");
        InsnList il = mn.instructions;
        LabelNode no = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, WK, "x", "(" + W512_D + ")J", false));
        il.add(new InsnNode(Opcodes.DUP2));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFNE, no));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, WK, "z", "(" + W512_D + ")J", false));
        il.add(new InsnNode(Opcodes.DUP2));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFNE, no));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(no);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    private static MethodNode mapHelper() {
        MethodNode mn = m(Opcodes.ACC_PRIVATE, "farlands$map", "()" + MAP_D);
        InsnList il = mn.instructions;
        LabelNode have = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "farlands$chunksW", MAP_D));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new JumpInsnNode(Opcodes.IFNONNULL, have));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new TypeInsnNode(Opcodes.NEW, MAP));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.SIPUSH, 256));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, MAP, "<init>", "(I)V", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new InsnNode(Opcodes.SWAP));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "farlands$chunksW", MAP_D));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "farlands$chunksW", MAP_D));
        il.add(have);
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    private static MethodNode getLevelW() {
        MethodNode mn = m(Opcodes.ACC_PUBLIC, "farlands$getLevelW", "(" + W512_D + ")I");
        InsnList il = mn.instructions;
        LabelNode fallback = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$map", "()" + MAP_D, false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MAP, "containsKey", "(" + W512_D + ")Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, fallback));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$map", "()" + MAP_D, false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, 33));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MAP, "getOrDefault", "(" + W512_D + "B)B", false));
        il.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.SIPUSH, 255));
        il.add(new InsnNode(Opcodes.IAND));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(fallback);
        // far keys must not fall back to the long map (truncation aliases int chunks)
        LabelNode fbOk = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "farlands$isIntW", "(" + W512_D + ")Z", false));
        il.add(new JumpInsnNode(Opcodes.IFNE, fbOk));
        il.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, 33));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(fbOk);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "farlands$packTrunc", "(" + W512_D + ")J", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "getLevel", "(J)I", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    private static MethodNode setLevelW() {
        MethodNode mn = m(Opcodes.ACC_PUBLIC, "farlands$setLevelW", "(" + W512_D + "I)V");
        InsnList il = mn.instructions;
        LabelNode put = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, 33));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPLT, put));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$map", "()" + MAP_D, false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new InsnNode(Opcodes.ICONST_M1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MAP, "remove", "(" + W512_D + "B)B", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new InsnNode(Opcodes.RETURN));
        il.add(put);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$map", "()" + MAP_D, false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new InsnNode(Opcodes.I2B));
        il.add(new InsnNode(Opcodes.ICONST_M1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MAP, "put", "(" + W512_D + "BB)B", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    private static MethodNode getLevelFromSourceW() {
        MethodNode mn = m(Opcodes.ACC_PUBLIC, "farlands$getLevelFromSourceW", "(" + W512_D + ")I");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "ticketStorage", TICKET_STORAGE_D));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TICKET_STORAGE, "farlands$getTicketLevelAtW", "(" + W512_D + "Z)I", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    private static MethodNode runAllUpdatesW() {
        MethodNode mn = m(Opcodes.ACC_PUBLIC, "farlands$runAllUpdatesW", "()V");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/Integer", "MAX_VALUE", "I"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CORE, "farlands$runUpdatesW", "(I)I", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    private static void injectCtor(ClassNode node) {
        MethodNode ctor = null;
        for (MethodNode mn : node.methods) {
            if ("<init>".equals(mn.name) && ("(" + TICKET_STORAGE_D + ")V").equals(mn.desc)) {
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
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TICKET_STORAGE, "farlands$setSimulationChunkUpdatedListenerW",
            "(Lnet/minecraft/server/level/ChunkTracker;)V", false));
        ctor.instructions.insertBefore(ret, il);
    }
}
