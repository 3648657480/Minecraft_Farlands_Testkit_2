package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * W4-2 slice 3-F (step 2): wide ticket entry on {@code ServerChunkCache} -
 * exposes {@code TicketStorage.farlands$addTicketWithRadiusW} to the testgen
 * harness (the {@code ticketStorage} field is private, the harness lives outside
 * the MC packages).
 */
public final class ServerChunkCacheWidePatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/server/level/ServerChunkCache";
    private static final String CHUNK_POS = "net/minecraft/world/level/ChunkPos";
    private static final String TICKET_TYPE = "net/minecraft/server/level/TicketType";
    private static final String TICKET_TYPE_D = "Lnet/minecraft/server/level/TicketType;";
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
        if (node.methods.stream().anyMatch(mn -> "farlands$addTicketWithRadiusW".equals(mn.name))) {
            return original;
        }

        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "farlands$addTicketWithRadiusW",
            "(" + TICKET_TYPE_D + "L" + CHUNK_POS + ";I)V", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "ticketStorage", TICKET_STORAGE_D));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TICKET_STORAGE, "farlands$addTicketWithRadiusW",
            "(" + TICKET_TYPE_D + "L" + CHUNK_POS + ";I)V", false));
        il.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(mn);
        node.methods.add(getChunkWide(node));

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    /**
     * {@code public LevelChunk farlands$getChunkWide(ChunkPos pos)}: wide holder
     * lookup + {@code managedBlock} wait on the full-chunk future. The
     * {@code isDone} BooleanSupplier lambda is reused by copying the existing
     * {@code managedBlock(serverFuture::isDone)} call site's invokedynamic, so
     * no new lambda needs to be synthesised. managedBlock drains the main-thread
     * task queue while waiting, so worker completions are not starved.
     */
    private static MethodNode getChunkWide(ClassNode node) {
        InvokeDynamicInsnNode isDone = null;
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof InvokeDynamicInsnNode idy
                    && idy.desc.endsWith("Ljava/util/function/BooleanSupplier;")) {
                    isDone = idy;
                    break;
                }
            }
            if (isDone != null) {
                break;
            }
        }
        if (isDone == null) {
            throw new IllegalStateException(TARGET + ": isDone/BooleanSupplier indy not found");
        }

        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "farlands$getChunkWide",
            "(Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/world/level/chunk/LevelChunk;", null, null);
        InsnList il = mn.instructions;
        LabelNode hasHolder = new LabelNode();
        LabelNode hasResult = new LabelNode();
        // holder = chunkMap.farlands$getUpdatingChunkW(pos.widePos())
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "chunkMap", "Lnet/minecraft/server/level/ChunkMap;"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNK_POS, "widePos", "()Lcom/scalarwide/mc/WideChunkPos;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/server/level/ChunkMap",
            "farlands$getUpdatingChunkW", "(Lcom/scalarwide/mc/WideChunkPos;)Lnet/minecraft/server/level/ChunkHolder;", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new JumpInsnNode(Opcodes.IFNONNULL, hasHolder));
        il.add(new InsnNode(Opcodes.ACONST_NULL));
        il.add(new InsnNode(Opcodes.ARETURN));
        il.add(hasHolder);
        // future = holder.getFullChunkFuture()
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/server/level/ChunkHolder",
            "getFullChunkFuture", "()Ljava/util/concurrent/CompletableFuture;", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 3));
        // mainThreadProcessor.managedBlock(<isDone>)
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "mainThreadProcessor",
            "Lnet/minecraft/server/level/ServerChunkCache$MainThreadExecutor;"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new InvokeDynamicInsnNode(isDone.name, isDone.desc, isDone.bsm, isDone.bsmArgs));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/server/level/ServerChunkCache$MainThreadExecutor",
            "managedBlock", "(Ljava/util/function/BooleanSupplier;)V", false));
        // result = (ChunkResult) future.join()
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/util/concurrent/CompletableFuture", "join",
            "()Ljava/lang/Object;", false));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/server/level/ChunkResult"));
        il.add(new VarInsnNode(Opcodes.ASTORE, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new JumpInsnNode(Opcodes.IFNONNULL, hasResult));
        il.add(new InsnNode(Opcodes.ACONST_NULL));
        il.add(new InsnNode(Opcodes.ARETURN));
        il.add(hasResult);
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new InsnNode(Opcodes.ACONST_NULL));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "net/minecraft/server/level/ChunkResult", "orElse",
            "(Ljava/lang/Object;)Ljava/lang/Object;", true));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/world/level/chunk/LevelChunk"));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }
}
