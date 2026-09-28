package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

/**
 * Sanitizes {@code BoundingBox} outputs at extreme coordinates.
 *
 * <p>Structure boxes near +/-2^31 hold wrapped values whose spans, centers
 * and accessors overflow 32-bit arithmetic and poison structure placement.
 * This patch keeps the int storage but recomputes every value that leaves
 * the class in the long domain and clamps it, mirroring the behavior of the
 * working fork build:</p>
 * <ul>
 *   <li>{@code minX/minZ/maxX/maxZ()} clamp to +/-2117483647 (2^31 - 30M)</li>
 *   <li>{@code getXSpan/getYSpan/getZSpan()} compute in long, clamp to [1, 256]</li>
 *   <li>{@code getCenter()} computes in long, clamps x/z to +/-2147467263
 *       (2^31 - 16384) and y to [-64, 320]</li>
 *   <li>{@code getLength()} computes in long</li>
 * </ul>
 */
public final class BoundingBoxPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/levelgen/structure/BoundingBox";
    private static final long CLAMP_XZ = 2_147_483_647L - 30_000_000L; // 2117483647
    private static final long CLAMP_CENTER = 2_147_483_647L - 16_384L; // 2147467263

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        MethodNode minX = find(node, "minX", "()I");
        if (minX != null && containsClampBound(minX)) {
            return original; // already patched (our unique clamp lower bound is present)
        }

        rewrite(node, "minX", "()I", clampGetter("minX"));
        rewrite(node, "minZ", "()I", clampGetter("minZ"));
        rewrite(node, "maxX", "()I", clampGetter("maxX"));
        rewrite(node, "maxZ", "()I", clampGetter("maxZ"));
        rewrite(node, "getXSpan", "()I", span("maxX", "minX"));
        rewrite(node, "getYSpan", "()I", span("maxY", "minY"));
        rewrite(node, "getZSpan", "()I", span("maxZ", "minZ"));
        rewrite(node, "getCenter", "()Lnet/minecraft/core/BlockPos;", center());
        rewrite(node, "getLength", "()Lnet/minecraft/core/Vec3i;", length());

        // fromCorners builds a box from two Vec3i corners. At 2^31 the int
        // getX/getZ wrap and place the two (in truth adjacent) corners on
        // opposite sides of zero, producing a box spanning ~4.3 billion blocks
        // (e.g. UnderwaterMagmaFeature iterates it and spins). Compute the
        // extremes in the wide domain and clamp into the same int-safe range
        // the accessors already use; at normal coordinates getLongX == getX so
        // the result is identical. Only meaningful when Vec3i is widened.
        if (FarLandsPatcher.flag("farlands.wide", true)) {
            rewrite(node, "fromCorners",
                "(Lnet/minecraft/core/Vec3i;Lnet/minecraft/core/Vec3i;)L" + TARGET + ";",
                fromCornersWide());
        }

        // Direct (IIIIII) constructions (encapsulate / encapsulating /
        // orientBox / features computing corners with int getX()/getZ())
        // can receive one endpoint that wrapped across the 2^31 seam while
        // the other did not: min > max. Normalize such a wrap-crossing pair
        // back into the int-safe XZ range the accessors already use, so the
        // box never logs "inverted bounds" and never spans ~2^32 blocks.
        addWrapHelpers(node);
        patchConstructor(node);

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static void rewrite(ClassNode node, String name, String desc, InsnList body) {
        MethodNode m = find(node, name, desc);
        if (m == null) {
            throw new IllegalStateException(TARGET + "#" + name + desc + " not found");
        }
        m.instructions.clear();
        m.localVariables = null;
        m.tryCatchBlocks.clear();
        m.instructions.add(body);
    }

    private static MethodNode find(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) {
            if (name.equals(m.name) && desc.equals(m.desc)) {
                return m;
            }
        }
        return null;
    }

    /** Idempotency marker: our unique clamp lower bound, not a generic Math.clamp. */
    private static boolean containsClampBound(MethodNode m) {
        for (AbstractInsnNode n : m.instructions) {
            if (n instanceof LdcInsnNode ldc && ldc.cst instanceof Long v && v == -2_117_483_648L) {
                return true;
            }
        }
        return false;
    }

    private static InsnList clampGetter(String field) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, field, "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new LdcInsnNode(-2_117_483_648));
        il.add(new LdcInsnNode(2_117_483_647));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "clamp", "(JII)I", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    private static InsnList span(String maxField, String minField) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, maxField, "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, minField, "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new IntInsnNode(Opcodes.SIPUSH, 256));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "clamp", "(JII)I", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    private static InsnList center() {
        InsnList il = new InsnList();
        il.add(new TypeInsnNode(Opcodes.NEW, "net/minecraft/core/BlockPos"));
        il.add(new InsnNode(Opcodes.DUP));
        centerAxis(il, "minX", "maxX");
        centerAxisY(il);
        centerAxis(il, "minZ", "maxZ");
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "net/minecraft/core/BlockPos", "<init>", "(III)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static void centerAxis(InsnList il, String minField, String maxField) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, minField, "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, maxField, "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, minField, "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new LdcInsnNode(2L));
        il.add(new InsnNode(Opcodes.LDIV));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new LdcInsnNode(-CLAMP_CENTER - 1));
        il.add(new LdcInsnNode(CLAMP_CENTER));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "clamp", "(JJJ)J", false));
        il.add(new InsnNode(Opcodes.L2I));
    }

    private static void centerAxisY(InsnList il) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "minY", "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "maxY", "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "minY", "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new LdcInsnNode(2L));
        il.add(new InsnNode(Opcodes.LDIV));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new IntInsnNode(Opcodes.BIPUSH, -64));
        il.add(new IntInsnNode(Opcodes.SIPUSH, 320));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "clamp", "(JII)I", false));
    }

    /** Wide-safe {@code fromCorners}: min/max in long, clamped to the int-safe XZ range. */
    private static InsnList fromCornersWide() {
        InsnList il = new InsnList();
        il.add(new TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        cornerAxis(il, "X", true);   // minX
        cornerY(il, true);                 // minY
        cornerAxis(il, "Z", true);   // minZ
        cornerAxis(il, "X", false);  // maxX
        cornerY(il, false);                // maxY
        cornerAxis(il, "Z", false);  // maxZ
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(IIIIII)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    /** X/Z are read via {@code getLongX/Z} and clamped into the int-safe range. */
    private static void cornerAxis(InsnList il, String axis, boolean min) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLong" + axis, "()J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLong" + axis, "()J", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", min ? "min" : "max", "(JJ)J", false));
        il.add(new LdcInsnNode(-2_117_483_648L));
        il.add(new LdcInsnNode(2_117_483_647L));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "clamp", "(JJJ)J", false));
        il.add(new InsnNode(Opcodes.L2I));
    }

    private static void cornerY(InsnList il, boolean min) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getY", "()I", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getY", "()I", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", min ? "min" : "max", "(II)I", false));
    }

    private static InsnList length() {
        InsnList il = new InsnList();
        il.add(new TypeInsnNode(Opcodes.NEW, "net/minecraft/core/Vec3i"));
        il.add(new InsnNode(Opcodes.DUP));
        lengthAxis(il, "maxX", "minX");
        lengthAxis(il, "maxY", "minY");
        lengthAxis(il, "maxZ", "minZ");
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "net/minecraft/core/Vec3i", "<init>", "(III)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static void lengthAxis(InsnList il, String maxField, String minField) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, maxField, "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, minField, "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new InsnNode(Opcodes.L2I));
    }

    // ---- wrap-aware constructor normalization ----------------------------

    /**
     * Adds the two private static helpers used by the constructor preamble.
     * Each takes the raw (min,max) pair and, only when the pair is inverted
     * but the unsigned order is sane (i.e. it crossed the 2^31 seam), returns
     * the clamped endpoint; otherwise it returns the raw value unchanged.
     */
    private static void addWrapHelpers(ClassNode node) {
        if (node.methods.stream().anyMatch(m -> "farlands$wrapMin".equals(m.name))) {
            return;
        }
        node.methods.add(wrapHelper("farlands$wrapMin", true));
        node.methods.add(wrapHelper("farlands$wrapMax", false));
    }

    private static MethodNode wrapHelper(String name, boolean min) {
        MethodNode m = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, name, "(II)I", null, null);
        InsnList il = m.instructions;
        org.objectweb.asm.tree.LabelNode ret = new org.objectweb.asm.tree.LabelNode();
        // if (max >= min) return raw;
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new VarInsnNode(Opcodes.ILOAD, 0));
        il.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IF_ICMPGE, ret));
        // long umin = min & 0xFFFFFFFFL;
        il.add(new VarInsnNode(Opcodes.ILOAD, 0));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new LdcInsnNode(0xFFFFFFFFL));
        il.add(new InsnNode(Opcodes.LAND));
        il.add(new VarInsnNode(Opcodes.LSTORE, 2));
        // long umax = max & 0xFFFFFFFFL;
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new LdcInsnNode(0xFFFFFFFFL));
        il.add(new InsnNode(Opcodes.LAND));
        il.add(new VarInsnNode(Opcodes.LSTORE, 4));
        // if (umax < umin) return raw;  (genuine bug, leave vanilla to handle)
        il.add(new VarInsnNode(Opcodes.LLOAD, 4));
        il.add(new VarInsnNode(Opcodes.LLOAD, 2));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFLT, ret));
        // if (umax - umin > 1e9) return raw;  (not a short seam-crossing span)
        il.add(new VarInsnNode(Opcodes.LLOAD, 4));
        il.add(new VarInsnNode(Opcodes.LLOAD, 2));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new LdcInsnNode(1_000_000_000L));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFGT, ret));
        // return (int) Math.clamp(umax | umin, -CLAMP_XZ-1, CLAMP_XZ);
        il.add(new VarInsnNode(Opcodes.LLOAD, min ? 2 : 4));
        il.add(new LdcInsnNode(-CLAMP_XZ - 1));
        il.add(new LdcInsnNode(CLAMP_XZ));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "clamp", "(JJJ)J", false));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(ret);
        // return raw;
        il.add(new VarInsnNode(Opcodes.ILOAD, min ? 0 : 1));
        il.add(new InsnNode(Opcodes.IRETURN));
        return m;
    }

    /** Rewrites the X and Z argument pairs of the {@code (IIIIII)} constructor. */
    private static void patchConstructor(ClassNode node) {
        MethodNode ctor = find(node, "<init>", "(IIIIII)V");
        if (ctor == null) {
            throw new IllegalStateException(TARGET + "#<init>(IIIIII)V not found");
        }
        InsnList il = new InsnList();
        axisNorm(il, 1, 4); // X
        axisNorm(il, 3, 6); // Z
        AbstractInsnNode first = ctor.instructions.getFirst();
        ctor.instructions.insertBefore(first, il);
    }

    private static void axisNorm(InsnList il, int minSlot, int maxSlot) {
        // preserve the raw pair across the two helper calls
        il.add(new VarInsnNode(Opcodes.ILOAD, minSlot));
        il.add(new VarInsnNode(Opcodes.ISTORE, 7));
        il.add(new VarInsnNode(Opcodes.ILOAD, maxSlot));
        il.add(new VarInsnNode(Opcodes.ISTORE, 8));
        il.add(new VarInsnNode(Opcodes.ILOAD, 7));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "farlands$wrapMin", "(II)I", false));
        il.add(new VarInsnNode(Opcodes.ISTORE, minSlot));
        il.add(new VarInsnNode(Opcodes.ILOAD, 7));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "farlands$wrapMax", "(II)I", false));
        il.add(new VarInsnNode(Opcodes.ISTORE, maxSlot));
    }
}

