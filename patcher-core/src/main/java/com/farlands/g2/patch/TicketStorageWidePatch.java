package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * W4-2 slice 3-B: wide twin for {@code TicketStorage}.
 *
 * <p>Design: <b>wide reads with long fallback, wide writes to the wide store
 * only</b>. A wide lookup that misses in the wide map falls back to the vanilla
 * long-keyed store via the truncating {@code ChunkPos.pack(int,int)}; in the int
 * domain that bridge is bit-exact (the wide key of ({@code (int)x,(int)z}) is the
 * key the long store was written with), and for far keys the long store is
 * simply empty, so the fallback returns the correct "no tickets" level. Wide
 * writes ({@code farlands$addTicketW/removeTicketW}) touch only the wide map -
 * far tickets have no long representation. No mirror writes are needed, which
 * keeps the vanilla hot path untouched (int domain behaviour unchanged, M1
 * regression gate).</p>
 *
 * <p>Wide listener callbacks are typed as {@code ChunkTracker} and dispatched to
 * the injected {@code ChunkTracker.farlands$updateW(Wide512,int,boolean)}
 * ({@code ChunkTrackerWidePatch}) - no new class/interface is required.</p>
 */
public final class TicketStorageWidePatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/TicketStorage";
    private static final String W512 = "com/scalarwide/mc/WideChunkPos";
    private static final String W512_D = "Lcom/scalarwide/mc/WideChunkPos;";
    private static final String WMAP = "com/scalarwide/mc/WideChunkPosFlatMap";
    private static final String WMAP_D = "Lcom/scalarwide/mc/WideChunkPosFlatMap;";
    private static final String WSET = "com/scalarwide/mc/WideChunkPosOrderedSet";
    private static final String WSET_D = "Lcom/scalarwide/mc/WideChunkPosOrderedSet;";
    private static final String WK = "com/scalarwide/mc/WideChunkPos";
    private static final String CHUNK_POS = "net/minecraft/world/level/ChunkPos";
    private static final String TICKET = "net/minecraft/server/level/Ticket";
    private static final String TICKET_D = "Lnet/minecraft/server/level/Ticket;";
    private static final String TICKET_TYPE = "net/minecraft/server/level/TicketType";
    private static final String TICKET_TYPE_D = "Lnet/minecraft/server/level/TicketType;";
    private static final String TRACKER = "net/minecraft/server/level/ChunkTracker";
    private static final String TRACKER_D = "Lnet/minecraft/server/level/ChunkTracker;";
    private static final String LIST = "java/util/List";
    private static final String LIST_D = "Ljava/util/List;";
    private static final String ARRAY_LIST = "java/util/ArrayList";

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
        if (node.fields.stream().anyMatch(f -> "farlands$ticketsW".equals(f.name))) {
            return original;
        }

        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "farlands$ticketsW", WMAP_D, null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "farlands$forcedW", WSET_D, null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "farlands$loadingListenerW", TRACKER_D, null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "farlands$simulationListenerW", TRACKER_D, null, null));

        node.methods.add(keyW());
        node.methods.add(isIntW());
        node.methods.add(packTrunc());
        node.methods.add(wideMap());
        node.methods.add(forcedSet());
        node.methods.add(getTicketLevelAtW());
        node.methods.add(addTicketW());
        node.methods.add(removeTicketW());
        node.methods.add(addTicketWithRadiusW());
        node.methods.add(setListenerW("farlands$setLoadingChunkUpdatedListenerW", "farlands$loadingListenerW"));
        node.methods.add(setListenerW("farlands$setSimulationChunkUpdatedListenerW", "farlands$simulationListenerW"));

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static MethodNode m(int access, String name, String desc) {
        return new MethodNode(access, name, desc, null, null);
    }

    private static void getf(InsnList il, String owner, String name, String desc) {
        il.add(new FieldInsnNode(Opcodes.GETFIELD, owner, name, desc));
    }

    /** {@code ChunkPos.unpack(packed).widePos()} */
    private static MethodNode keyW() {
        MethodNode mn = m(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "farlands$keyW", "(J)" + W512_D);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.LLOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CHUNK_POS, "unpack", "(J)L" + CHUNK_POS + ";", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNK_POS, "widePos", "()" + W512_D, false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    /** True when the wide key's coordinates fit int (the long store is authoritative there). */
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

    /** {@code ChunkPos.pack((int)x(key), (int)z(key))} */
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

    private static MethodNode wideMap() {
        MethodNode mn = m(Opcodes.ACC_PRIVATE, "farlands$wide", "()" + WMAP_D);
        InsnList il = mn.instructions;
        LabelNode have = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$ticketsW", WMAP_D);
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new JumpInsnNode(Opcodes.IFNONNULL, have));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new TypeInsnNode(Opcodes.NEW, WMAP));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, WMAP, "<init>", "()V", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new InsnNode(Opcodes.SWAP));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "farlands$ticketsW", WMAP_D));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$ticketsW", WMAP_D);
        il.add(have);
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    private static MethodNode forcedSet() {
        MethodNode mn = m(Opcodes.ACC_PRIVATE, "farlands$forcedSet", "()" + WSET_D);
        InsnList il = mn.instructions;
        LabelNode have = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$forcedW", WSET_D);
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new JumpInsnNode(Opcodes.IFNONNULL, have));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new TypeInsnNode(Opcodes.NEW, WSET));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, WSET, "<init>", "()V", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new InsnNode(Opcodes.SWAP));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "farlands$forcedW", WSET_D));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$forcedW", WSET_D);
        il.add(have);
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    /** {@code farlands$getTicketLevelAtW(Wide512 key, boolean simulation)} */
    private static MethodNode getTicketLevelAtW() {
        MethodNode mn = m(Opcodes.ACC_PUBLIC, "farlands$getTicketLevelAtW", "(" + W512_D + "Z)I");
        InsnList il = mn.instructions;
        LabelNode have = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$wide", "()" + WMAP_D, false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WMAP, "get", "(" + W512_D + ")Ljava/lang/Object;", false));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, LIST));
        il.add(new VarInsnNode(Opcodes.ASTORE, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new JumpInsnNode(Opcodes.IFNONNULL, have));
        // far keys must NOT fall back to the long store (their truncation aliases
        // unrelated int-domain tickets, e.g. the spawn PLAYER_SPAWN ticket at (0,0))
        LabelNode fbOk = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "farlands$isIntW", "(" + W512_D + ")Z", false));
        il.add(new JumpInsnNode(Opcodes.IFNE, fbOk));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/server/level/ChunkLevel", "MAX_LEVEL", "I"));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(fbOk);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "farlands$packTrunc", "(" + W512_D + ")J", false));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "getTicketLevelAt", "(JZ)I", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(have);
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "getTicketLevelAt", "(" + LIST_D + "Z)I", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    /** {@code farlands$addTicketW(ChunkPos, Ticket)} - wide-store add mirror. */
    private static MethodNode addTicketW() {
        MethodNode mn = m(Opcodes.ACC_PUBLIC, "farlands$addTicketW", "(L" + CHUNK_POS + ";" + TICKET_D + ")Z");
        InsnList il = mn.instructions;
        // key
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNK_POS, "widePos", "()" + W512_D, false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 3));
        // list = (List) wide().get(key)
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$wide", "()" + WMAP_D, false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WMAP, "get", "(" + W512_D + ")Ljava/lang/Object;", false));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, LIST));
        il.add(new VarInsnNode(Opcodes.ASTORE, 4));
        // if (list == null) { list = new ArrayList(4); wide().put(key, list); }
        LabelNode have = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new JumpInsnNode(Opcodes.IFNONNULL, have));
        il.add(new TypeInsnNode(Opcodes.NEW, ARRAY_LIST));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, ARRAY_LIST, "<init>", "(I)V", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$wide", "()" + WMAP_D, false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WMAP, "put",
            "(" + W512_D + "Ljava/lang/Object;)Ljava/lang/Object;", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(have);
        // duplicate scan
        LabelNode endLoop = new LabelNode();
        LabelNode next = new LabelNode();
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 5));
        LabelNode loop = new LabelNode();
        il.add(loop);
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, LIST, "size", "()I", true));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGE, endLoop));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, LIST, "get", "(I)Ljava/lang/Object;", true));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, TICKET));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "isTicketSameTypeAndLevel", "(" + TICKET_D + TICKET_D + ")Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, next));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, LIST, "get", "(I)Ljava/lang/Object;", true));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, TICKET));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TICKET, "resetTicksLeft", "()V", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "setDirty", "()V", false));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(next);
        il.add(new org.objectweb.asm.tree.IincInsnNode(5, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, loop));
        il.add(endLoop);
        // old levels
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "getTicketLevelAt", "(" + LIST_D + "Z)I", false));
        il.add(new VarInsnNode(Opcodes.ISTORE, 7));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "getTicketLevelAt", "(" + LIST_D + "Z)I", false));
        il.add(new VarInsnNode(Opcodes.ISTORE, 8));
        // list.add(ticket)
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, LIST, "add", "(Ljava/lang/Object;)Z", true));
        il.add(new InsnNode(Opcodes.POP));
        // simulation listener
        notifyW(il, "farlands$simulationListenerW", "farlands$simulationListenerW", "doesSimulate", 7, 3);
        // loading listener
        notifyW(il, "farlands$loadingListenerW", "farlands$loadingListenerW", "doesLoad", 8, 3);
        // forced
        LabelNode noForced = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TICKET, "getType", "()" + TICKET_TYPE_D, false));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, TICKET_TYPE, "FORCED", TICKET_TYPE_D));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TICKET_TYPE, "equals", "(Ljava/lang/Object;)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, noForced));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$forcedSet", "()" + WSET_D, false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WSET, "add", "(" + W512_D + ")Z", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(noForced);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "setDirty", "()V", false));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    /** Shared listener notification: {@code if (ticket.type.doesX() && ticket.level < oldLevel && listener != null) listener.updateW(key, level, true)}. */
    private static void notifyW(InsnList il, String field, String fieldOwnerName, String gate, int oldLevelSlot, int keySlot) {
        LabelNode skip = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TICKET, "getType", "()" + TICKET_TYPE_D, false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TICKET_TYPE, gate, "()Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, skip));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TICKET, "getTicketLevel", "()I", false));
        il.add(new VarInsnNode(Opcodes.ILOAD, oldLevelSlot));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGE, skip));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, field, TRACKER_D);
        il.add(new JumpInsnNode(Opcodes.IFNULL, skip));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, field, TRACKER_D);
        il.add(new VarInsnNode(Opcodes.ALOAD, keySlot));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TICKET, "getTicketLevel", "()I", false));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TRACKER, "farlands$updateW", "(" + W512_D + "IZ)V", false));
        il.add(skip);
    }

    /** {@code farlands$removeTicketW(ChunkPos, Ticket)}. */
    private static MethodNode removeTicketW() {
        MethodNode mn = m(Opcodes.ACC_PUBLIC, "farlands$removeTicketW", "(L" + CHUNK_POS + ";" + TICKET_D + ")Z");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNK_POS, "widePos", "()" + W512_D, false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$wide", "()" + WMAP_D, false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WMAP, "get", "(" + W512_D + ")Ljava/lang/Object;", false));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, LIST));
        il.add(new VarInsnNode(Opcodes.ASTORE, 4));
        LabelNode missing = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new JumpInsnNode(Opcodes.IFNULL, missing));
        // find index
        il.add(new InsnNode(Opcodes.ICONST_M1));
        il.add(new VarInsnNode(Opcodes.ISTORE, 5));
        LabelNode endLoop = new LabelNode();
        LabelNode next = new LabelNode();
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 6));
        LabelNode loop = new LabelNode();
        il.add(loop);
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, LIST, "size", "()I", true));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGE, endLoop));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, LIST, "get", "(I)Ljava/lang/Object;", true));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, TICKET));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "isTicketSameTypeAndLevel", "(" + TICKET_D + TICKET_D + ")Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, next));
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        il.add(new VarInsnNode(Opcodes.ISTORE, 5));
        il.add(new JumpInsnNode(Opcodes.GOTO, endLoop));
        il.add(next);
        il.add(new org.objectweb.asm.tree.IincInsnNode(6, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, loop));
        il.add(endLoop);
        LabelNode notFound = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new JumpInsnNode(Opcodes.IFLT, notFound));
        // remove
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, LIST, "remove", "(I)Ljava/lang/Object;", true));
        il.add(new InsnNode(Opcodes.POP));
        // if empty -> remove key
        LabelNode notEmpty = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, LIST, "isEmpty", "()Z", true));
        il.add(new JumpInsnNode(Opcodes.IFEQ, notEmpty));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$wide", "()" + WMAP_D, false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WMAP, "remove", "(" + W512_D + ")Ljava/lang/Object;", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(notEmpty);
        // listeners (false)
        LabelNode skipSim = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TICKET, "getType", "()" + TICKET_TYPE_D, false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TICKET_TYPE, "doesSimulate", "()Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, skipSim));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$simulationListenerW", TRACKER_D);
        il.add(new JumpInsnNode(Opcodes.IFNULL, skipSim));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$simulationListenerW", TRACKER_D);
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "getTicketLevelAt", "(" + LIST_D + "Z)I", false));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TRACKER, "farlands$updateW", "(" + W512_D + "IZ)V", false));
        il.add(skipSim);
        LabelNode skipLoad = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TICKET, "getType", "()" + TICKET_TYPE_D, false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TICKET_TYPE, "doesLoad", "()Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, skipLoad));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$loadingListenerW", TRACKER_D);
        il.add(new JumpInsnNode(Opcodes.IFNULL, skipLoad));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$loadingListenerW", TRACKER_D);
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "getTicketLevelAt", "(" + LIST_D + "Z)I", false));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TRACKER, "farlands$updateW", "(" + W512_D + "IZ)V", false));
        il.add(skipLoad);
        // forced
        LabelNode noForced = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TICKET, "getType", "()" + TICKET_TYPE_D, false));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, TICKET_TYPE, "FORCED", TICKET_TYPE_D));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TICKET_TYPE, "equals", "(Ljava/lang/Object;)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, noForced));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$forcedSet", "()" + WSET_D, false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WSET, "remove", "(" + W512_D + ")Z", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(noForced);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "setDirty", "()V", false));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        // notFound / missing -> false
        il.add(notFound);
        il.add(missing);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    /** {@code farlands$addTicketWithRadiusW(TicketType, ChunkPos, int)} - wide radius ticket. */
    private static MethodNode addTicketWithRadiusW() {
        MethodNode mn = m(Opcodes.ACC_PUBLIC, "farlands$addTicketWithRadiusW",
            "(L" + TICKET_TYPE + ";L" + CHUNK_POS + ";I)V");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, TICKET));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/server/level/FullChunkStatus", "FULL",
            "Lnet/minecraft/server/level/FullChunkStatus;"));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/server/level/ChunkLevel", "byStatus",
            "(Lnet/minecraft/server/level/FullChunkStatus;)I", false));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new InsnNode(Opcodes.ISUB));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TICKET, "<init>", "(" + TICKET_TYPE_D + "I)V", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$addTicketW",
            "(L" + CHUNK_POS + ";" + TICKET_D + ")Z", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    private static MethodNode setListenerW(String name, String field) {
        MethodNode mn = m(Opcodes.ACC_PUBLIC, name, "(" + TRACKER_D + ")V");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, field, TRACKER_D));
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }
}
