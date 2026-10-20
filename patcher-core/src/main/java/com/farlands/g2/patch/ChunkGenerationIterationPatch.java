package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.util.ArrayList;
import java.util.List;

/** Keep generation-loop counters local; convert only the cache lookup back to its int view. */
public final class ChunkGenerationIterationPatch implements ClassPatch {
    private static final String TARGET = "net/minecraft/server/level/ChunkGenerationTask";
    private static final String POS = "net/minecraft/world/level/ChunkPos";
    private static final String CACHE = "net/minecraft/util/StaticCache2D";
    @Override public boolean matches(String name) { return TARGET.equals(name); }

    @Override public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        int methods = 0;
        for (MethodNode m : node.methods) {
            if (m.name.equals("scheduleLayer") || m.name.equals("canLoadWithoutGeneration")) {
                patchLoops(m);
                methods++;
            }
        }
        if (methods != 2) throw new IllegalStateException("Missing generation traversal methods");
        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static void patchLoops(MethodNode m) {
        List<MethodInsnNode> bounds = new ArrayList<>();
        int xSlot = -1, zSlot = -1;
        for (AbstractInsnNode n : m.instructions) {
            if (!(n instanceof MethodInsnNode mi) || !mi.owner.equals(POS)
                || !mi.desc.equals("()I") || !(mi.name.equals("x") || mi.name.equals("z"))) continue;
            AbstractInsnNode radius = next(mi), operation = next(radius);
            if (!(radius instanceof VarInsnNode v) || v.getOpcode() != Opcodes.ILOAD || operation == null
                || !(operation.getOpcode() == Opcodes.IADD || operation.getOpcode() == Opcodes.ISUB)) continue;
            if (operation.getOpcode() == Opcodes.ISUB) {
                AbstractInsnNode store = next(operation);
                if (!(store instanceof VarInsnNode s) || s.getOpcode() != Opcodes.ISTORE)
                    throw new IllegalStateException("Unexpected generation loop start in " + m.name);
                if (mi.name.equals("x")) xSlot = s.var; else zSlot = s.var;
            }
            bounds.add(mi);
        }
        if (bounds.size() != 4 || xSlot < 0 || zSlot < 0)
            throw new IllegalStateException("Expected four bounds in " + m.name + ", got " + bounds.size());
        for (MethodInsnNode mi : bounds) {
            AbstractInsnNode field = prev(mi), load = prev(field);
            if (!(field instanceof FieldInsnNode f) || !f.name.equals("pos")
                || !(load instanceof VarInsnNode v) || v.getOpcode() != Opcodes.ALOAD || v.var != 0)
                throw new IllegalStateException("Unexpected position receiver in " + m.name);
            m.instructions.insertBefore(load, new InsnNode(Opcodes.ICONST_0));
            m.instructions.remove(load); m.instructions.remove(field); m.instructions.remove(mi);
        }
        int lookups = 0, distances = 0;
        for (AbstractInsnNode n : m.instructions.toArray()) {
            if (!(n instanceof MethodInsnNode call)) continue;
            AbstractInsnNode z = prev(call), x = prev(z);
            if (!(z instanceof VarInsnNode zv) || zv.getOpcode() != Opcodes.ILOAD || zv.var != zSlot
                || !(x instanceof VarInsnNode xv) || xv.getOpcode() != Opcodes.ILOAD || xv.var != xSlot) continue;
            if (call.owner.equals(CACHE) && call.name.equals("get")) {
                m.instructions.insertBefore(x, coordinate("x"));
                m.instructions.insert(x, new InsnNode(Opcodes.IADD));
                m.instructions.insertBefore(z, coordinate("z"));
                m.instructions.insert(z, new InsnNode(Opcodes.IADD));
                lookups++;
            } else if (call.owner.equals(POS) && call.name.equals("getChessboardDistance")) {
                AbstractInsnNode field = prev(x), load = prev(field);
                if (!(field instanceof FieldInsnNode f) || !f.name.equals("pos")
                    || !(load instanceof VarInsnNode v) || v.var != 0)
                    throw new IllegalStateException("Unexpected distance receiver");
                m.instructions.remove(load); m.instructions.remove(field);
                m.instructions.insert(x, new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "abs", "(I)I", false));
                m.instructions.insert(z, new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "abs", "(I)I", false));
                m.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(II)I", false));
                distances++;
            }
        }
        if (lookups != 1 || (m.name.equals("canLoadWithoutGeneration") && distances != 1))
            throw new IllegalStateException("Incomplete generation traversal rewrite in " + m.name);
    }

    private static InsnList coordinate(String axis) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "pos", "L" + POS + ";"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, POS, axis, "()I", false));
        return il;
    }
    private static AbstractInsnNode next(AbstractInsnNode n) {
        if (n == null) return null;
        do { n = n.getNext(); } while (n != null && n.getOpcode() < 0);
        return n;
    }
    private static AbstractInsnNode prev(AbstractInsnNode n) {
        if (n == null) return null;
        do { n = n.getPrevious(); } while (n != null && n.getOpcode() < 0);
        return n;
    }
}
