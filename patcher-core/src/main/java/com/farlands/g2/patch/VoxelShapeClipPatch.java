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

/**
 * Fixes block picking for positions whose block X/Y/Z does not fit an int.
 *
 * <p>{@code VoxelShape.clip(Vec3, Vec3, BlockPos)} converts the ray into block
 * local space with {@code pos.getX()/getY()/getZ()} (int accessors). At
 * {@code block >= 2^31} (block 2147483648 = chunk 134217728 / section
 * 134217728 for Y) the int view wraps to {@code -2147483648}, the local
 * coordinate becomes ~4.3e9, the {@code isFullWide} fast path and the
 * {@code AABB.clip} fallback both lose the box, and the ray passes through the
 * block: mining/placing beyond the seam picks nothing.</p>
 *
 * <p>This patch subtracts the true long block coordinate
 * ({@code Vec3i.getLongX/getLongY/getLongZ}, added by {@link Vec3iWidePatch}).
 * The long value equals the int accessor wherever the coordinate fits an int,
 * so normal-world picking is bit-identical.</p>
 */
public final class VoxelShapeClipPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/phys/shapes/VoxelShape";
    private static final String VEC3I = "net/minecraft/core/Vec3i";
    private static final String BLOCK_POS = "net/minecraft/core/BlockPos";

    private static final String CLIP_DESC =
        "(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
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

        // Idempotency: key on our own injected call, never on a shape that
        // vanilla could already contain. An older revision patched X/Z only
        // (getLongX/getLongZ but no getLongY): the int Y read is still there,
        // so throw instead of silently leaving Y broken.
        boolean hasLongX = false;
        boolean hasLongY = false;
        for (AbstractInsnNode n : clip.instructions) {
            if (n instanceof MethodInsnNode min) {
                if ("getLongX".equals(min.name)) {
                    hasLongX = true;
                } else if ("getLongY".equals(min.name)) {
                    hasLongY = true;
                }
            }
        }
        if (hasLongY) {
            return original; // already patched
        }
        if (hasLongX) {
            throw new IllegalStateException(TARGET + ": has getLongX but no getLongY"
                + " (patched by an older revision); patch a vanilla jar");
        }

        // Replace getX()I + I2D with getLongX()J + L2D, same for Y and Z.
        int replaced = 0;
        for (AbstractInsnNode n : clip.instructions.toArray()) {
            if (!(n instanceof MethodInsnNode min) || min.getOpcode() != Opcodes.INVOKEVIRTUAL
                || !BLOCK_POS.equals(min.owner) || !"()I".equals(min.desc)) {
                continue;
            }
            String axis;
            if ("getX".equals(min.name)) {
                axis = "X";
            } else if ("getY".equals(min.name)) {
                axis = "Y";
            } else if ("getZ".equals(min.name)) {
                axis = "Z";
            } else {
                continue;
            }
            AbstractInsnNode next = min.getNext();
            if (next == null || next.getOpcode() != Opcodes.I2D) {
                continue;
            }
            InsnList repl = new InsnList();
            repl.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I,
                "getLong" + axis, "()J", false));
            repl.add(new InsnNode(Opcodes.L2D));
            clip.instructions.insert(min, repl);
            clip.instructions.remove(next);
            clip.instructions.remove(min);
            replaced++;
        }
        if (replaced != 3) {
            throw new IllegalStateException(TARGET + ": expected 3 int block-coordinate reads in clip,"
                + " found " + replaced);
        }

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }
}
