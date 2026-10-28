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
 * Wide positional random factories.
 *
 * <p>{@code PositionalRandomFactory.at(BlockPos)} hashes the int block
 * coordinates, so at 2^31 the far positive and far negative halves share the
 * same worldgen randomness. Add {@code atWide(long, int, long)} to the
 * interface and both factories; inside the int range the seed is computed with
 * the exact vanilla int arithmetic (bit-identical), outside it uses the long
 * coordinates.</p>
 */
public final class PositionalRandomWidePatch implements ClassPatch {

    private static final String IFACE = "net/minecraft/world/level/levelgen/PositionalRandomFactory";
    private static final String LEGACY =
        "net/minecraft/world/level/levelgen/LegacyRandomSource$LegacyPositionalRandomFactory";
    private static final String XOROSHIRO =
        "net/minecraft/world/level/levelgen/XoroshiroRandomSource$XoroshiroPositionalRandomFactory";
    private static final String RANDOM = "net/minecraft/util/RandomSource";
    private static final String WIDE_SEED = "com/farlands/g2/runtime/WideSeed";
    private static final String AT_WIDE_DESC = "(JIJ)L" + RANDOM + ";";

    @Override
    public boolean matches(String internalName) {
        return IFACE.equals(internalName) || LEGACY.equals(internalName) || XOROSHIRO.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        if (hasAtWide(node)) {
            return original;
        }
        if (IFACE.equals(node.name)) {
            patchInterface(node);
        } else if (LEGACY.equals(node.name)) {
            node.methods.add(legacyAtWide());
        } else if (XOROSHIRO.equals(node.name)) {
            node.methods.add(xoroshiroAtWide());
        } else {
            return original;
        }
        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static boolean hasAtWide(ClassNode node) {
        for (MethodNode m : node.methods) {
            if ("atWide".equals(m.name) && AT_WIDE_DESC.equals(m.desc)) {
                return true;
            }
        }
        return false;
    }

    /** default RandomSource atWide(long x, int y, long z) { return at((int) x, y, (int) z); } */
    private static MethodNode defaultAtWide() {
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, "atWide", AT_WIDE_DESC, null, null);
        InsnList il = m.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 1));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new VarInsnNode(Opcodes.LLOAD, 4));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, IFACE, "at",
            "(III)L" + RANDOM + ";", true));
        il.add(new InsnNode(Opcodes.ARETURN));
        return m;
    }

    /** Rewrites at(BlockPos) to call atWide with the wide coordinates. */
    private static void patchInterface(ClassNode node) {
        MethodNode atPos = null;
        for (MethodNode m : node.methods) {
            if ("at".equals(m.name)
                && ("(Lnet/minecraft/core/BlockPos;)L" + RANDOM + ";").equals(m.desc)) {
                atPos = m;
            }
        }
        if (atPos == null) {
            throw new IllegalStateException(IFACE + ": at(BlockPos) not found");
        }
        atPos.instructions.clear();
        atPos.localVariables = null;
        atPos.tryCatchBlocks.clear();
        InsnList il = atPos.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "getLongX", "()J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "getY", "()I", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "getLongZ", "()J", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, IFACE, "atWide", AT_WIDE_DESC, true));
        il.add(new InsnNode(Opcodes.ARETURN));
        node.methods.add(defaultAtWide());
    }

    private static MethodNode legacyAtWide() {
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, "atWide", AT_WIDE_DESC, null, null);
        InsnList il = m.instructions;
        appendSeed(il, LEGACY, "seed");
        il.add(new TypeInsnNode(Opcodes.NEW, "net/minecraft/world/level/levelgen/LegacyRandomSource"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.LLOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
            "net/minecraft/world/level/levelgen/LegacyRandomSource", "<init>", "(J)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return m;
    }

    private static MethodNode xoroshiroAtWide() {
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, "atWide", AT_WIDE_DESC, null, null);
        InsnList il = m.instructions;
        appendSeed(il, XOROSHIRO, "seedLo");
        il.add(new TypeInsnNode(Opcodes.NEW, "net/minecraft/world/level/levelgen/XoroshiroRandomSource"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.LLOAD, 6));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, XOROSHIRO, "seedHi", "J"));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
            "net/minecraft/world/level/levelgen/XoroshiroRandomSource", "<init>", "(JJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return m;
    }

    /** local6 = WideSeed.at(x, y, z) ^ this.&lt;seedField&gt; */
    private static void appendSeed(InsnList il, String owner, String seedField) {
        il.add(new VarInsnNode(Opcodes.LLOAD, 1));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new VarInsnNode(Opcodes.LLOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, WIDE_SEED, "at", "(JIJ)J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, owner, seedField, "J"));
        il.add(new InsnNode(Opcodes.LXOR));
        il.add(new VarInsnNode(Opcodes.LSTORE, 6));
    }
}
