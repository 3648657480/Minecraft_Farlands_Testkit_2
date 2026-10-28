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
 * Widens two {@code AABB} bridges to the far domain:
 *
 * <p>1. {@code AABB.clip(Iterable, Vec3, Vec3, BlockPos)}: vanilla moves each
 * candidate box by {@code pos.getX()/getY()/getZ()} (int accessors), so at
 * {@code block >= 2^31} (block 2147483648 = chunk 134217728 for X/Z, section
 * 134217728 for Y) the box is translated to {@code -2147483648} and the DDA
 * ray-vs-box tests all miss: block picking returns MISS beyond the seam. This
 * patch moves the candidates by the true long block coordinate
 * ({@code Vec3i.getLongX/getLongY/getLongZ}, added by
 * {@link Vec3iWidePatch}), which equals the int accessor everywhere the
 * coordinate fits an int, so normal-world picking is unchanged.</p>
 *
 * <p>2. {@code AABB.of(BoundingBox)}: the jigsaw placement overlap test (and
 * the debug structures renderer) convert a structure box to doubles. This patch
 * reads the wide X/Z boundaries added by {@link BoundingBoxPatch}
 * ({@code minXLong()/maxXLong()/minZLong()/maxZLong()}) instead of the
 * int-saturated views, so a piece generated at block 2^31 keeps its real
 * extent. At normal coordinates the long equals the int, so the result is
 * bit-identical.</p>
 */
public final class AabbClipPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/phys/AABB";
    private static final String VEC3I = "net/minecraft/core/Vec3i";
    private static final String BOUNDING_BOX =
        "net/minecraft/world/level/levelgen/structure/BoundingBox";
    private static final String CLIP_DESC =
        "(Ljava/lang/Iterable;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
            + "Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/BlockHitResult;";
    private static final String OF_BOX_DESC =
        "(L" + BOUNDING_BOX + ";)Lnet/minecraft/world/phys/AABB;";

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

        boolean changed = false;
        // Idempotency: key on our own injected call (Vec3i.getLongY, added by
        // this patch on top of Vec3iWidePatch). Do NOT key on move(DDD):
        // vanilla clip already contains a move(DDD) call, so that check would
        // falsely mark this class patched (the same class of bug as the old
        // WgrPatch). An older revision patched X/Z only (getLongX/getLongZ but
        // no getLongY) and already rewrote move(BlockPos), so re-patching it
        // cannot work: throw instead of silently keeping the int Y.
        boolean hasLongX = hasCall(clip, VEC3I, "getLongX");
        if (!hasCall(clip, VEC3I, "getLongY")) {
            if (hasLongX) {
                throw new IllegalStateException(TARGET + ": has getLongX but no getLongY"
                    + " (patched by an older revision); patch a vanilla jar");
            }
            patchClip(clip);
            changed = true;
        }
        if (patchBoundingBoxFactory(node)) {
            changed = true;
        }
        if (!changed) {
            return original;
        }

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static void patchClip(MethodNode clip) {
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

        // New locals: ux = 14, uz = 16, uy = 18 (vanilla uses 0..13).
        InsnList head = new InsnList();
        head.add(new VarInsnNode(Opcodes.ALOAD, 3));
        head.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I, "getLongX", "()J", false));
        head.add(new InsnNode(Opcodes.L2D));
        head.add(new VarInsnNode(Opcodes.DSTORE, 14));
        head.add(new VarInsnNode(Opcodes.ALOAD, 3));
        head.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I, "getLongY", "()J", false));
        head.add(new InsnNode(Opcodes.L2D));
        head.add(new VarInsnNode(Opcodes.DSTORE, 18));
        head.add(new VarInsnNode(Opcodes.ALOAD, 3));
        head.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I, "getLongZ", "()J", false));
        head.add(new InsnNode(Opcodes.L2D));
        head.add(new VarInsnNode(Opcodes.DSTORE, 16));
        clip.instructions.insert(head);

        // Replace aabb.move(pos) with aabb.move(ux, uy, uz).
        InsnList args = new InsnList();
        args.add(new VarInsnNode(Opcodes.DLOAD, 14));
        args.add(new VarInsnNode(Opcodes.DLOAD, 18));
        args.add(new VarInsnNode(Opcodes.DLOAD, 16));
        clip.instructions.insertBefore(move, args);
        clip.instructions.remove(posLoad);
        move.desc = "(DDD)Lnet/minecraft/world/phys/AABB;";
    }

    /**
     * {@code AABB.of(BoundingBox)}: convert the structure box through the wide
     * X/Z accessors added by {@link BoundingBoxPatch}, so a box at block 2^31
     * becomes the true double extent instead of the int-saturated view.
     * Consumers are the jigsaw placement overlap test and the debug renderer;
     * at normal coordinates the wide value equals the int one.
     */
    private static boolean patchBoundingBoxFactory(ClassNode node) {
        if (!FarLandsPatcher.flag("farlands.wide", true)) {
            return false; // BoundingBox has no *Long() accessors in this mode
        }
        MethodNode of = null;
        for (MethodNode m : node.methods) {
            if ("of".equals(m.name) && OF_BOX_DESC.equals(m.desc)) {
                of = m;
                break;
            }
        }
        if (of == null) {
            throw new IllegalStateException(TARGET + "#of" + OF_BOX_DESC + " not found");
        }
        if (hasCall(of, BOUNDING_BOX, "minXLong")) {
            return false; // already patched
        }
        InsnList il = new InsnList();
        il.add(new TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        boxAxis(il, "minX", false);
        boxAxisInt(il, "minY");
        boxAxis(il, "minZ", false);
        boxAxis(il, "maxX", true);
        boxAxisIntPlusOne(il, "maxY");
        boxAxis(il, "maxZ", true);
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>",
            "(DDDDDD)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        replace(of, il);
        return true;
    }

    /** Push the wide X/Z bound as a double, optionally plus one block. */
    private static void boxAxis(InsnList il, String getter, boolean plusOne) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BOUNDING_BOX, getter + "Long", "()J", false));
        if (plusOne) {
            il.add(new InsnNode(Opcodes.LCONST_1));
            il.add(new InsnNode(Opcodes.LADD));
        }
        il.add(new InsnNode(Opcodes.L2D));
    }

    private static void boxAxisInt(InsnList il, String getter) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BOUNDING_BOX, getter, "()I", false));
        il.add(new InsnNode(Opcodes.I2D));
    }

    private static void boxAxisIntPlusOne(InsnList il, String getter) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BOUNDING_BOX, getter, "()I", false));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new InsnNode(Opcodes.I2D));
    }

    private static boolean hasCall(MethodNode m, String owner, String name) {
        for (AbstractInsnNode n : m.instructions) {
            if (n instanceof MethodInsnNode mi && owner.equals(mi.owner) && name.equals(mi.name)) {
                return true;
            }
        }
        return false;
    }

    private static void replace(MethodNode m, InsnList body) {
        m.instructions.clear();
        m.localVariables = null;
        m.tryCatchBlocks.clear();
        m.instructions.add(body);
    }
}
