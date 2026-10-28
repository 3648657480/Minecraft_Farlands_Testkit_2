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
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Widens the X/Z boundaries of {@code BoundingBox} from {@code int} to
 * {@code long} so structure/jigsaw boxes keep their real coordinates across
 * block 2^31.
 *
 * <p>Root cause of the far-domain truncation: {@code BoundingBox.move(Vec3i)}
 * read {@code Vec3i.getX()/getZ()} (int views, wrapped at 2^31) and the
 * constructors stored the result in int fields. A jigsaw piece generated at
 * block 2^31 therefore had its box on the mirror side of the seam.</p>
 *
 * <p>Design:</p>
 * <ul>
 *   <li>storage: {@code minX/minZ/maxX/maxZ} become {@code long};
 *       {@code minY/maxY} stay {@code int};</li>
 *   <li>the vanilla {@code (IIIIII)V} constructor keeps working (int args are
 *       widened; the existing seam-crossing normalization runs first);</li>
 *   <li>a wide {@code (JJJJJJ)V} constructor and long accessors
 *       {@code minXLong()/maxXLong()/minZLong()/maxZLong()} are added;</li>
 *   <li>{@code move(Vec3i)} uses {@code getLongX/getLongZ}, and
 *       {@code move/moved/inflatedBy/fromCorners/encapsulate/intersects/
 *       isInside} compute in the long domain, with long overloads where the
 *       public API needs them;</li>
 *   <li>{@code minX()/maxX()/minZ()/maxZ()} keep the int signature and the
 *       old int-safe saturation (clamp to the int range), so every existing
 *       int consumer keeps the exact behavior of the previous clamping patch;
 *       the exact value is available through the {@code *Long()} accessors.</li>
 * </ul>
 *
 * <p>Methods that are not part of the wide path (the {@code CODEC} int[]
 * projection, {@code forAllCorners}, the stream-codec encode projection) are
 * converted mechanically: a {@code GETFIELD} of a widened field is followed by
 * {@code L2I} and a {@code PUTFIELD} is preceded by {@code I2L}. That keeps
 * their int semantics bit-identical to vanilla at every coordinate that fits
 * an int.</p>
 *
 * <p>Scope: this patch only changes the structure box. It does not touch
 * collision, lighting, rendering or the worldgen random source.</p>
 */
public final class BoundingBoxPatch implements ClassPatch {

    private static final String TARGET =
        "net/minecraft/world/level/levelgen/structure/BoundingBox";
    private static final String BLOCKPOS = "net/minecraft/core/BlockPos";
    private static final String VEC3I = "net/minecraft/core/Vec3i";
    private static final String MATH = "java/lang/Math";
    private static final String OBJECT = "java/lang/Object";
    private static final String RET = "L" + TARGET + ";";
    private static final String BOX_DESC = "L" + TARGET + ";";

    private static final String MIN_X = "minX";
    private static final String MIN_Y = "minY";
    private static final String MIN_Z = "minZ";
    private static final String MAX_X = "maxX";
    private static final String MAX_Y = "maxY";
    private static final String MAX_Z = "maxZ";

    /** Same int-safe center bound as the previous clamping patch. */
    private static final long CLAMP_CENTER = 2_147_483_647L - 16_384L;

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        // Long storage only makes sense together with the Vec3i long accessors.
        if (!FarLandsPatcher.flag("farlands.wide", true)) {
            return original;
        }
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        FieldNode minX = field(node, MIN_X);
        if (minX != null && "J".equals(minX.desc)) {
            return original; // already patched
        }
        requireVanillaShape(node);

        // 1. Widen the four X/Z fields. minY/maxY stay int.
        for (String f : new String[]{MIN_X, MIN_Z, MAX_X, MAX_Z}) {
            field(node, f).desc = "J";
        }

        // 2. Mechanical int-context conversion for every remaining method:
        //    GETFIELD of a widened field must become (J, L2I), PUTFIELD (I2L, J).
        //    Explicit wide rewrites below clear their bodies, so this only
        //    survives where the old int semantics are intentional.
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions) {
                if (!(insn instanceof FieldInsnNode fi) || !TARGET.equals(fi.owner)
                    || !isXZField(fi.name) || !"I".equals(fi.desc)) {
                    continue;
                }
                fi.desc = "J";
                if (fi.getOpcode() == Opcodes.GETFIELD) {
                    m.instructions.insert(insn, new InsnNode(Opcodes.L2I));
                } else if (fi.getOpcode() == Opcodes.PUTFIELD) {
                    m.instructions.insertBefore(insn, new InsnNode(Opcodes.I2L));
                }
            }
        }

        // 3. Wide rewrites of the structure-boundary path.
        rewrite(node, "<init>", "(L" + BLOCKPOS + ";)V", blockPosConstructor());
        addWrapHelpers(node);
        patchConstructor(node);

        rewrite(node, "fromCorners", "(L" + VEC3I + ";L" + VEC3I + ";)" + RET, fromCorners());
        rewrite(node, "move", "(III)" + RET, moveInts());
        rewrite(node, "move", "(L" + VEC3I + ";)" + RET, moveVec());
        rewrite(node, "moved", "(III)" + RET, moved());
        rewrite(node, "inflatedBy", "(III)" + RET, inflatedBy());
        rewrite(node, "isInside", "(L" + VEC3I + ";)Z", isInsideVec());
        rewrite(node, "isInside", "(III)Z", isInsideInts());
        rewrite(node, "intersects", "(" + BOX_DESC + ")Z", intersectsBox());
        rewrite(node, "intersects", "(IIII)Z", intersectsInts());
        rewrite(node, "encapsulate", "(" + BOX_DESC + ")" + RET, encapsulateBox());
        rewrite(node, "encapsulate", "(L" + BLOCKPOS + ";)" + RET, encapsulatePos());
        rewrite(node, "encapsulating", "(" + BOX_DESC + BOX_DESC + ")" + RET, encapsulating());
        rewrite(node, "encapsulatingBoxes",
            "(Ljava/lang/Iterable;)Ljava/util/Optional;", encapsulatingBoxes());
        rewrite(node, "getLength", "()L" + VEC3I + ";", getLength());
        rewrite(node, "getXSpan", "()I", span(MAX_X, MIN_X));
        rewrite(node, "getZSpan", "()I", span(MAX_Z, MIN_Z));
        rewrite(node, "getCenter", "()L" + BLOCKPOS + ";", getCenter());
        rewrite(node, "toString", "()Ljava/lang/String;", toStringBody());
        rewrite(node, "equals", "(Ljava/lang/Object;)Z", equalsBody());
        rewrite(node, "hashCode", "()I", hashCodeBody());
        for (String f : new String[]{MIN_X, MIN_Z, MAX_X, MAX_Z}) {
            rewrite(node, f, "()I", clampIntView(f));
        }

        // 4. New wide API.
        addMethod(node, longConstructor());
        for (String f : new String[]{MIN_X, MIN_Z, MAX_X, MAX_Z}) {
            addMethod(node, longGetter(f));
        }
        addMethod(node, method(Opcodes.ACC_PUBLIC, "move", "(JJJ)" + RET, moveLong()));
        addMethod(node, method(Opcodes.ACC_PUBLIC, "intersects", "(JJJJ)Z", intersectsLongs()));
        addMethod(node, method(Opcodes.ACC_PUBLIC, "isInside", "(JIJ)Z", isInsideLong()));

        // 5. Post-conditions: widened storage + the new API, and no int-typed
        //    field reference left anywhere.
        for (String f : new String[]{MIN_X, MIN_Z, MAX_X, MAX_Z}) {
            FieldNode fn = field(node, f);
            if (fn == null || !"J".equals(fn.desc)) {
                throw new IllegalStateException(TARGET + "." + f + " was not widened");
            }
        }
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof FieldInsnNode fi && TARGET.equals(fi.owner)
                    && isXZField(fi.name) && !"J".equals(fi.desc)) {
                    throw new IllegalStateException("unwidened field reference " + fi.name
                        + " in " + m.name + m.desc);
                }
            }
        }
        for (String name : new String[]{"minXLong", "minZLong", "maxXLong", "maxZLong"}) {
            requireMethod(node, name, "()J");
        }
        requireMethod(node, "<init>", "(JJJJJJ)V");
        requireMethod(node, "move", "(JJJ)" + RET);
        requireMethod(node, "intersects", "(JJJJ)Z");
        requireMethod(node, "isInside", "(JIJ)Z");

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    // ------------------------------------------------------------------
    // preconditions / plumbing
    // ------------------------------------------------------------------

    private static void requireVanillaShape(ClassNode node) {
        for (String f : new String[]{MIN_X, MIN_Y, MIN_Z, MAX_X, MAX_Y, MAX_Z}) {
            FieldNode fn = field(node, f);
            if (fn == null) {
                throw new IllegalStateException(TARGET + "." + f + " missing");
            }
            if (!"I".equals(fn.desc)) {
                throw new IllegalStateException(TARGET + "." + f + " is " + fn.desc
                    + ", expected I (unexpected vanilla shape)");
            }
        }
        requireMethod(node, "<init>", "(IIIIII)V");
        requireMethod(node, "move", "(III)" + RET);
        requireMethod(node, "move", "(L" + VEC3I + ";)" + RET);
        requireMethod(node, "getXSpan", "()I");
        requireMethod(node, "minX", "()I");
    }

    private static boolean isXZField(String name) {
        return MIN_X.equals(name) || MIN_Z.equals(name) || MAX_X.equals(name) || MAX_Z.equals(name);
    }

    private static FieldNode field(ClassNode node, String name) {
        for (FieldNode f : node.fields) {
            if (name.equals(f.name)) {
                return f;
            }
        }
        return null;
    }

    private static MethodNode find(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) {
            if (name.equals(m.name) && desc.equals(m.desc)) {
                return m;
            }
        }
        return null;
    }

    private static void requireMethod(ClassNode node, String name, String desc) {
        if (find(node, name, desc) == null) {
            StringBuilder sb = new StringBuilder();
            for (MethodNode m : node.methods) {
                if (name.equals(m.name)) {
                    sb.append(m.name).append(m.desc).append(' ');
                }
            }
            throw new IllegalStateException(TARGET + "#" + name + desc + " not found; candidates: "
                + sb);
        }
    }

    private static void addMethod(ClassNode node, MethodNode mn) {
        if (find(node, mn.name, mn.desc) != null) {
            throw new IllegalStateException(TARGET + "#" + mn.name + mn.desc + " already exists");
        }
        node.methods.add(mn);
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

    // ------------------------------------------------------------------
    // emitters
    // ------------------------------------------------------------------

    private static void aload(InsnList il, int slot) {
        il.add(new VarInsnNode(Opcodes.ALOAD, slot));
    }

    private static void iload(InsnList il, int slot) {
        il.add(new VarInsnNode(Opcodes.ILOAD, slot));
    }

    private static void lload(InsnList il, int slot) {
        il.add(new VarInsnNode(Opcodes.LLOAD, slot));
    }

    /** Push {@code slot.<field>} (long). */
    private static void getLong(InsnList il, int objSlot, String field) {
        aload(il, objSlot);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, field, "J"));
    }

    /** Push {@code slot.<field>} (int). */
    private static void getInt(InsnList il, int objSlot, String field) {
        aload(il, objSlot);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, field, "I"));
    }

    /** {@code PUTFIELD <field>:J}; the objectref and value must be on the stack. */
    private static void putLong(InsnList il, String field) {
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, field, "J"));
    }

    /** {@code PUTFIELD <field>:I}; the objectref and value must be on the stack. */
    private static void putInt(InsnList il, String field) {
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, field, "I"));
    }

    private static void invokeStatic(InsnList il, String owner, String name, String desc) {
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, name, desc, false));
    }

    private static void invokeVirtual(InsnList il, String owner, String name, String desc) {
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, name, desc, false));
    }

    private static void invokeInterface(InsnList il, String owner, String name, String desc) {
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, owner, name, desc, true));
    }

    /** Push {@code slot.getLongX()} / {@code slot.getLongZ()} (Vec3i). */
    private static void vecLong(InsnList il, int slot, String axis) {
        aload(il, slot);
        invokeVirtual(il, VEC3I, "getLong" + axis, "()J");
    }

    /** Push {@code slot.getY()} (Vec3i). */
    private static void vecY(InsnList il, int slot) {
        aload(il, slot);
        invokeVirtual(il, VEC3I, "getY", "()I");
    }

    private static void minLong(InsnList il) {
        invokeStatic(il, MATH, "min", "(JJ)J");
    }

    private static void maxLong(InsnList il) {
        invokeStatic(il, MATH, "max", "(JJ)J");
    }

    private static void minInt(InsnList il) {
        invokeStatic(il, MATH, "min", "(II)I");
    }

    private static void maxInt(InsnList il) {
        invokeStatic(il, MATH, "max", "(II)I");
    }

    /** {@code (int) Math.clamp(field, MIN, MAX)}: the old int-safe view. */
    private static InsnList clampIntView(String field) {
        InsnList il = new InsnList();
        aload(il, 0);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, field, "J"));
        il.add(new LdcInsnNode(Integer.MIN_VALUE));
        il.add(new LdcInsnNode(Integer.MAX_VALUE));
        invokeStatic(il, MATH, "clamp", "(JII)I");
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    private static MethodNode method(int access, String name, String desc, InsnList body) {
        MethodNode mn = new MethodNode(access, name, desc, null, null);
        mn.instructions.add(body);
        return mn;
    }

    // ------------------------------------------------------------------
    // constructors
    // ------------------------------------------------------------------

    /** {@code BoundingBox(BlockPos)}: keep the wide coordinates of the corner. */
    private static InsnList blockPosConstructor() {
        InsnList il = new InsnList();
        aload(il, 0);
        vecLong(il, 1, "X");
        vecY(il, 1);
        il.add(new InsnNode(Opcodes.I2L));
        vecLong(il, 1, "Z");
        vecLong(il, 1, "X");
        vecY(il, 1);
        il.add(new InsnNode(Opcodes.I2L));
        vecLong(il, 1, "Z");
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJJJJ)V", false));
        il.add(new InsnNode(Opcodes.RETURN));
        return il;
    }

    /** Wide constructor; Y is clamped into the int range it is stored in. */
    private static MethodNode longConstructor() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(JJJJJJ)V", null, null);
        InsnList il = mn.instructions;
        aload(il, 0);
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, OBJECT, "<init>", "()V", false));

        aload(il, 0);
        lload(il, 1);
        putLong(il, MIN_X);

        aload(il, 0);
        lload(il, 3);
        il.add(new LdcInsnNode(Integer.MIN_VALUE));
        il.add(new LdcInsnNode(Integer.MAX_VALUE));
        invokeStatic(il, MATH, "clamp", "(JII)I");
        putInt(il, MIN_Y);

        aload(il, 0);
        lload(il, 5);
        putLong(il, MIN_Z);

        aload(il, 0);
        lload(il, 7);
        putLong(il, MAX_X);

        aload(il, 0);
        lload(il, 9);
        il.add(new LdcInsnNode(Integer.MIN_VALUE));
        il.add(new LdcInsnNode(Integer.MAX_VALUE));
        invokeStatic(il, MATH, "clamp", "(JII)I");
        putInt(il, MAX_Y);

        aload(il, 0);
        lload(il, 11);
        putLong(il, MAX_Z);

        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    // ---- seam-aware (IIIIII) constructor preamble ---------------------

    /**
     * Adds the two private static helpers used by the constructor preamble.
     * Each takes the raw (min,max) int pair and, only when the pair is inverted
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
        LabelNode ret = new LabelNode();
        // if (max >= min) return raw;
        iload(il, 1);
        iload(il, 0);
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGE, ret));
        // long umin = min & 0xFFFFFFFFL;
        iload(il, 0);
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new LdcInsnNode(0xFFFFFFFFL));
        il.add(new InsnNode(Opcodes.LAND));
        il.add(new VarInsnNode(Opcodes.LSTORE, 2));
        // long umax = max & 0xFFFFFFFFL;
        iload(il, 1);
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new LdcInsnNode(0xFFFFFFFFL));
        il.add(new InsnNode(Opcodes.LAND));
        il.add(new VarInsnNode(Opcodes.LSTORE, 4));
        // if (umax < umin) return raw;  (genuine inversion, leave to vanilla)
        il.add(new VarInsnNode(Opcodes.LLOAD, 4));
        il.add(new VarInsnNode(Opcodes.LLOAD, 2));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFLT, ret));
        // if (umax - umin > 1e9) return raw;  (not a short seam-crossing span)
        il.add(new VarInsnNode(Opcodes.LLOAD, 4));
        il.add(new VarInsnNode(Opcodes.LLOAD, 2));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new LdcInsnNode(1_000_000_000L));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFGT, ret));
        // return (int) Math.clamp(umax | umin, -MAX, MAX);
        il.add(new VarInsnNode(Opcodes.LLOAD, min ? 2 : 4));
        il.add(new LdcInsnNode(-2_147_483_647L));
        il.add(new LdcInsnNode(2_147_483_647L));
        invokeStatic(il, MATH, "clamp", "(JJJ)J");
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(ret);
        iload(il, min ? 0 : 1);
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
        ctor.instructions.insert(il);
    }

    private static void axisNorm(InsnList il, int minSlot, int maxSlot) {
        iload(il, minSlot);
        il.add(new VarInsnNode(Opcodes.ISTORE, 7));
        iload(il, maxSlot);
        il.add(new VarInsnNode(Opcodes.ISTORE, 8));
        iload(il, 7);
        iload(il, 8);
        invokeStatic(il, TARGET, "farlands$wrapMin", "(II)I");
        il.add(new VarInsnNode(Opcodes.ISTORE, minSlot));
        iload(il, 7);
        iload(il, 8);
        invokeStatic(il, TARGET, "farlands$wrapMax", "(II)I");
        il.add(new VarInsnNode(Opcodes.ISTORE, maxSlot));
    }

    // ------------------------------------------------------------------
    // factories / accessors / movement
    // ------------------------------------------------------------------

    private static InsnList fromCorners() {
        InsnList il = new InsnList();
        il.add(new TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        // minX, minY, minZ, maxX, maxY, maxZ (constructor order).
        for (boolean min : new boolean[]{true, false}) {
            for (String axis : new String[]{"X", "Y", "Z"}) {
                if ("Y".equals(axis)) {
                    vecY(il, 0);
                    vecY(il, 1);
                    if (min) {
                        minInt(il);
                    } else {
                        maxInt(il);
                    }
                    il.add(new InsnNode(Opcodes.I2L));
                } else {
                    vecLong(il, 0, axis);
                    vecLong(il, 1, axis);
                    if (min) {
                        minLong(il);
                    } else {
                        maxLong(il);
                    }
                }
            }
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJJJJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static InsnList moveInts() {
        InsnList il = new InsnList();
        aload(il, 0);
        iload(il, 1);
        il.add(new InsnNode(Opcodes.I2L));
        iload(il, 2);
        il.add(new InsnNode(Opcodes.I2L));
        iload(il, 3);
        il.add(new InsnNode(Opcodes.I2L));
        invokeVirtual(il, TARGET, "move", "(JJJ)" + RET);
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static InsnList moveVec() {
        InsnList il = new InsnList();
        aload(il, 0);
        vecLong(il, 1, "X");
        vecY(il, 1);
        il.add(new InsnNode(Opcodes.I2L));
        vecLong(il, 1, "Z");
        invokeVirtual(il, TARGET, "move", "(JJJ)" + RET);
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static InsnList moveLong() {
        InsnList il = new InsnList();
        // X: long + dx
        for (String f : new String[]{MIN_X, MAX_X}) {
            aload(il, 0);
            getLong(il, 0, f);
            lload(il, 1);
            il.add(new InsnNode(Opcodes.LADD));
            putLong(il, f);
        }
        // Y: int + (int) dy
        for (String f : new String[]{MIN_Y, MAX_Y}) {
            aload(il, 0);
            getInt(il, 0, f);
            lload(il, 3);
            il.add(new InsnNode(Opcodes.L2I));
            il.add(new InsnNode(Opcodes.IADD));
            putInt(il, f);
        }
        // Z: long + dz
        for (String f : new String[]{MIN_Z, MAX_Z}) {
            aload(il, 0);
            getLong(il, 0, f);
            lload(il, 5);
            il.add(new InsnNode(Opcodes.LADD));
            putLong(il, f);
        }
        aload(il, 0);
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static InsnList moved() {
        InsnList il = new InsnList();
        il.add(new TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        // Constructor order: minX, minY, minZ, maxX, maxY, maxZ.
        pushFieldPlus(il, MIN_X, 1, true);
        pushFieldPlus(il, MIN_Y, 2, false);
        pushFieldPlus(il, MIN_Z, 3, true);
        pushFieldPlus(il, MAX_X, 1, true);
        pushFieldPlus(il, MAX_Y, 2, false);
        pushFieldPlus(il, MAX_Z, 3, true);
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJJJJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    /** Push {@code this.<field> + iload(addSlot)} as a long. */
    private static void pushFieldPlus(InsnList il, String field, int addSlot, boolean isLong) {
        aload(il, 0);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, field, isLong ? "J" : "I"));
        iload(il, addSlot);
        if (isLong) {
            il.add(new InsnNode(Opcodes.I2L));
            il.add(new InsnNode(Opcodes.LADD));
        } else {
            il.add(new InsnNode(Opcodes.IADD));
            il.add(new InsnNode(Opcodes.I2L));
        }
    }

    private static InsnList inflatedBy() {
        InsnList il = new InsnList();
        il.add(new TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        pushFieldMinus(il, MIN_X, 1);
        pushFieldMinusInt(il, MIN_Y, 2);
        pushFieldMinus(il, MIN_Z, 3);
        pushFieldPlus(il, MAX_X, 1, true);
        pushFieldPlus(il, MAX_Y, 2, false);
        pushFieldPlus(il, MAX_Z, 3, true);
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJJJJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static void pushFieldMinus(InsnList il, String field, int slot) {
        aload(il, 0);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, field, "J"));
        iload(il, slot);
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LSUB));
    }

    private static void pushFieldMinusInt(InsnList il, String field, int slot) {
        aload(il, 0);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, field, "I"));
        iload(il, slot);
        il.add(new InsnNode(Opcodes.ISUB));
        il.add(new InsnNode(Opcodes.I2L));
    }

    private static MethodNode longGetter(String field) {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, field + "Long", "()J", null, null);
        getLong(mn.instructions, 0, field);
        mn.instructions.add(new InsnNode(Opcodes.LRETURN));
        return mn;
    }

    // ------------------------------------------------------------------
    // predicates
    // ------------------------------------------------------------------

    private static InsnList isInsideVec() {
        InsnList il = new InsnList();
        aload(il, 0);
        vecLong(il, 1, "X");
        vecY(il, 1);
        vecLong(il, 1, "Z");
        invokeVirtual(il, TARGET, "isInside", "(JIJ)Z");
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    private static InsnList isInsideInts() {
        InsnList il = new InsnList();
        aload(il, 0);
        iload(il, 1);
        il.add(new InsnNode(Opcodes.I2L));
        iload(il, 2);
        iload(il, 3);
        il.add(new InsnNode(Opcodes.I2L));
        invokeVirtual(il, TARGET, "isInside", "(JIJ)Z");
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    private static InsnList isInsideLong() {
        InsnList il = new InsnList();
        LabelNode no = new LabelNode();
        lload(il, 1);
        getLong(il, 0, MIN_X);
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFLT, no));
        lload(il, 1);
        getLong(il, 0, MAX_X);
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFGT, no));
        lload(il, 4);
        getLong(il, 0, MIN_Z);
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFLT, no));
        lload(il, 4);
        getLong(il, 0, MAX_Z);
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFGT, no));
        iload(il, 3);
        getInt(il, 0, MIN_Y);
        il.add(new JumpInsnNode(Opcodes.IF_ICMPLT, no));
        iload(il, 3);
        getInt(il, 0, MAX_Y);
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGT, no));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(no);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    private static InsnList intersectsBox() {
        InsnList il = new InsnList();
        LabelNode no = new LabelNode();
        getLong(il, 0, MAX_X);
        getLong(il, 1, MIN_X);
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFLT, no));
        getLong(il, 0, MIN_X);
        getLong(il, 1, MAX_X);
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFGT, no));
        getLong(il, 0, MAX_Z);
        getLong(il, 1, MIN_Z);
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFLT, no));
        getLong(il, 0, MIN_Z);
        getLong(il, 1, MAX_Z);
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFGT, no));
        getInt(il, 0, MAX_Y);
        getInt(il, 1, MIN_Y);
        il.add(new JumpInsnNode(Opcodes.IF_ICMPLT, no));
        getInt(il, 0, MIN_Y);
        getInt(il, 1, MAX_Y);
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGT, no));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(no);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    private static InsnList intersectsLongs() {
        InsnList il = new InsnList();
        LabelNode no = new LabelNode();
        // (minX, minZ, maxX, maxZ)
        getLong(il, 0, MAX_X);
        lload(il, 1);
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFLT, no));
        getLong(il, 0, MIN_X);
        lload(il, 5);
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFGT, no));
        getLong(il, 0, MAX_Z);
        lload(il, 3);
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFLT, no));
        getLong(il, 0, MIN_Z);
        lload(il, 7);
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFGT, no));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(no);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    private static InsnList intersectsInts() {
        InsnList il = new InsnList();
        aload(il, 0);
        iload(il, 1);
        il.add(new InsnNode(Opcodes.I2L));
        iload(il, 2);
        il.add(new InsnNode(Opcodes.I2L));
        iload(il, 3);
        il.add(new InsnNode(Opcodes.I2L));
        iload(il, 4);
        il.add(new InsnNode(Opcodes.I2L));
        invokeVirtual(il, TARGET, "intersects", "(JJJJ)Z");
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    // ------------------------------------------------------------------
    // merging
    // ------------------------------------------------------------------

    private static InsnList encapsulateBox() {
        InsnList il = new InsnList();
        // X
        aload(il, 0);
        getLong(il, 0, MIN_X);
        getLong(il, 1, MIN_X);
        minLong(il);
        putLong(il, MIN_X);
        aload(il, 0);
        getLong(il, 0, MAX_X);
        getLong(il, 1, MAX_X);
        maxLong(il);
        putLong(il, MAX_X);
        // Y
        aload(il, 0);
        getInt(il, 0, MIN_Y);
        getInt(il, 1, MIN_Y);
        minInt(il);
        putInt(il, MIN_Y);
        aload(il, 0);
        getInt(il, 0, MAX_Y);
        getInt(il, 1, MAX_Y);
        maxInt(il);
        putInt(il, MAX_Y);
        // Z
        aload(il, 0);
        getLong(il, 0, MIN_Z);
        getLong(il, 1, MIN_Z);
        minLong(il);
        putLong(il, MIN_Z);
        aload(il, 0);
        getLong(il, 0, MAX_Z);
        getLong(il, 1, MAX_Z);
        maxLong(il);
        putLong(il, MAX_Z);
        aload(il, 0);
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static InsnList encapsulatePos() {
        InsnList il = new InsnList();
        aload(il, 0);
        getLong(il, 0, MIN_X);
        vecLong(il, 1, "X");
        minLong(il);
        putLong(il, MIN_X);
        aload(il, 0);
        getLong(il, 0, MAX_X);
        vecLong(il, 1, "X");
        maxLong(il);
        putLong(il, MAX_X);
        aload(il, 0);
        getInt(il, 0, MIN_Y);
        vecY(il, 1);
        minInt(il);
        putInt(il, MIN_Y);
        aload(il, 0);
        getInt(il, 0, MAX_Y);
        vecY(il, 1);
        maxInt(il);
        putInt(il, MAX_Y);
        aload(il, 0);
        getLong(il, 0, MIN_Z);
        vecLong(il, 1, "Z");
        minLong(il);
        putLong(il, MIN_Z);
        aload(il, 0);
        getLong(il, 0, MAX_Z);
        vecLong(il, 1, "Z");
        maxLong(il);
        putLong(il, MAX_Z);
        aload(il, 0);
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static InsnList encapsulating() {
        InsnList il = new InsnList();
        il.add(new TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        // Constructor order: minX, minY, minZ, maxX, maxY, maxZ.
        getLong(il, 0, MIN_X);
        getLong(il, 1, MIN_X);
        minLong(il);
        getInt(il, 0, MIN_Y);
        getInt(il, 1, MIN_Y);
        minInt(il);
        il.add(new InsnNode(Opcodes.I2L));
        getLong(il, 0, MIN_Z);
        getLong(il, 1, MIN_Z);
        minLong(il);
        getLong(il, 0, MAX_X);
        getLong(il, 1, MAX_X);
        maxLong(il);
        getInt(il, 0, MAX_Y);
        getInt(il, 1, MAX_Y);
        maxInt(il);
        il.add(new InsnNode(Opcodes.I2L));
        getLong(il, 0, MAX_Z);
        getLong(il, 1, MAX_Z);
        maxLong(il);
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJJJJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static InsnList encapsulatingBoxes() {
        InsnList il = new InsnList();
        LabelNode nonEmpty = new LabelNode();
        LabelNode loop = new LabelNode();
        LabelNode done = new LabelNode();
        aload(il, 0);
        invokeInterface(il, "java/lang/Iterable", "iterator", "()Ljava/util/Iterator;");
        il.add(new VarInsnNode(Opcodes.ASTORE, 1));
        aload(il, 1);
        invokeInterface(il, "java/util/Iterator", "hasNext", "()Z");
        il.add(new JumpInsnNode(Opcodes.IFNE, nonEmpty));
        invokeStatic(il, "java/util/Optional", "empty", "()Ljava/util/Optional;");
        il.add(new InsnNode(Opcodes.ARETURN));
        il.add(nonEmpty);
        aload(il, 1);
        invokeInterface(il, "java/util/Iterator", "next", "()Ljava/lang/Object;");
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, TARGET));
        il.add(new VarInsnNode(Opcodes.ASTORE, 2));
        il.add(new TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        aload(il, 2);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, MIN_X, "J"));
        aload(il, 2);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, MIN_Y, "I"));
        il.add(new InsnNode(Opcodes.I2L));
        aload(il, 2);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, MIN_Z, "J"));
        aload(il, 2);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, MAX_X, "J"));
        aload(il, 2);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, MAX_Y, "I"));
        il.add(new InsnNode(Opcodes.I2L));
        aload(il, 2);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, MAX_Z, "J"));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJJJJ)V", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 3));
        il.add(loop);
        aload(il, 1);
        invokeInterface(il, "java/util/Iterator", "hasNext", "()Z");
        il.add(new JumpInsnNode(Opcodes.IFEQ, done));
        aload(il, 3);
        aload(il, 1);
        invokeInterface(il, "java/util/Iterator", "next", "()Ljava/lang/Object;");
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, TARGET));
        invokeVirtual(il, TARGET, "encapsulate", "(" + BOX_DESC + ")" + RET);
        il.add(new InsnNode(Opcodes.POP));
        il.add(new JumpInsnNode(Opcodes.GOTO, loop));
        il.add(done);
        aload(il, 3);
        invokeStatic(il, "java/util/Optional", "of", "(Ljava/lang/Object;)Ljava/util/Optional;");
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    // ------------------------------------------------------------------
    // extents / center
    // ------------------------------------------------------------------

    private static InsnList getLength() {
        InsnList il = new InsnList();
        il.add(new TypeInsnNode(Opcodes.NEW, VEC3I));
        il.add(new InsnNode(Opcodes.DUP));
        aload(il, 0);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, MAX_X, "J"));
        aload(il, 0);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, MIN_X, "J"));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new InsnNode(Opcodes.L2I));
        getInt(il, 0, MAX_Y);
        getInt(il, 0, MIN_Y);
        il.add(new InsnNode(Opcodes.ISUB));
        aload(il, 0);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, MAX_Z, "J"));
        aload(il, 0);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, MIN_Z, "J"));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, VEC3I, "<init>", "(III)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static InsnList span(String maxField, String minField) {
        InsnList il = new InsnList();
        aload(il, 0);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, maxField, "J"));
        aload(il, 0);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, minField, "J"));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new IntInsnNode(Opcodes.SIPUSH, 256));
        invokeStatic(il, MATH, "clamp", "(JII)I");
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    private static InsnList getCenter() {
        InsnList il = new InsnList();
        il.add(new TypeInsnNode(Opcodes.NEW, BLOCKPOS));
        il.add(new InsnNode(Opcodes.DUP));
        centerAxis(il, MIN_X, MAX_X);
        centerAxisY(il);
        centerAxis(il, MIN_Z, MAX_Z);
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, BLOCKPOS, "<init>", "(III)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static void centerAxis(InsnList il, String minField, String maxField) {
        aload(il, 0);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, minField, "J"));
        aload(il, 0);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, maxField, "J"));
        aload(il, 0);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, minField, "J"));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new LdcInsnNode(2L));
        il.add(new InsnNode(Opcodes.LDIV));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new LdcInsnNode(-CLAMP_CENTER - 1));
        il.add(new LdcInsnNode(CLAMP_CENTER));
        invokeStatic(il, MATH, "clamp", "(JJJ)J");
        il.add(new InsnNode(Opcodes.L2I));
    }

    private static void centerAxisY(InsnList il) {
        aload(il, 0);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, MIN_Y, "I"));
        il.add(new InsnNode(Opcodes.I2L));
        aload(il, 0);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, MAX_Y, "I"));
        il.add(new InsnNode(Opcodes.I2L));
        aload(il, 0);
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, MIN_Y, "I"));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new LdcInsnNode(2L));
        il.add(new InsnNode(Opcodes.LDIV));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new IntInsnNode(Opcodes.BIPUSH, -64));
        il.add(new IntInsnNode(Opcodes.SIPUSH, 320));
        invokeStatic(il, MATH, "clamp", "(JII)I");
    }

    // ------------------------------------------------------------------
    // value semantics
    // ------------------------------------------------------------------

    private static InsnList toStringBody() {
        InsnList il = new InsnList();
        aload(il, 0);
        invokeStatic(il, "com/google/common/base/MoreObjects", "toStringHelper",
            "(Ljava/lang/Object;)Lcom/google/common/base/MoreObjects$ToStringHelper;");
        for (String f : new String[]{MIN_X, MIN_Y, MIN_Z, MAX_X, MAX_Y, MAX_Z}) {
            il.add(new LdcInsnNode(f));
            aload(il, 0);
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, f,
                isXZField(f) ? "J" : "I"));
            invokeVirtual(il, "com/google/common/base/MoreObjects$ToStringHelper", "add",
                "(Ljava/lang/String;" + (isXZField(f) ? "J" : "I")
                    + ")Lcom/google/common/base/MoreObjects$ToStringHelper;");
        }
        invokeVirtual(il, "com/google/common/base/MoreObjects$ToStringHelper", "toString",
            "()Ljava/lang/String;");
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static InsnList equalsBody() {
        InsnList il = new InsnList();
        LabelNode notSame = new LabelNode();
        LabelNode no = new LabelNode();
        aload(il, 0);
        aload(il, 1);
        il.add(new JumpInsnNode(Opcodes.IF_ACMPNE, notSame));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(notSame);
        aload(il, 1);
        il.add(new TypeInsnNode(Opcodes.INSTANCEOF, TARGET));
        il.add(new JumpInsnNode(Opcodes.IFEQ, no));
        aload(il, 1);
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, TARGET));
        il.add(new VarInsnNode(Opcodes.ASTORE, 2));
        for (String f : new String[]{MIN_X, MIN_Z, MAX_X, MAX_Z}) {
            aload(il, 0);
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, f, "J"));
            aload(il, 2);
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, f, "J"));
            il.add(new InsnNode(Opcodes.LCMP));
            il.add(new JumpInsnNode(Opcodes.IFNE, no));
        }
        for (String f : new String[]{MIN_Y, MAX_Y}) {
            aload(il, 0);
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, f, "I"));
            aload(il, 2);
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, f, "I"));
            il.add(new JumpInsnNode(Opcodes.IF_ICMPNE, no));
        }
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(no);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    private static InsnList hashCodeBody() {
        InsnList il = new InsnList();
        il.add(new IntInsnNode(Opcodes.BIPUSH, 6));
        il.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        int index = 0;
        for (String f : new String[]{MIN_X, MIN_Y, MIN_Z, MAX_X, MAX_Y, MAX_Z}) {
            il.add(new InsnNode(Opcodes.DUP));
            il.add(new IntInsnNode(Opcodes.BIPUSH, index++));
            aload(il, 0);
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, f, isXZField(f) ? "J" : "I"));
            if (isXZField(f)) {
                invokeStatic(il, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;");
            } else {
                invokeStatic(il, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
            }
            il.add(new InsnNode(Opcodes.AASTORE));
        }
        invokeStatic(il, "java/util/Objects", "hash", "([Ljava/lang/Object;)I");
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }
}
