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
import org.objectweb.asm.tree.VarInsnNode;

/**
 * W4-2 slice 3-F (step 2): wide ticket entry on {@code ServerChunkCache} -
 * exposes {@code TicketStorage.farlands$addTicketWithRadiusW} to the testgen
 * harness (the {@code ticketStorage} field is private, the harness lives outside
 * the MC packages).
 */
public final class ServerChunkCacheWidePatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/server/level/ServerChunkCache";
    private static final String CHUNK_POS = "net/minecraft/world/level/ChunkPos";
    private static final String TICKET_TYPE = "net/minecraft/server/level/TicketType";
    private static final String TICKET_TYPE_D = "Lnet/minecraft/server/level/TicketType;";
    private static final String TICKET_STORAGE = "net/minecraft/world/level/TicketStorage";
    private static final String TICKET_STORAGE_D = "Lnet/minecraft/world/level/TicketStorage;";

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
        if (node.methods.stream().anyMatch(mn -> "farlands$addTicketWithRadiusW".equals(mn.name))) {
            return original;
        }

        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "farlands$addTicketWithRadiusW",
            "(" + TICKET_TYPE_D + "L" + CHUNK_POS + ";I)V", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "ticketStorage", TICKET_STORAGE_D));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TICKET_STORAGE, "farlands$addTicketWithRadiusW",
            "(" + TICKET_TYPE_D + "L" + CHUNK_POS + ";I)V", false));
        il.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(mn);

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }
}
