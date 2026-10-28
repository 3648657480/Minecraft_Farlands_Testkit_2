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
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Sound positions on the wire.
 *
 * <p>{@code ClientboundSoundPacket} stores x/y/z as ints scaled by 8 (1/8 block
 * fixed point), so any sound at or beyond block 2^31 is truncated and played at
 * the mirrored position (inaudible, wrong distance). Store the exact doubles
 * instead: both sides run this patch, so the wire format stays self-consistent
 * and the vanilla distance attenuation is preserved.</p>
 */
public final class ClientboundSoundPacketPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/network/protocol/game/ClientboundSoundPacket";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        FieldNode fx = null;
        for (FieldNode f : node.fields) {
            if (("x".equals(f.name) || "y".equals(f.name) || "z".equals(f.name)) && "I".equals(f.desc)) {
                f.desc = "D";
                fx = f;
            }
        }
        if (fx == null) {
            return original; // already double
        }
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof FieldInsnNode fi && TARGET.equals(fi.owner)
                    && ("x".equals(fi.name) || "y".equals(fi.name) || "z".equals(fi.name))) {
                    fi.desc = "D";
                }
            }
        }
        for (MethodNode m : node.methods) {
            if ("<init>".equals(m.name) && m.desc.contains("DDDFFJ")) {
                fixDoubleCtor(m);
            } else if ("<init>".equals(m.name) && m.desc.contains("RegistryFriendlyByteBuf")) {
                fixReadCtor(m);
            } else if ("write".equals(m.name)) {
                fixWrite(m);
            } else if (("getX".equals(m.name) || "getY".equals(m.name) || "getZ".equals(m.name))
                && "()D".equals(m.desc)) {
                fixGetter(m);
            }
        }
        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static void fixDoubleCtor(MethodNode m) {
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof FieldInsnNode fi && fi.getOpcode() == Opcodes.PUTFIELD
                && ("x".equals(fi.name) || "y".equals(fi.name) || "z".equals(fi.name))) {
                AbstractInsnNode d2i = fi.getPrevious();
                AbstractInsnNode dmul = d2i == null ? null : d2i.getPrevious();
                AbstractInsnNode ldc = dmul == null ? null : dmul.getPrevious();
                if (d2i != null && d2i.getOpcode() == Opcodes.D2I
                    && dmul != null && dmul.getOpcode() == Opcodes.DMUL
                    && ldc instanceof LdcInsnNode l && Double.valueOf(8.0).equals(l.cst)) {
                    m.instructions.remove(d2i);
                    m.instructions.remove(dmul);
                    m.instructions.remove(ldc);
                }
            }
        }
    }

    private static void fixReadCtor(MethodNode m) {
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof FieldInsnNode fi && fi.getOpcode() == Opcodes.PUTFIELD
                && ("x".equals(fi.name) || "y".equals(fi.name) || "z".equals(fi.name))) {
                AbstractInsnNode prev = fi.getPrevious();
                if (prev instanceof MethodInsnNode mi && "readInt".equals(mi.name) && "()I".equals(mi.desc)) {
                    mi.name = "readDouble";
                    mi.desc = "()D";
                }
            }
        }
    }

    private static void fixWrite(MethodNode m) {
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof MethodInsnNode mi && "writeInt".equals(mi.name) && "(I)Lnet/minecraft/network/FriendlyByteBuf;".equals(mi.desc)) {
                AbstractInsnNode prev = mi.getPrevious();
                if (prev instanceof FieldInsnNode fi && ("x".equals(fi.name) || "y".equals(fi.name) || "z".equals(fi.name))) {
                    mi.name = "writeDouble";
                    mi.desc = "(D)Lnet/minecraft/network/FriendlyByteBuf;";
                }
            }
        }
    }

    private static void fixGetter(MethodNode m) {
        m.instructions.clear();
        m.localVariables = null;
        m.tryCatchBlocks.clear();
        InsnList il = m.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, m.name.substring(3).toLowerCase(java.util.Locale.ROOT), "D"));
        il.add(new InsnNode(Opcodes.DRETURN));
    }
}