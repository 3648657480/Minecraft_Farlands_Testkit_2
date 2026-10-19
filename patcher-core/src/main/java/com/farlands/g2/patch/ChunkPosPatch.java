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
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Widens {@code ChunkPos}'s x/z storage from int to long.
 *
 * <p>All existing int accessors ({@code x()/z()}, {@code asLong()},
 * equals/hashCode) preserve their wrapped semantics, so every consumer and
 * storage key behaves exactly as before. New {@code xLong()/zLong()} expose
 * the wide values for the far-domain generation pipeline, a (JJ)
 * constructor carries wide positions. The 64-bit {@code pack()} remains for
 * the legacy int domain; new consumers use {@code widePos()} from
 * {@code ChunkPosWideIdentityPatch}.</p>
 */
public final class ChunkPosPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/ChunkPos";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName) || internalName.startsWith(TARGET + "$");
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        if (!TARGET.equals(node.name)) {
            // Nestmate (e.g. ChunkPos$2): only adapt field accesses to the widened fields.
            boolean changed = false;
            for (MethodNode m : node.methods) {
                for (AbstractInsnNode insn : m.instructions) {
                    if (insn instanceof FieldInsnNode fi && TARGET.equals(fi.owner)
                        && (fi.name.equals("x") || fi.name.equals("z"))) {
                        if (fi.getOpcode() == Opcodes.GETFIELD && "I".equals(fi.desc)) {
                            fi.desc = "J";
                            m.instructions.insert(insn, new InsnNode(Opcodes.L2I));
                            changed = true;
                        } else if (fi.getOpcode() == Opcodes.PUTFIELD && "I".equals(fi.desc)) {
                            fi.desc = "J";
                            m.instructions.insertBefore(insn, new InsnNode(Opcodes.I2L));
                            changed = true;
                        }
                    }
                }
            }
            if (!changed) {
                return original;
            }
            ClassWriter cwNest = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
            node.accept(cwNest);
            return cwNest.toByteArray();
        }

        boolean widened = false;
        for (FieldNode f : node.fields) {
            if ((f.name.equals("x") || f.name.equals("z")) && "I".equals(f.desc)) {
                f.desc = "J";
                widened = true;
            }
        }
        if (!widened) {
            return original;
        }

        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof FieldInsnNode fi && TARGET.equals(fi.owner)
                    && (fi.name.equals("x") || fi.name.equals("z"))) {
                    if (fi.getOpcode() == Opcodes.GETFIELD && "I".equals(fi.desc)) {
                        fi.desc = "J";
                        m.instructions.insert(insn, new InsnNode(Opcodes.L2I));
                    } else if (fi.getOpcode() == Opcodes.PUTFIELD && "I".equals(fi.desc)) {
                        fi.desc = "J";
                        m.instructions.insertBefore(insn, new InsnNode(Opcodes.I2L));
                    }
                }
            }
        }

        if (node.methods.stream().noneMatch(m -> "xLong".equals(m.name))) {
            for (String axis : new String[]{"x", "z"}) {
                MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, axis + "Long", "()J", null, null);
                mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                mn.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, axis, "J"));
                mn.instructions.add(new InsnNode(Opcodes.LRETURN));
                node.methods.add(mn);
            }
            // (JJ) constructor carrying wide coordinates (stores fields directly,
            // without delegating to the truncating canonical constructor)
            MethodNode ctor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(JJ)V", null, null);
            InsnList il = ctor.instructions;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Record", "<init>", "()V", false));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new VarInsnNode(Opcodes.LLOAD, 1));
            il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "x", "J"));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new VarInsnNode(Opcodes.LLOAD, 3));
            il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "z", "J"));
            il.add(new InsnNode(Opcodes.RETURN));
            node.methods.add(ctor);
        }

        addWideBlockAccessors(node);

        replaceRecordEquals(node);

        rewritePackBlockPos(node);

        rewriteContainingBlockPos(node);

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    /**
     * Wide block-coordinate accessors: {@code getMinBlockXLong()},
     * {@code getMiddleBlockXLong()}, {@code getBlockXLong(int)} (and Z).
     *
     * <p>The vanilla int accessors derive block coordinates with
     * {@code SectionPos.sectionToBlockCoord}, i.e. {@code chunk.x << 4} in the
     * int domain, which wraps at chunk {@code 2^27} (block {@code 2^31}).
     * Carvers and the carver-biome lookup consume those int results as
     * doubles/long grid coordinates, so a wrapped chunk silently carves the
     * wrong columns. These long variants compute the same expression from the
     * widened x/z storage; at normal coordinates every result equals the int
     * one it replaces.</p>
     */
    /**
     * {@code pack(BlockPos)}: derive the chunk key from the wide block fields
     * ({@code (int)(getLongX()>>4)}); the int view wraps at block 2^31 and puts
     * worldgen/tick reads on the negative chunk. Chunk coords fit an int over the
     * whole int block domain; at normal coordinates the key is identical.
     */
    private static void rewritePackBlockPos(ClassNode node) {
        MethodNode m = null;
        for (MethodNode mn : node.methods) {
            if ("pack".equals(mn.name) && "(Lnet/minecraft/core/BlockPos;)J".equals(mn.desc)) {
                m = mn;
                break;
            }
        }
        if (m == null) {
            return;
        }
        for (AbstractInsnNode n : m.instructions) {
            if (n instanceof MethodInsnNode mi && "getLongX".equals(mi.name)) {
                return; // already patched
            }
        }
        m.instructions.clear();
        m.localVariables = null;
        m.tryCatchBlocks.clear();
        InsnList il = m.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongX", "()J", false));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 4));
        il.add(new InsnNode(Opcodes.LSHR));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongZ", "()J", false));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 4));
        il.add(new InsnNode(Opcodes.LSHR));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "pack", "(II)J", false));
        il.add(new InsnNode(Opcodes.LRETURN));
    }

    /**
     * {@code containing(BlockPos)}: vanilla derives the chunk from
     * {@code BlockPos.getX()}, which wraps at block 2^31 and puts the client's
     * view center on the negative chunk band while the server sends the
     * positive east chunks. Use the wide block fields; identical at normal
     * coordinates.
     */
    private static void rewriteContainingBlockPos(ClassNode node) {
        MethodNode m = null;
        for (MethodNode mn : node.methods) {
            if ("containing".equals(mn.name)
                && "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/ChunkPos;".equals(mn.desc)) {
                m = mn;
                break;
            }
        }
        if (m == null) {
            throw new IllegalStateException(TARGET + "#containing(BlockPos) not found");
        }
        for (AbstractInsnNode n : m.instructions) {
            if (n instanceof MethodInsnNode mi && "getLongX".equals(mi.name)) {
                return; // already patched
            }
        }
        m.instructions.clear();
        m.localVariables = null;
        m.tryCatchBlocks.clear();
        InsnList il = m.instructions;
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        for (String axis : new String[]{"X", "Z"}) {
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i",
                "getLong" + axis, "()J", false));
            il.add(new IntInsnNode(Opcodes.BIPUSH, 4));
            il.add(new InsnNode(Opcodes.LSHR));
            il.add(new InsnNode(Opcodes.L2I));
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(II)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
    }

    private static void addWideBlockAccessors(ClassNode node) {
        if (node.methods.stream().anyMatch(m -> "getMinBlockXLong".equals(m.name))) {
            return;
        }
        for (String axis : new String[]{"x", "z"}) {
            String suffix = axis.toUpperCase(java.util.Locale.ROOT);
            node.methods.add(blockAccessor(axis, "getMinBlock" + suffix + "Long", 0L));
            node.methods.add(blockAccessor(axis, "getMiddleBlock" + suffix + "Long", 8L));
            MethodNode offset = new MethodNode(Opcodes.ACC_PUBLIC,
                "getBlock" + suffix + "Long", "(I)J", null, null);
            InsnList oi = offset.instructions;
            oi.add(new VarInsnNode(Opcodes.ALOAD, 0));
            oi.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, axis, "J"));
            oi.add(new LdcInsnNode(4));
            oi.add(new InsnNode(Opcodes.LSHL));
            oi.add(new VarInsnNode(Opcodes.ILOAD, 1));
            oi.add(new InsnNode(Opcodes.I2L));
            oi.add(new InsnNode(Opcodes.LADD));
            oi.add(new InsnNode(Opcodes.LRETURN));
            node.methods.add(offset);
        }
    }

    /** {@code (chunkX << 4) + constant}. */
    private static MethodNode blockAccessor(String axis, String name, long constant) {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, name, "()J", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, axis, "J"));
        il.add(new LdcInsnNode(4));
        il.add(new InsnNode(Opcodes.LSHL));
        if (constant != 0L) {
            il.add(new LdcInsnNode(constant));
            il.add(new InsnNode(Opcodes.LADD));
        }
        il.add(new InsnNode(Opcodes.LRETURN));
        return mn;
    }

    /**
     * The record equals invokedynamic bootstraps with REF_getField handles,
     * which break once the fields are widened to long. Replace it with an
     * explicit implementation using the wrapped int view.
     */
    private static void replaceRecordEquals(ClassNode node) {
        MethodNode equals = null;
        for (MethodNode m : node.methods) {
            if ("equals".equals(m.name) && "(Ljava/lang/Object;)Z".equals(m.desc)) {
                equals = m;
                break;
            }
        }
        if (equals == null) {
            throw new IllegalStateException(TARGET + "#equals not found");
        }
        InsnList il = new InsnList();
        org.objectweb.asm.tree.LabelNode notSame = new org.objectweb.asm.tree.LabelNode();
        org.objectweb.asm.tree.LabelNode notInstance = new org.objectweb.asm.tree.LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IF_ACMPNE, notSame));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(notSame);
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.INSTANCEOF, TARGET));
        il.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFEQ, notInstance));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "x", "J"));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.CHECKCAST, TARGET));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "x", "J"));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IF_ICMPNE, notInstance));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "z", "J"));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.CHECKCAST, TARGET));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "z", "J"));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IF_ICMPNE, notInstance));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(notInstance);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));
        equals.instructions.clear();
        equals.localVariables = null;
        equals.tryCatchBlocks.clear();
        equals.instructions.add(il);
    }
}
