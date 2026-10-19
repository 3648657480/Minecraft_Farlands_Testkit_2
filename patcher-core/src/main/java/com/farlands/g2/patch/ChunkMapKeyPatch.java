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
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * W4-1b: re-keys {@code ChunkMap}'s chunk-holder maps from the 32+32 packed
 * {@code long} ({@code ChunkPos.pack()}) to the 512-bit
 * {@link com.scalarwide.mc.WideChunkPosFlatMap} (key {@code com.scalarwide.mc.WideChunkPos},
 * per the W4 key ledger: x high 256 / z low 256).
 *
 * <p>The three maps ({@code updatingChunkMap} / {@code visibleChunkMap} /
 * {@code pendingUnloads}) are the chunk-holder registry; their packed-long keys
 * alias beyond 2^31 chunks, so chunk 2^49 (M2) cannot be addressed. Every
 * usage site keeps its original {@code long} argument: the op is redirected to
 * a static bridge injected here that widens the packed value
 * ({@code ChunkPos.pack()} layout: x high 32 / z low 32) into the 512-bit key.
 * In the int domain the widened key equals the packed one (sign-extended), so
 * behaviour there is unchanged; the wide request entry (W4-2) will construct
 * the key from the true wide {@code ChunkPos} coordinates instead.</p>
 *
 * <p>Rewrite shapes (all stack-shape preserving, measured with javap):
 * field descriptors; {@code new}/{@code <init>}; {@code clone()};
 * {@code isEmpty}/{@code size}; {@code values()ObjectCollection} →
 * {@code values()List} (only the collection accessor immediately following
 * that call is re-owned, so unrelated {@code Int2ObjectMap.values()} uses stay
 * intact); {@code get/put/remove/remove2/containsKey} → static bridges; and the
 * single {@code long2ObjectEntrySet()} loop in {@code dumpChunks} is rewritten
 * to iterate {@code values()} with the key derived from
 * {@code holder.getPos().pack()}.</p>
 *
 * <p>The class is read with {@code SKIP_FRAMES} and written with
 * {@code COMPUTE_FRAMES}: keeping the input StackMapTable would let stale
 * fastutil-typed frames merge with the recomputed ones and crash ASM's frame
 * merge.</p>
 *
 * <p>Iteration order becomes table order instead of insertion order (affects
 * unload/pending ordering only, not generation); {@code chunksToEagerlySave}
 * and {@code nextChunkSaveTime} stay 64-bit for now (recorded follow-up).</p>
 */
public final class ChunkMapKeyPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/server/level/ChunkMap";
    private static final String OLD_MAP = "it/unimi/dsi/fastutil/longs/Long2ObjectLinkedOpenHashMap";
    private static final String OLD_MAP_DESC = "L" + OLD_MAP + ";";
    private static final String OLD_COLL = "it/unimi/dsi/fastutil/objects/ObjectCollection";
    private static final String OLD_ENTSET =
        "it/unimi/dsi/fastutil/longs/Long2ObjectSortedMap$FastSortedEntrySet";
    private static final String OLD_ENTRY = "it/unimi/dsi/fastutil/longs/Long2ObjectMap$Entry";
    private static final String WIDE_MAP = "com/scalarwide/mc/WideChunkPosFlatMap";
    private static final String WIDE = "com/scalarwide/mc/WideChunkPos";
    private static final String CHUNKPOS = "net/minecraft/world/level/ChunkPos";
    private static final String HOLDER = "net/minecraft/server/level/ChunkHolder";
    private static final String FIELD_DESC = "L" + WIDE_MAP + ";";
    private static final String[] KEY_FIELDS = {"updatingChunkMap", "visibleChunkMap", "pendingUnloads"};

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, ClassReader.SKIP_FRAMES);

        for (FieldNode f : node.fields) {
            if ("updatingChunkMap".equals(f.name) && FIELD_DESC.equals(f.desc)) {
                return original; // already patched
            }
        }
        for (FieldNode f : node.fields) {
            if (isKeyField(f.name)) {
                f.desc = FIELD_DESC;
                f.signature = null; // drop the stale generic signature (old map type)
            }
        }
        for (MethodNode m : node.methods) {
            rewriteMethod(m);
        }
        node.methods.add(wideKey());
        node.methods.add(cmGet());
        node.methods.add(cmPut());
        node.methods.add(cmRemove());
        node.methods.add(cmRemove2());
        node.methods.add(cmContains());
        node.methods.add(cmGetW());
        node.methods.add(cmPutW());
        node.methods.add(cmRemoveW());
        node.methods.add(cmRemove2W());
        node.methods.add(cmContainsW());
        node.methods.add(cmKeyOf());

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static boolean isKeyField(String name) {
        for (String f : KEY_FIELDS) {
            if (f.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static void rewriteMethod(MethodNode m) {
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; ) {
            AbstractInsnNode next = insn.getNext();
            if (insn instanceof FieldInsnNode fi && TARGET.equals(fi.owner) && isKeyField(fi.name)
                && OLD_MAP_DESC.equals(fi.desc)) {
                fi.desc = FIELD_DESC;
            } else if (insn instanceof TypeInsnNode ti && ti.getOpcode() == Opcodes.NEW
                && OLD_MAP.equals(ti.desc)) {
                ti.desc = WIDE_MAP;
            } else if (insn instanceof TypeInsnNode ti && ti.getOpcode() == Opcodes.CHECKCAST
                && OLD_ENTRY.equals(ti.desc)) {
                ti.desc = HOLDER;
            } else if (insn instanceof MethodInsnNode mi) {
                rewriteInvoke(m, insn, mi);
            }
            insn = next;
        }
    }

    private static void rewriteInvoke(MethodNode m, AbstractInsnNode insn, MethodInsnNode mi) {
        if (OLD_MAP.equals(mi.owner)) {
            switch (mi.name + mi.desc) {
                case "<init>()V" -> mi.owner = WIDE_MAP;
                case "clone()" + OLD_MAP_DESC -> {
                    mi.owner = WIDE_MAP;
                    mi.desc = "()" + FIELD_DESC;
                }
                case "isEmpty()Z", "size()I" -> mi.owner = WIDE_MAP;
                case "values()Lit/unimi/dsi/fastutil/objects/ObjectCollection;" -> {
                    mi.owner = WIDE_MAP;
                    mi.desc = "()Ljava/util/List;";
                    rewriteFollower(insn);
                }
                case "get(J)Ljava/lang/Object;" -> bridge(mi, "farlands$cmGet",
                    "(L" + WIDE_MAP + ";J)Ljava/lang/Object;");
                case "put(JLjava/lang/Object;)Ljava/lang/Object;" -> bridge(mi, "farlands$cmPut",
                    "(L" + WIDE_MAP + ";JLjava/lang/Object;)Ljava/lang/Object;");
                case "remove(J)Ljava/lang/Object;" -> bridge(mi, "farlands$cmRemove",
                    "(L" + WIDE_MAP + ";J)Ljava/lang/Object;");
                case "remove(JLjava/lang/Object;)Z" -> bridge(mi, "farlands$cmRemove2",
                    "(L" + WIDE_MAP + ";JLjava/lang/Object;)Z");
                case "containsKey(J)Z" -> bridge(mi, "farlands$cmContains",
                    "(L" + WIDE_MAP + ";J)Z");
                case "long2ObjectEntrySet()Lit/unimi/dsi/fastutil/longs/Long2ObjectSortedMap$FastSortedEntrySet;" -> {
                    mi.owner = WIDE_MAP;
                    mi.name = "values";
                    mi.desc = "()Ljava/util/List;";
                    rewriteFollower(insn);
                }
                default -> {
                }
            }
        } else if (OLD_ENTRY.equals(mi.owner)) {
            if ("getLongKey".equals(mi.name)) {
                // [holder] -> holder.getPos().pack()
                InsnList add = new InsnList();
                add.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, HOLDER, "getPos",
                    "()L" + CHUNKPOS + ";", false));
                add.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNKPOS, "pack", "()J", false));
                m.instructions.insert(insn, add);
                m.instructions.remove(insn);
            } else if ("getValue".equals(mi.name)) {
                // [holder(entry)] -> the holder itself
                m.instructions.remove(insn);
            }
        }
    }

    /**
     * Rewrites the collection accessor that immediately follows a rewritten
     * {@code values()} call (for-each {@code iterator()}, {@code stream()},
     * {@code forEach()}). Other {@code ObjectCollection} uses in the class
     * belong to unrelated maps (e.g. {@code Int2ObjectMap.values()}) and must
     * be left untouched. Pseudo-instructions (line numbers, frames, labels) may
     * sit between the two calls.
     */
    private static void rewriteFollower(AbstractInsnNode valuesInsn) {
        AbstractInsnNode next = valuesInsn.getNext();
        while (next != null && next.getOpcode() < 0) {
            next = next.getNext();
        }
        if (!(next instanceof MethodInsnNode nm)) {
            return;
        }
        if (!OLD_COLL.equals(nm.owner) && !OLD_ENTSET.equals(nm.owner)) {
            return;
        }
        switch (nm.name) {
            case "iterator" -> {
                nm.owner = "java/util/List";
                nm.desc = "()Ljava/util/Iterator;";
            }
            case "stream", "forEach" -> nm.owner = "java/util/List";
            default -> {
            }
        }
    }

    private static void bridge(MethodInsnNode mi, String name, String desc) {
        mi.setOpcode(Opcodes.INVOKESTATIC);
        mi.owner = TARGET;
        mi.name = name;
        mi.desc = desc;
        mi.itf = false;
    }

    /**
     * {@code Wide512.of(l0..l7)} from the vanilla packed long, in the true
     * {@code ChunkPos.widePos} layout: z in the low half (l0-l3), x in the
     * high half (l4-l7).
     *
     * <p>Vanilla {@code pack(x, z)} is x-low32 / z-high32, so the low half of
     * the wide key must be {@code packed >> 32} (z) and the high half
     * {@code (int) packed} (x). This makes
     * {@code farlands$cmKey(pack(x,z)) == widePos(x,z)} - the same key the
     * wide holders use. (A swapped layout here silently aliased every
     * int-domain neighbour lookup against the wide-key queries - REV49
     * regression: only diagonal chunks matched.) */
    private static MethodNode wideKey() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "farlands$cmKey",
            "(J)L" + WIDE + ";", null, null);
        InsnList il = mn.instructions;
        // x = (int) packed (low 32); z = packed >> 32 (high 32)
        il.add(new VarInsnNode(Opcodes.LLOAD, 0));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.LLOAD, 0));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 32));
        il.add(new InsnNode(Opcodes.LSHR));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, WIDE, "pack",
            "(II)L" + WIDE + ";", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    private static MethodNode cmGet() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "farlands$cmGet",
            "(L" + WIDE_MAP + ";J)Ljava/lang/Object;", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 1));
        il.add(keyCall());
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WIDE_MAP, "get",
            "(L" + WIDE + ";)Ljava/lang/Object;", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    private static MethodNode cmPut() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "farlands$cmPut",
            "(L" + WIDE_MAP + ";JLjava/lang/Object;)Ljava/lang/Object;", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 1));
        il.add(keyCall());
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WIDE_MAP, "put",
            "(L" + WIDE + ";Ljava/lang/Object;)Ljava/lang/Object;", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    private static MethodNode cmRemove() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "farlands$cmRemove",
            "(L" + WIDE_MAP + ";J)Ljava/lang/Object;", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 1));
        il.add(keyCall());
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WIDE_MAP, "remove",
            "(L" + WIDE + ";)Ljava/lang/Object;", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    private static MethodNode cmRemove2() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "farlands$cmRemove2",
            "(L" + WIDE_MAP + ";JLjava/lang/Object;)Z", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 1));
        il.add(keyCall());
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WIDE_MAP, "remove",
            "(L" + WIDE + ";Ljava/lang/Object;)Z", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    private static MethodNode cmContains() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "farlands$cmContains",
            "(L" + WIDE_MAP + ";J)Z", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 1));
        il.add(keyCall());
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WIDE_MAP, "containsKey",
            "(L" + WIDE + ";)Z", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    private static MethodInsnNode keyCall() {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "farlands$cmKey",
            "(J)L" + WIDE + ";", false);
    }

    // ------------------------------------------------------------------
    // W4-2 slice 3 primitives: same ops, but the caller already holds the
    // Wide512 key (e.g. from holder.getPos().widePos()) - no packed-long
    // round trip, so far-domain keys survive. Used by the wide
    // DistanceManager / ChunkMap scheduling methods.
    // ------------------------------------------------------------------

    /** {@code map.get(key)} with a caller-supplied wide key. */
    private static MethodNode cmGetW() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "farlands$cmGetW",
            "(L" + WIDE_MAP + ";L" + WIDE + ";)Ljava/lang/Object;", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WIDE_MAP, "get",
            "(L" + WIDE + ";)Ljava/lang/Object;", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    private static MethodNode cmPutW() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "farlands$cmPutW",
            "(L" + WIDE_MAP + ";L" + WIDE + ";Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WIDE_MAP, "put",
            "(L" + WIDE + ";Ljava/lang/Object;)Ljava/lang/Object;", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    private static MethodNode cmRemoveW() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "farlands$cmRemoveW",
            "(L" + WIDE_MAP + ";L" + WIDE + ";)Ljava/lang/Object;", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WIDE_MAP, "remove",
            "(L" + WIDE + ";)Ljava/lang/Object;", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    private static MethodNode cmRemove2W() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "farlands$cmRemove2W",
            "(L" + WIDE_MAP + ";L" + WIDE + ";Ljava/lang/Object;)Z", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WIDE_MAP, "remove",
            "(L" + WIDE + ";Ljava/lang/Object;)Z", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    private static MethodNode cmContainsW() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "farlands$cmContainsW",
            "(L" + WIDE_MAP + ";L" + WIDE + ";)Z", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WIDE_MAP, "containsKey",
            "(L" + WIDE + ";)Z", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    /** The wide registry key of a holder: {@code holder.getPos().widePos()}. */
    private static MethodNode cmKeyOf() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "farlands$cmKeyOf",
            "(L" + HOLDER + ";)L" + WIDE + ";", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, HOLDER, "getPos",
            "()L" + CHUNKPOS + ";", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNKPOS, "widePos",
            "()L" + WIDE + ";", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }
}
