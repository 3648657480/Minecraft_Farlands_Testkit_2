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
 * Fixes {@code AABB.clip(Iterable, Vec3, Vec3, BlockPos)} for coordinates whose
 * block X/Z does not fit an int.
 *
 * <p>Vanilla moves each candidate box by {@code pos.getX()/getZ()} (int
 * accessors), so at {@code block >= 2^31} (block 2147483648 = chunk 134217728)
 * the box is translated to {@code -2147483648} and the DDA ray-vs-box tests all
 * miss: block picking returns MISS east of the seam.</p>
 *
 * <p>This patch moves the candidates by the true long block coordinate
 * ({@code Vec3i.getLongX/getLongZ}, added by
 * {@link Vec3iWidePatch}), which equals the int accessor everywhere the
 * coordinate fits an int, so normal-world picking is unchanged.</p>
 */
public final class AabbClipPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/phys/AABB";
    private static final String VEC3I = "net/minecraft/core/Vec3i";
    private static final String CLIP_DESC =
        "(Ljava/lang/Iterable;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
            + "Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/BlockHitResult;";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        MethodNode clip = null;
        for (MethodNode m : node.methods) {
            if ("clip".equals(m.name) && CLIP_DESC.equals(m.desc)) {
                clip = m;
                break;
            }
        }
        if (clip == null) {
            throw new IllegalStateException(TARGET + "#clip" + CLIP_DESC + " not found");
        }

        // Idempotency: key on our own injected call (Vec3i.getLongX, added by
        // Vec3iWidePatch). Do NOT key on move(DDD): vanilla clip already
        // contains a move(DDD) call, so that check would falsely mark this
        // class patched (the same class of bug as the old WgrPatch).
        for (AbstractInsnNode n : clip.instructions) {
            if (n instanceof MethodInsnNode min && VEC3I.equals(min.owner)
                && "getLongX".equals(min.name)) {
                return original; // already patched
            }
        }

        // Locate aabb.move(BlockPos) inside the loop.
        MethodInsnNode move = null;
        for (AbstractInsnNode n : clip.instructions) {
            if (n instanceof MethodInsnNode min && min.getOpcode() == Opcodes.INVOKEVIRTUAL
                && TARGET.equals(min.owner) && "move".equals(min.name)
                && "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/AABB;".equals(min.desc)) {
                move = min;
                break;
            }
        }
        if (move == null) {
            throw new IllegalStateException(TARGET + ": move(BlockPos) call not found in clip");
        }
        AbstractInsnNode prev = move.getPrevious();
        AbstractInsnNode prevPrev = prev == null ? null : prev.getPrevious();
        if (!(prev instanceof VarInsnNode posLoad) || posLoad.getOpcode() != Opcodes.ALOAD || posLoad.var != 3
            || !(prevPrev instanceof VarInsnNode aabbLoad)
            || aabbLoad.getOpcode() != Opcodes.ALOAD || aabbLoad.var != 13) {
            throw new IllegalStateException(TARGET + ": unexpected move(BlockPos) call pattern in clip");
        }

        // New locals: ux = 14, uz = 16 (vanilla uses 0..13).
        InsnList head = new InsnList();
        head.add(new VarInsnNode(Opcodes.ALOAD, 3));
        head.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I, "getLongX", "()J", false));
        head.add(new InsnNode(Opcodes.L2D));
        head.add(new VarInsnNode(Opcodes.DSTORE, 14));
        head.add(new VarInsnNode(Opcodes.ALOAD, 3));
        head.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I, "getLongZ", "()J", false));
        head.add(new InsnNode(Opcodes.L2D));
        head.add(new VarInsnNode(Opcodes.DSTORE, 16));
        clip.instructions.insert(head);

        // Replace aabb.move(pos) with aabb.move(ux, (double)pos.getY(), uz).
        InsnList args = new InsnList();
        args.add(new VarInsnNode(Opcodes.DLOAD, 14));
        args.add(new VarInsnNode(Opcodes.ALOAD, 3));
        args.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "getY", "()I", false));
        args.add(new InsnNode(Opcodes.I2D));
        args.add(new VarInsnNode(Opcodes.DLOAD, 16));
        clip.instructions.insertBefore(move, args);
        clip.instructions.remove(posLoad);
        move.desc = "(DDD)Lnet/minecraft/world/phys/AABB;";

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }
}
