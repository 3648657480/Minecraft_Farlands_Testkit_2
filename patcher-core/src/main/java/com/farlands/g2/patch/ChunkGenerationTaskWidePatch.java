package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * W4-2 slice 3-G: wide generation-neighbour acquisition in
 * {@code ChunkGenerationTask}.
 *
 * <p>{@code create} builds a {@code StaticCache2D} centred on the chunk's
 * <b>int</b> coordinates and fills it through {@code (x,z) -> acquireGeneration(
 * ChunkPos.pack(x,z))}. For a far chunk every cache cell key is truncated. The
 * cache's int coordinates are only an offset space, so the fix is local: the
 * initializer lambda additionally captures the centre {@code ChunkPos} and
 * resolves each cell as {@code WideChunkKey.neighbor(centre.packWide(), x-cx,
 * z-cz)} into the wide acquisition. Exact in the int domain (the wide key equals
 * the packed key there).</p>
 */
public final class ChunkGenerationTaskWidePatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/server/level/ChunkGenerationTask";
    private static final String GEN_MAP = "net/minecraft/server/level/GeneratingChunkMap";
    private static final String GEN_MAP_D = "Lnet/minecraft/server/level/GeneratingChunkMap;";
    private static final String GEN_HOLDER = "net/minecraft/server/level/GenerationChunkHolder";
    private static final String GEN_HOLDER_D = "Lnet/minecraft/server/level/GenerationChunkHolder;";
    private static final String CHUNK_POS = "net/minecraft/world/level/ChunkPos";
    private static final String CHUNK_POS_D = "Lnet/minecraft/world/level/ChunkPos;";
    private static final String W512_D = "Lcom/scalarwide/Wide512;";
    private static final String WK = "com/scalarwide/mc/WideChunkKey";
    private static final String INITIALIZER = "net/minecraft/util/StaticCache2D$Initializer";
    private static final String WIDE_SIG =
        "(" + GEN_MAP_D + CHUNK_POS_D + "II)" + GEN_HOLDER_D;

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
        if (node.methods.stream().anyMatch(mn -> "farlands$acquireWide".equals(mn.name))) {
            return original;
        }
        node.methods.add(acquireWide());

        // rewrite the cache initializer indy inside create(...)
        MethodNode create = null;
        for (MethodNode mn : node.methods) {
            if ("create".equals(mn.name) && mn.desc.startsWith("(" + GEN_MAP_D)) {
                create = mn;
                break;
            }
        }
        if (create == null) {
            throw new IllegalStateException(TARGET + "#create not found");
        }
        InvokeDynamicInsnNode target = null;
        for (AbstractInsnNode insn = create.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof InvokeDynamicInsnNode idy
                && idy.desc.endsWith("L" + INITIALIZER + ";")
                && idy.bsmArgs.length >= 3) {
                target = idy;
                break;
            }
        }
        if (target == null) {
            throw new IllegalStateException(TARGET + "#create initializer indy not found");
        }
        Handle impl = new Handle(Opcodes.H_INVOKESTATIC, TARGET, "farlands$acquireWide", WIDE_SIG, false);
        // only the implementation method changes; samMethodType (args[0]) and
        // instantiatedMethodType (args[2], captures excluded: "(II)L...;") stay.
        Object[] args = target.bsmArgs.clone();
        args[1] = impl;
        InvokeDynamicInsnNode replaced = new InvokeDynamicInsnNode(target.name,
            "(" + GEN_MAP_D + CHUNK_POS_D + ")L" + INITIALIZER + ";", target.bsm, args);
        create.instructions.insertBefore(target, new VarInsnNode(Opcodes.ALOAD, 2)); // centre pos
        create.instructions.set(target, replaced);
        injectTaskDiag(create);

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    /** Debug aid: print far-domain task centre/target/cache radius right before cache construction. */
    private static void injectTaskDiag(MethodNode create) {
        MethodInsnNode createCall = null;
        for (AbstractInsnNode insn = create.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode mi && "create".equals(mi.name)
                && mi.owner.startsWith("net/minecraft/util/StaticCache2D")) {
                createCall = mi;
                break;
            }
        }
        if (createCall == null) {
            return;
        }
        InsnList il = new InsnList();
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
        il.add(new TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new LdcInsnNode("genTask far pos="));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>",
            "(Ljava/lang/String;)V", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNK_POS, "xLong", "()J", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
            "(J)Ljava/lang/StringBuilder;", false));
        il.add(new LdcInsnNode(","));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNK_POS, "zLong", "()J", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
            "(J)Ljava/lang/StringBuilder;", false));
        il.add(new LdcInsnNode(" target="));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/chunk/status/ChunkStatus",
            "getName", "()Ljava/lang/String;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
        il.add(new LdcInsnNode(" r="));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
            "(I)Ljava/lang/StringBuilder;", false));
        il.add(new LdcInsnNode(" src="));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
        // new Throwable().getStackTrace()[2..3]
        for (int frame = 4; frame <= 5; frame++) {
            il.add(new TypeInsnNode(Opcodes.NEW, "java/lang/Throwable"));
            il.add(new InsnNode(Opcodes.DUP));
            il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Throwable", "<init>", "()V", false));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable", "getStackTrace",
                "()[Ljava/lang/StackTraceElement;", false));
            il.add(new IntInsnNode(Opcodes.BIPUSH, frame));
            il.add(new InsnNode(Opcodes.AALOAD));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StackTraceElement", "toString",
                "()Ljava/lang/String;", false));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
            if (frame == 2) {
                il.add(new LdcInsnNode(" | "));
                il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
            }
        }
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString",
            "()Ljava/lang/String;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println",
            "(Ljava/lang/String;)V", false));
        create.instructions.insertBefore(createCall, il);
    }

    /**
     * {@code (map, centre, x, z) -> map.farlands$acquireGenerationW(key(cell))}.
     *
     * <p>All cell keys are built in the true {@code ChunkPos.packWide} layout;
     * the long-path {@code ChunkMap.farlands$cmKey} translation produces the
     * same key (unified encoding).</p>
     */
    private static MethodNode acquireWide() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "farlands$acquireWide",
            WIDE_SIG, null, null);
        InsnList il = mn.instructions;
        LabelNode have = new LabelNode();
        // cx, cz (exact long coords of the centre)
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNK_POS, "xLong", "()J", false));
        il.add(new VarInsnNode(Opcodes.LSTORE, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNK_POS, "zLong", "()J", false));
        il.add(new VarInsnNode(Opcodes.LSTORE, 6));
        // offsets (int-coord space)
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNK_POS, "x", "()I", false));
        il.add(new InsnNode(Opcodes.ISUB));
        il.add(new VarInsnNode(Opcodes.ISTORE, 8));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNK_POS, "z", "()I", false));
        il.add(new InsnNode(Opcodes.ISUB));
        il.add(new VarInsnNode(Opcodes.ISTORE, 9));
        // cell coords (exact)
        il.add(new VarInsnNode(Opcodes.LLOAD, 4));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 10));
        il.add(new VarInsnNode(Opcodes.LLOAD, 6));
        il.add(new VarInsnNode(Opcodes.ILOAD, 9));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 12));
        // cell key: true packWide layout, unified with the long-path cmKey
        // translation (both now produce ChunkPos.packWide(x, z))
        il.add(new VarInsnNode(Opcodes.LLOAD, 10));
        il.add(new VarInsnNode(Opcodes.LLOAD, 12));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, WK, "pack", "(JJ)" + W512_D, false));
        il.add(have);
        il.add(new VarInsnNode(Opcodes.ASTORE, 14));
        // pre-check: on miss throw with centre + cell coordinates (debug aid)
        LabelNode ok = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 14));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, GEN_MAP, "farlands$hasGenerationW",
            "(" + W512_D + ")Z", true));
        il.add(new JumpInsnNode(Opcodes.IFNE, ok));
        il.add(new TypeInsnNode(Opcodes.NEW, "java/lang/IllegalStateException"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new LdcInsnNode("acquireWide miss centre=("));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>",
            "(Ljava/lang/String;)V", false));
        il.add(new VarInsnNode(Opcodes.LLOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
            "(J)Ljava/lang/StringBuilder;", false));
        il.add(new LdcInsnNode(","));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
        il.add(new VarInsnNode(Opcodes.LLOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
            "(J)Ljava/lang/StringBuilder;", false));
        il.add(new LdcInsnNode(") cell=("));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
        il.add(new VarInsnNode(Opcodes.LLOAD, 10));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
            "(J)Ljava/lang/StringBuilder;", false));
        il.add(new LdcInsnNode(","));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
        il.add(new VarInsnNode(Opcodes.LLOAD, 12));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
            "(J)Ljava/lang/StringBuilder;", false));
        il.add(new LdcInsnNode(")"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString",
            "()Ljava/lang/String;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>",
            "(Ljava/lang/String;)V", false));
        il.add(new InsnNode(Opcodes.ATHROW));
        il.add(ok);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 14));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, GEN_MAP, "farlands$acquireGenerationW",
            "(" + W512_D + ")" + GEN_HOLDER_D, true));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }
}
