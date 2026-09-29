package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * W5-D: keeps {@code MonsterRoomFeature.place}'s chest position wide.
 *
 * <p>The room scan already goes through the widened {@code BlockPos.offset}
 * (the patch set rewrote that method), but the chest placement loop rebuilds
 * its position from the int view:
 * {@code x = origin.getX() + rand - offset}. At the 2^31 seam the origin's
 * int view is negative, so every chest position is sign-extended back to the
 * negative far side and the write is dropped as "far chunk". The loop is
 * rebuilt in the long domain: {@code getLongX/Z} plus long arithmetic, and
 * the position is built with the widened {@code BlockPos(long,long,long)}
 * constructor. Random-call order and the y value are unchanged, so at normal
 * coordinates the produced positions and the random stream are identical.</p>
 */
public final class MonsterRoomFeaturePatch implements ClassPatch {

    private static final String TARGET =
        "net/minecraft/world/level/levelgen/feature/MonsterRoomFeature";
    private static final String PLACE_DESC =
        "(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z";
    private static final String BLOCKPOS = "net/minecraft/core/BlockPos";
    private static final String RANDOM = "net/minecraft/util/RandomSource";

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
        MethodNode place = find(node, "place", PLACE_DESC);
        if (place == null) {
            throw new IllegalStateException(TARGET + "#place not found");
        }
        for (AbstractInsnNode insn : place.instructions) {
            if (insn instanceof MethodInsnNode mi && BLOCKPOS.equals(mi.owner)
                    && "getLongX".equals(mi.name)) {
                return original; // already patched
            }
        }

        // The chest loop's position constructor: new BlockPos(x18, y19, z20).
        MethodInsnNode ctor = null;
        for (AbstractInsnNode insn : place.instructions) {
            if (insn instanceof MethodInsnNode mi && mi.getOpcode() == Opcodes.INVOKESPECIAL
                    && BLOCKPOS.equals(mi.owner) && "<init>".equals(mi.name)
                    && "(III)V".equals(mi.desc)) {
                AbstractInsnNode prev = mi.getPrevious();
                if (prev instanceof VarInsnNode v && v.var == 20) {
                    ctor = mi;
                    break;
                }
            }
        }
        if (ctor == null) {
            throw new IllegalStateException(TARGET + "#place: chest BlockPos ctor not found");
        }
        // Walk back to the start of the x expression: ALOAD origin; getX().
        AbstractInsnNode start = null;
        for (AbstractInsnNode n = ctor; n != null; n = n.getPrevious()) {
            if (n instanceof VarInsnNode v && v.getOpcode() == Opcodes.ALOAD && v.var == 3
                    && n.getNext() instanceof MethodInsnNode g && BLOCKPOS.equals(g.owner)
                    && "getX".equals(g.name) && "()I".equals(g.desc)) {
                start = n;
                break;
            }
        }
        if (start == null) {
            throw new IllegalStateException(TARGET + "#place: chest x expression not found");
        }

        place.instructions.insertBefore(start, emitChestPos());
        // Remove the old int expression but KEEP label nodes: debug
        // LineNumberNodes outside the range can share a start label with a
        // LineNumberNode inside it, and a dangling label makes the frame
        // computation bail with an ArrayIndexOutOfBoundsException. Labels are
        // stack-neutral, so leaving them is safe.
        for (AbstractInsnNode n = start; n != null; ) {
            AbstractInsnNode next = n.getNext();
            if (!(n instanceof org.objectweb.asm.tree.LabelNode)) {
                place.instructions.remove(n);
            }
            if (n == ctor) {
                break;
            }
            n = next;
        }
        place.localVariables = null;

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    @Override
    public String describe(String internalName) {
        return "MonsterRoomFeaturePatch (wide chest position at 2^31)";
    }

    /**
     * {@code new BlockPos(origin.getLongX() + rand.nextInt(2*k+1) - k,
     *                    origin.getY(),
     *                    origin.getLongZ() + rand.nextInt(2*m+1) - m)}
     * with locals 3 = origin, 4 = random, 7 = k, 12 = m; the position is left
     * on the stack for the caller's original {@code ASTORE 21}.
     */
    private static InsnList emitChestPos() {
        InsnList il = new InsnList();
        il.add(new TypeInsnNode(Opcodes.NEW, BLOCKPOS));
        il.add(new InsnNode(Opcodes.DUP));
        // X
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongX", "()J", false));
        il.add(randomOffset(4, 7));
        il.add(new VarInsnNode(Opcodes.ILOAD, 7));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LSUB));
        // Y
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getY", "()I", false));
        il.add(new InsnNode(Opcodes.I2L));
        // Z
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getLongZ", "()J", false));
        il.add(randomOffset(4, 12));
        il.add(new VarInsnNode(Opcodes.ILOAD, 12));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LSUB));
        // new BlockPos(JJJ); the caller's original ASTORE 21 consumes it.
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, BLOCKPOS, "<init>", "(JJJ)V", false));
        return il;
    }

    /** {@code rand.nextInt(2*k + 1)} as a long. */
    private static InsnList randomOffset(int randomSlot, int kSlot) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, randomSlot));
        il.add(new VarInsnNode(Opcodes.ILOAD, kSlot));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new InsnNode(Opcodes.IMUL));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, RANDOM, "nextInt", "(I)I", true));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LADD));
        return il;
    }

    private static MethodNode find(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) {
            if (name.equals(m.name) && desc.equals(m.desc)) {
                return m;
            }
        }
        return null;
    }
}
