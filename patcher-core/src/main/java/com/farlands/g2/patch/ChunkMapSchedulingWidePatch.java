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
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * W4-2 slice 3-E (part 2): wide {@code updateChunkScheduling} in {@code ChunkMap}.
 *
 * <p>Mirrors the vanilla private long method with a wide key: wide holder
 * creation ({@code new ChunkHolder} with the wide {@link net.minecraft.world.level.ChunkPos}
 * built via the patched {@code (JJ)} constructor), wide map access through
 * {@code farlands$cmRemoveW/cmPutW} (from {@code ChunkMapKeyPatch}, so this patch
 * runs after it). The {@code this::onLevelChange} callback is reused verbatim by
 * copying the original method's {@code invokedynamic} instruction.</p>
 *
 * <p>{@code toDrop} is still a 64-bit {@code LongSet} (documented residual), so
 * the drop set is keyed by the truncated value.</p>
 */
public final class ChunkMapSchedulingWidePatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/server/level/ChunkMap";
    private static final String W512 = "com/scalarwide/Wide512";
    private static final String W512_D = "Lcom/scalarwide/Wide512;";
    private static final String CHUNK_POS = "net/minecraft/world/level/ChunkPos";
    private static final String HOLDER = "net/minecraft/server/level/ChunkHolder";
    private static final String HOLDER_D = "Lnet/minecraft/server/level/ChunkHolder;";
    private static final String WMAP = "com/scalarwide/mc/Wide512FlatMap";
    private static final String WMAP_D = "Lcom/scalarwide/mc/Wide512FlatMap;";
    private static final String LONG_SET = "it/unimi/dsi/fastutil/longs/LongSet";
    private static final String WK = "com/scalarwide/mc/WideChunkKey";
    private static final String CHUNK_LEVEL = "net/minecraft/server/level/ChunkLevel";
    private static final String ORIG_DESC = "(JI" + HOLDER_D + "I)" + HOLDER_D;

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, ClassReader.SKIP_FRAMES);
        if (!TARGET.equals(node.name)) {
            return original;
        }
        if (node.methods.stream().anyMatch(mn -> "farlands$updateChunkSchedulingW".equals(mn.name))) {
            return original;
        }

        MethodNode orig = null;
        for (MethodNode mn : node.methods) {
            if ("updateChunkScheduling".equals(mn.name) && ORIG_DESC.equals(mn.desc)) {
                orig = mn;
                break;
            }
        }
        if (orig == null) {
            throw new IllegalStateException(TARGET + "#updateChunkScheduling(long,...) not found");
        }
        InvokeDynamicInsnNode callback = null;
        MethodInsnNode holderCtor = null;
        for (AbstractInsnNode insn = orig.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof InvokeDynamicInsnNode idy && callback == null) {
                callback = idy;
            } else if (insn instanceof MethodInsnNode mi && HOLDER.equals(mi.owner) && "<init>".equals(mi.name)) {
                holderCtor = mi;
            }
        }
        if (callback == null || holderCtor == null) {
            throw new IllegalStateException(TARGET + "#updateChunkScheduling: callback/ctor site not found");
        }

        String updatingMap = fieldDesc(node, "updatingChunkMap");
        String pendingUnloads = fieldDesc(node, "pendingUnloads");
        String toDrop = fieldDesc(node, "toDrop");
        String levelField = fieldDesc(node, "level");
        String lightEngine = fieldDesc(node, "lightEngine");
        String modified = fieldDesc(node, "modified");

        node.methods.add(chunkPosOfW());
        node.methods.add(packTruncW());
        node.methods.add(getUpdatingChunkW(updatingMap));
        InvokeDynamicInsnNode callbackCopy = new InvokeDynamicInsnNode(callback.name, callback.desc,
            callback.bsm, callback.bsmArgs);
        node.methods.add(updateW(callbackCopy, holderCtor.desc, updatingMap, pendingUnloads, toDrop,
            levelField, lightEngine, modified));

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static String fieldDesc(ClassNode node, String name) {
        for (FieldNode f : node.fields) {
            if (name.equals(f.name)) {
                return f.desc;
            }
        }
        throw new IllegalStateException(TARGET + "#" + name + " not found");
    }

    private static MethodNode m(int access, String name, String desc) {
        return new MethodNode(access, name, desc, null, null);
    }

    /** Wide holder lookup for the testgen harness: {@code (ChunkHolder) cmGetW(updatingChunkMap, key)}. */
    private static MethodNode getUpdatingChunkW(String updatingMap) {
        MethodNode mn = m(Opcodes.ACC_PUBLIC, "farlands$getUpdatingChunkW", "(" + W512_D + ")" + HOLDER_D);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "updatingChunkMap", updatingMap));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "farlands$cmGetW",
            "(" + WMAP_D + W512_D + ")Ljava/lang/Object;", false));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, HOLDER));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    /** {@code new ChunkPos(xLong(node), zLong(node))}. */
    private static MethodNode chunkPosOfW() {
        MethodNode mn = m(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "farlands$chunkPosOfW", "(" + W512_D + ")L" + CHUNK_POS + ";");
        InsnList il = mn.instructions;
        il.add(new TypeInsnNode(Opcodes.NEW, CHUNK_POS));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, WK, "x", "(" + W512_D + ")J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, WK, "z", "(" + W512_D + ")J", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, CHUNK_POS, "<init>", "(JJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    /** {@code ChunkPos.pack((int)x,(int)z)} for the 64-bit toDrop set. */
    private static MethodNode packTruncW() {
        MethodNode mn = m(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "farlands$packTruncW", "(" + W512_D + ")J");
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

    private static MethodNode updateW(InvokeDynamicInsnNode callback, String ctorDesc, String updatingMap,
                                      String pendingUnloads, String toDrop, String levelField,
                                      String lightEngine, String modified) {
        MethodNode mn = m(0, "farlands$updateChunkSchedulingW", "(" + W512_D + "I" + HOLDER_D + "I)" + HOLDER_D);
        InsnList il = mn.instructions;
        LabelNode l1 = new LabelNode();
        LabelNode l2 = new LabelNode();
        LabelNode l3 = new LabelNode();
        LabelNode drop = new LabelNode();
        LabelNode haveChunk = new LabelNode();
        LabelNode nullChunk = new LabelNode();
        LabelNode put = new LabelNode();
        LabelNode ret = new LabelNode();

        // if (!ChunkLevel.isLoaded(oldLevel) && !ChunkLevel.isLoaded(level)) return chunk;
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CHUNK_LEVEL, "isLoaded", "(I)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFNE, l1));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CHUNK_LEVEL, "isLoaded", "(I)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, ret));
        il.add(l1);
        // if (chunk != null) chunk.setTicketLevel(level);
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new JumpInsnNode(Opcodes.IFNULL, l2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, HOLDER, "setTicketLevel", "(I)V", false));
        il.add(l2);
        // if (chunk != null) { toDrop add/remove }
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new JumpInsnNode(Opcodes.IFNULL, l3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "toDrop", toDrop));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "farlands$packTruncW", "(" + W512_D + ")J", false));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CHUNK_LEVEL, "isLoaded", "(I)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFNE, drop));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, LONG_SET, "add", "(J)Z", true));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new JumpInsnNode(Opcodes.GOTO, l3));
        il.add(drop);
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, LONG_SET, "remove", "(J)Z", true));
        il.add(new InsnNode(Opcodes.POP));
        il.add(l3);
        // if (ChunkLevel.isLoaded(level) && chunk == null) { ... }
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CHUNK_LEVEL, "isLoaded", "(I)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, ret));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new JumpInsnNode(Opcodes.IFNONNULL, ret));
        // chunk = (ChunkHolder) farlands$cmRemoveW(pendingUnloads, node)
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "pendingUnloads", updatingMap));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "farlands$cmRemoveW",
            "(" + WMAP_D + W512_D + ")Ljava/lang/Object;", false));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, HOLDER));
        il.add(new VarInsnNode(Opcodes.ASTORE, 3));
        // if (chunk != null) setTicketLevel else new ChunkHolder(...)
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new JumpInsnNode(Opcodes.IFNULL, nullChunk));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, HOLDER, "setTicketLevel", "(I)V", false));
        il.add(new JumpInsnNode(Opcodes.GOTO, put));
        il.add(nullChunk);
        il.add(new TypeInsnNode(Opcodes.NEW, HOLDER));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "farlands$chunkPosOfW",
            "(" + W512_D + ")L" + CHUNK_POS + ";", false));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "level", levelField));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "lightEngine", lightEngine));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(callback);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, HOLDER, "<init>", ctorDesc, false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 3));
        il.add(put);
        // farlands$cmPutW(updatingChunkMap, node, chunk) + this.modified = true
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "updatingChunkMap", updatingMap));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "farlands$cmPutW",
            "(" + WMAP_D + W512_D + "Ljava/lang/Object;)Ljava/lang/Object;", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "modified", modified));
        il.add(ret);
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }
}
