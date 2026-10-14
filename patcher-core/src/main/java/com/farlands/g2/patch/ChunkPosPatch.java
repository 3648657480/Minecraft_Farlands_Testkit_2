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
 * constructor carries wide positions, and {@code packWide()} produces the
 * 128-bit key ({@code com.scalarwide.Wide128}: x high limb, z low limb) for
 * wide consumers that outgrow the 32+32 {@code pack()}.</p>
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

            // Wide chunk key (W4 key ledger): (x:Wide256, z:Wide256) packed as one
            // Wide512 - x in the high 256 bits, z in the low 256 bits, both
            // sign-extended from the long fields. The 64-bit pack() stays for the
            // int domain; wide consumers key on this instead.
            MethodNode packWide = new MethodNode(Opcodes.ACC_PUBLIC, "packWide",
                "()Lcom/scalarwide/Wide512;", null, null);
            InsnList pw = packWide.instructions;
            pw.add(new VarInsnNode(Opcodes.ALOAD, 0));
            pw.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "zLong", "()J", false));
            pw.add(new InsnNode(Opcodes.DUP2));
            pw.add(new IntInsnNode(Opcodes.BIPUSH, 63));
            pw.add(new InsnNode(Opcodes.LSHR));
            pw.add(new InsnNode(Opcodes.DUP2));
            pw.add(new InsnNode(Opcodes.DUP2));
            pw.add(new VarInsnNode(Opcodes.ALOAD, 0));
            pw.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "xLong", "()J", false));
            pw.add(new InsnNode(Opcodes.DUP2));
            pw.add(new IntInsnNode(Opcodes.BIPUSH, 63));
            pw.add(new InsnNode(Opcodes.LSHR));
            pw.add(new InsnNode(Opcodes.DUP2));
            pw.add(new InsnNode(Opcodes.DUP2));
            pw.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/scalarwide/Wide512", "of",
                "(JJJJJJJJ)Lcom/scalarwide/Wide512;", false));
            pw.add(new InsnNode(Opcodes.ARETURN));
            node.methods.add(packWide);
        }

        addWideBlockAccessors(node);

        replaceRecordEquals(node);

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
