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
 * Block positions on the wire.
 *
 * <p>Vanilla {@code FriendlyByteBuf.writeBlockPos} packs the position into one
 * long with 26-bit X/Z, so any block at or beyond 2^31 is truncated. Block
 * updates, block events and every other BlockPos-carrying packet then land on
 * the wrapped (negative) side: the client never re-renders the edited chunk and
 * no particles/sounds appear. Both sides run this patch, so the wire format is
 * self-consistent; it encodes X (long), Y (int) and Z (long).</p>
 */
public final class FriendlyByteBufBlockPosPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/network/FriendlyByteBuf";
    private static final String BYTE_BUF = "io/netty/buffer/ByteBuf";
    private static final String BLOCK_POS = "net/minecraft/core/BlockPos";
    private static final String READ_DESC = "(Lio/netty/buffer/ByteBuf;)Lnet/minecraft/core/BlockPos;";
    private static final String WRITE_DESC = "(Lio/netty/buffer/ByteBuf;Lnet/minecraft/core/BlockPos;)V";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        MethodNode read = null;
        MethodNode write = null;
        for (MethodNode m : node.methods) {
            if ("readBlockPos".equals(m.name) && READ_DESC.equals(m.desc)) {
                read = m;
            } else if ("writeBlockPos".equals(m.name) && WRITE_DESC.equals(m.desc)) {
                write = m;
            }
        }
        if (read == null || write == null) {
            throw new IllegalStateException(TARGET + ": block pos codec not found");
        }
        if (alreadyPatched(read)) {
            return original;
        }
        read.instructions.clear();
        read.localVariables = null;
        read.tryCatchBlocks.clear();
        read.instructions.add(readBody());
        write.instructions.clear();
        write.localVariables = null;
        write.tryCatchBlocks.clear();
        write.instructions.add(writeBody());
        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static boolean alreadyPatched(MethodNode read) {
        for (AbstractInsnNode n : read.instructions) {
            if (n instanceof MethodInsnNode min && BLOCK_POS.equals(min.owner)
                && "<init>".equals(min.name) && "(JJJ)V".equals(min.desc)) {
                return true;
            }
        }
        return false;
    }

    private static InsnList readBody() {
        InsnList il = new InsnList();
        il.add(new TypeInsnNode(Opcodes.NEW, BLOCK_POS));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BYTE_BUF, "readLong", "()J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BYTE_BUF, "readInt", "()I", false));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BYTE_BUF, "readLong", "()J", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, BLOCK_POS, "<init>", "(JJJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static InsnList writeBody() {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCK_POS, "getLongX", "()J", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BYTE_BUF, "writeLong", "(J)Lio/netty/buffer/ByteBuf;", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCK_POS, "getY", "()I", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BYTE_BUF, "writeInt", "(I)Lio/netty/buffer/ByteBuf;", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCK_POS, "getLongZ", "()J", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BYTE_BUF, "writeLong", "(J)Lio/netty/buffer/ByteBuf;", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new InsnNode(Opcodes.RETURN));
        return il;
    }
}