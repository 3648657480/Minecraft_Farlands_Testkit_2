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
 * W5 consumer widening for two non-{@code getX/getZ} constructor shapes:
 *
 * <ul>
 *   <li>{@code Structure.onTopOfChunkCenter}: {@code getMiddleBlockX/Z()} (int)
 *       rebuilt the stub position from the truncated chunk centre. Uses
 *       {@code ChunkPos.getMiddleBlockXLong/ZLong()}.</li>
 *   <li>{@code CountOnEveryLayerPlacement.getPositions}: {@code origin.getX/Z()}
 *       plus a 0..15 offset. Uses {@code WidePos.fromOrigin} to recover the wide
 *       origin while keeping the int offset.</li>
 * </ul>
 *
 * <p>At normal coordinates both helpers are value-identical to the int
 * constructor they replace.</p>
 */
public final class WidePosLocalPatch implements ClassPatch {

    private static final String STRUCTURE = "net/minecraft/world/level/levelgen/structure/Structure";
    private static final String COUNT = "net/minecraft/world/level/levelgen/placement/CountOnEveryLayerPlacement";
    private static final String BLOCKPOS = "net/minecraft/core/BlockPos";
    private static final String CHUNKPOS = "net/minecraft/world/level/ChunkPos";
    private static final String WIDEPOS = "com/farlands/g2/util/WidePos";

    @Override
    public boolean matches(String internalName) {
        return STRUCTURE.equals(internalName) || COUNT.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        boolean changed = false;
        for (MethodNode m : node.methods) {
            if (STRUCTURE.equals(node.name) && "onTopOfChunkCenter".equals(m.name)) {
                int chunkSlot = findChunkReceiver(m);
                if (chunkSlot >= 0) {
                    changed |= rewrite(m, chunkSlot, true);
                }
            } else if (COUNT.equals(node.name) && "getPositions".equals(m.name)
                && m.desc.startsWith("(Lnet/minecraft/world/level/levelgen/placement/PlacementContext;")) {
                int originSlot = (m.access & Opcodes.ACC_STATIC) != 0 ? 2 : 3;
                changed |= rewrite(m, originSlot, false);
            }
        }
        if (!changed) {
            return original;
        }
        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    /** Slot holding the {@code ChunkPos} used for {@code getMiddleBlockX()}. */
    private static int findChunkReceiver(MethodNode m) {
        for (AbstractInsnNode p = m.instructions.getFirst(); p != null; p = p.getNext()) {
            if (p instanceof MethodInsnNode mi
                && CHUNKPOS.equals(mi.owner)
                && "getMiddleBlockX".equals(mi.name)
                && "()I".equals(mi.desc)) {
                AbstractInsnNode recv = prevReal(mi);
                if (recv instanceof VarInsnNode vi && vi.getOpcode() == Opcodes.ALOAD) {
                    return vi.var;
                }
            }
        }
        return -1;
    }

    private static boolean rewrite(MethodNode m, int slot, boolean chunkMode) {
        for (AbstractInsnNode p = m.instructions.getFirst(); p != null; p = p.getNext()) {
            if (!(p instanceof MethodInsnNode ctor)
                || ctor.getOpcode() != Opcodes.INVOKESPECIAL
                || !BLOCKPOS.equals(ctor.owner)
                || !"<init>".equals(ctor.name)
                || !"(III)V".equals(ctor.desc)) {
                continue;
            }
            AbstractInsnNode c = prevReal(ctor);
            AbstractInsnNode b = prevReal(c);
            AbstractInsnNode a = prevReal(b);
            AbstractInsnNode dup = prevReal(a);
            AbstractInsnNode nw = prevReal(dup);
            if (!(c instanceof VarInsnNode vc && vc.getOpcode() == Opcodes.ILOAD)
                || !(b instanceof VarInsnNode vb && vb.getOpcode() == Opcodes.ILOAD)
                || !(a instanceof VarInsnNode va && va.getOpcode() == Opcodes.ILOAD)
                || !(dup instanceof InsnNode di && di.getOpcode() == Opcodes.DUP)
                || !(nw instanceof TypeInsnNode tn && tn.getOpcode() == Opcodes.NEW && BLOCKPOS.equals(tn.desc))) {
                continue;
            }

            InsnList il = new InsnList();
            if (chunkMode) {
                il.add(new VarInsnNode(Opcodes.ALOAD, slot));
                il.add(new VarInsnNode(Opcodes.ILOAD, vb.var));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, WIDEPOS, "chunkMiddle",
                    "(L" + CHUNKPOS + ";I)L" + BLOCKPOS + ";", false));
            } else {
                il.add(new VarInsnNode(Opcodes.ALOAD, slot));
                il.add(new VarInsnNode(Opcodes.ILOAD, va.var));
                il.add(new VarInsnNode(Opcodes.ILOAD, vb.var));
                il.add(new VarInsnNode(Opcodes.ILOAD, vc.var));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, WIDEPOS, "fromOrigin",
                    "(L" + BLOCKPOS + ";III)L" + BLOCKPOS + ";", false));
            }
            m.instructions.insertBefore(nw, il);

            AbstractInsnNode q = nw;
            while (q != null) {
                AbstractInsnNode next = q.getNext();
                m.instructions.remove(q);
                if (q == ctor) {
                    break;
                }
                q = next;
            }
            return true;
        }
        return false;
    }

    private static AbstractInsnNode prevReal(AbstractInsnNode n) {
        if (n == null) {
            return null;
        }
        AbstractInsnNode p = n.getPrevious();
        while (p != null && p.getOpcode() < 0) {
            p = p.getPrevious();
        }
        return p;
    }
}
