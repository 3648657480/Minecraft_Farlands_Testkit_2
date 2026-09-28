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
import org.objectweb.asm.tree.VarInsnNode;

/**
 * W5-C: keeps the wide X/Z in {@code LevelReader.getHeightmapPos}.
 *
 * <p>Vanilla reads {@code pos.getX()/getZ()} (int view) and rebuilds the
 * result with the int {@code BlockPos} constructor, so a wide position past
 * block 2^31 loses its real X/Z before the caller (tree decorators, structure
 * pieces) uses it. The height itself is already wide-safe because the
 * {@code getHeight(int,int)} implementation normalises the int view
 * ({@link WorldGenRegionHeightPatch}); the result is rebuilt with
 * {@code pos.atY(y)} so the wide X/Z survive. At normal coordinates
 * {@code atY(y)} equals {@code new BlockPos(x, y, z)}, so behavior is
 * bit-identical.</p>
 */
public final class LevelReaderHeightmapPosPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/LevelReader";
    private static final String BLOCKPOS = "net/minecraft/core/BlockPos";
    private static final String VEC3I = "net/minecraft/core/Vec3i";
    private static final String HTYPES = "net/minecraft/world/level/levelgen/Heightmap$Types";
    private static final String DESC = "(L" + HTYPES + ";L" + BLOCKPOS + ";)L" + BLOCKPOS + ";";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        MethodNode m = find(node, "getHeightmapPos", DESC);
        if (m == null) {
            throw new IllegalStateException(TARGET + "#getHeightmapPos" + DESC + " not found");
        }
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof MethodInsnNode mi && VEC3I.equals(mi.owner)
                && "getLongX".equals(mi.name)) {
                return original; // already patched
            }
        }

        m.instructions.clear();
        m.localVariables = null;
        m.tryCatchBlocks.clear();
        m.instructions.add(body());

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    /**
     * {@code return pos.atY(this.getHeight(type, (int) pos.getLongX(),
     * (int) pos.getLongZ()));}
     */
    private static InsnList body() {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I, "getLongX", "()J", false));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I, "getLongZ", "()J", false));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, TARGET, "getHeight",
            "(L" + HTYPES + ";II)I", true));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "atY",
            "(I)L" + BLOCKPOS + ";", false));
        il.add(new InsnNode(Opcodes.ARETURN));
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
