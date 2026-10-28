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
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Splits the three widened {@code long} coordinates of {@code Vec3i} into
 * inline 128-bit limbs: {@code xLo/xHi}, {@code yLo/yHi}, {@code zLo/zHi}.
 *
 * <p>Runs after {@link Vec3iWidePatch}, which converts the storage from
 * {@code int} to a single {@code long} per axis. This patch keeps the exact
 * low-limb (long view) semantics of that state and adds the high limb, so every
 * existing caller of {@code getX()/getLongX()/offset()/...} is unchanged while
 * {@code getWideXLo()/getWideXHi()} expose the full 128-bit value.</p>
 *
 * <p>Design rules:</p>
 * <ul>
 *   <li>{@code getLongX()} returns the low limb (exact only within 64 bits,
 *       like the previous single-long storage);</li>
 *   <li>constructors from {@code int}/{@code long} sign-extend into
 *       {@code hi}, so canonical values keep the old equality and ordering;</li>
 *   <li>2-limb arithmetic carries/borrows explicitly and leaves the same low
 *       limb as the previous long arithmetic, so results inside 64 bits are
 *       bit-identical;</li>
 *   <li>{@code equals/hashCode/compareTo/toString} compare canonical limbs;
 *       for sign-extended (canonical) values they are equivalent to the old
 *       single-long value (the hash formula is also bit-identical to the
 *       previous int-view formula because int arithmetic is mod 2^32).</li>
 * </ul>
 *
 * <p>Every expected field/method/instruction site is asserted: a missing match
 * throws instead of silently producing a half-rewritten class.</p>
 */
public final class Vec3iInline128Patch implements ClassPatch {

    private static final String TARGET = "net/minecraft/core/Vec3i";
    private static final String RET = "Lnet/minecraft/core/Vec3i;";
    private static final String[] AXIS = {"x", "y", "z"};
    private static final String[] AXIS_U = {"X", "Y", "Z"};
    private static final long[] HASH_PRIME = {
        0x9E3779B97F4A7C15L, 0xC2B2AE3D27D4EB4FL, 0x165667B19E3779F9L};

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        // Idempotency: our high limbs only exist after this patch ran.
        if (field(node, "xHi") != null) {
            return original;
        }
        transform(node);

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    /** Applies the field split + rewrites to a parsed, already widened class. */
    static void transform(ClassNode node) {
        requireWidenedState(node);

        // 1. Add the high limbs, then rename x/y/z to xLo/yLo/zLo.
        for (String axis : AXIS) {
            node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, axis + "Hi", "J", null, null));
        }
        for (FieldNode f : node.fields) {
            if (isAxis(f.name)) {
                f.name = f.name + "Lo";
            }
        }

        // 2. Retarget every field instruction from the old single field to the
        //    low limb. Writers are replaced wholesale in step 3; reads keep the
        //    previous long-view semantics by construction.
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof FieldInsnNode fi && TARGET.equals(fi.owner)
                    && "J".equals(fi.desc) && isAxis(fi.name)) {
                    fi.name = fi.name + "Lo";
                }
            }
        }

        // 3. Replace the writers (they must update lo+hi together).
        rewrite(node, "<init>", "(III)V", intConstructorBody());
        rewrite(node, "<init>", "(JJJ)V", longConstructorBody());
        for (int i = 0; i < 3; i++) {
            rewrite(node, "set" + AXIS_U[i], "(I)" + RET, intSetter(AXIS[i]));
            rewrite(node, "setLong" + AXIS_U[i], "(J)" + RET, longSetter(AXIS[i]));
        }

        // No method may still write a lone low limb.
        assertNoLoneWrites(node);

        // 4. New API: full 6-limb constructor, wide accessors and wide setters.
        addMethod(node, long6Constructor());
        for (int i = 0; i < 3; i++) {
            addMethod(node, wideGetter("getWide" + AXIS_U[i] + "Lo", AXIS[i] + "Lo"));
            addMethod(node, wideGetter("getWide" + AXIS_U[i] + "Hi", AXIS[i] + "Hi"));
            addMethod(node, wideSetter("setWide" + AXIS_U[i], AXIS[i]));
        }

        // 5. 2-limb arithmetic.
        rewrite(node, "offset", "(III)" + RET, newVec6(offsetInts()));
        rewrite(node, "offset", "(Lnet/minecraft/core/Vec3i;)" + RET, newVec6(offsetVec()));
        rewrite(node, "subtract", "(Lnet/minecraft/core/Vec3i;)" + RET, newVec6(subtractVec()));
        rewrite(node, "multiply", "(I)" + RET, newVec6(multiplyInt()));
        rewrite(node, "relative", "(Lnet/minecraft/core/Direction;I)" + RET, newVec6(relativeDir()));
        rewrite(node, "cross", "(Lnet/minecraft/core/Vec3i;)" + RET, newVec6(crossVec()));

        // 6. Value semantics over the limbs (canonical sign-extended hi).
        rewrite(node, "equals", "(Ljava/lang/Object;)Z", equalsBody());
        rewrite(node, "hashCode", "()I", hashCodeBody());
        rewrite(node, "compareTo", "(Lnet/minecraft/core/Vec3i;)I", compareToBody());
        rewrite(node, "toString", "()Ljava/lang/String;", toStringBody());
        addMethod(node, compareWideHelper());
        addMethod(node, axisStringHelper());

        // 7. Final audit: no instruction may reference the pre-split fields.
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof FieldInsnNode fi && TARGET.equals(fi.owner)
                    && isAxis(fi.name)) {
                    throw new IllegalStateException("unrewritten Vec3i field reference "
                        + fi.name + " in " + m.name + m.desc);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // preconditions / field plumbing
    // ------------------------------------------------------------------

    private static void requireWidenedState(ClassNode node) {
        for (String axis : AXIS) {
            FieldNode f = field(node, axis);
            if (f == null) {
                throw new IllegalStateException("Vec3i." + axis + " missing: Vec3iWidePatch must run first");
            }
            if (!"J".equals(f.desc)) {
                throw new IllegalStateException("Vec3i." + axis + " is " + f.desc
                    + ", expected J: Vec3iWidePatch must run first");
            }
            if (field(node, axis + "Lo") != null) {
                throw new IllegalStateException("Vec3i." + axis + "Lo already present but " + axis + "Hi missing");
            }
        }
        for (String axis : AXIS_U) {
            requireMethod(node, "getLong" + axis, "()J");
            requireMethod(node, "setLong" + axis, "(J)" + RET);
        }
        requireMethod(node, "<init>", "(JJJ)V");
        requireMethod(node, "offset", "(III)" + RET);
        requireMethod(node, "offset", "(Lnet/minecraft/core/Vec3i;)" + RET);
        requireMethod(node, "subtract", "(Lnet/minecraft/core/Vec3i;)" + RET);
        requireMethod(node, "multiply", "(I)" + RET);
        requireMethod(node, "relative", "(Lnet/minecraft/core/Direction;I)" + RET);
        requireMethod(node, "cross", "(Lnet/minecraft/core/Vec3i;)" + RET);
    }

    /**
     * Every writer must update the high limb as well: a lone low-limb write
     * would leave a stale high limb and silently corrupt the wide value.
     */
    private static void assertNoLoneWrites(ClassNode node) {
        for (MethodNode m : node.methods) {
            java.util.Set<String> wroteLo = new java.util.HashSet<>();
            java.util.Set<String> wroteHi = new java.util.HashSet<>();
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof FieldInsnNode fi && fi.getOpcode() == Opcodes.PUTFIELD
                    && TARGET.equals(fi.owner)) {
                    if (isLo(fi.name)) {
                        wroteLo.add(fi.name.substring(0, 1));
                    } else if (fi.name.endsWith("Hi")) {
                        wroteHi.add(fi.name.substring(0, 1));
                    }
                }
            }
            for (String axis : wroteLo) {
                if (!wroteHi.contains(axis)) {
                    throw new IllegalStateException("unpaired low-limb write to Vec3i."
                        + axis + "Lo in " + m.name + m.desc);
                }
            }
        }
    }

    private static boolean isAxis(String name) {
        return "x".equals(name) || "y".equals(name) || "z".equals(name);
    }

    private static boolean isLo(String name) {
        return "xLo".equals(name) || "yLo".equals(name) || "zLo".equals(name);
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
            throw new IllegalStateException(TARGET + "#" + name + desc + " not found");
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
    // constructors and setters
    // ------------------------------------------------------------------

    private static InsnList intConstructorBody() {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        for (int i = 0; i < 3; i++) {
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new VarInsnNode(Opcodes.ILOAD, i + 1));
            il.add(new InsnNode(Opcodes.I2L));
            il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, AXIS[i] + "Lo", "J"));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new VarInsnNode(Opcodes.ILOAD, i + 1));
            il.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, 31));
            il.add(new InsnNode(Opcodes.ISHR));
            il.add(new InsnNode(Opcodes.I2L));
            il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, AXIS[i] + "Hi", "J"));
        }
        il.add(new InsnNode(Opcodes.RETURN));
        return il;
    }

    private static InsnList longConstructorBody() {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        for (int i = 0; i < 3; i++) {
            int slot = 1 + 2 * i;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new VarInsnNode(Opcodes.LLOAD, slot));
            il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, AXIS[i] + "Lo", "J"));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new VarInsnNode(Opcodes.LLOAD, slot));
            il.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, 63));
            il.add(new InsnNode(Opcodes.LSHR));
            il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, AXIS[i] + "Hi", "J"));
        }
        il.add(new InsnNode(Opcodes.RETURN));
        return il;
    }

    /** {@code (xLo,xHi,yLo,yHi,zLo,zHi)V} - full inline 128-bit constructor. */
    private static MethodNode long6Constructor() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(JJJJJJ)V", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        for (int i = 0; i < 3; i++) {
            int loSlot = 1 + 4 * i;
            int hiSlot = loSlot + 2;
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new VarInsnNode(Opcodes.LLOAD, loSlot));
            il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, AXIS[i] + "Lo", "J"));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new VarInsnNode(Opcodes.LLOAD, hiSlot));
            il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, AXIS[i] + "Hi", "J"));
        }
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    /** {@code protected Vec3i setX(int v)} - int view, sign-extended into hi. */
    private static InsnList intSetter(String axis) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, axis + "Lo", "J"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, 31));
        il.add(new InsnNode(Opcodes.ISHR));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, axis + "Hi", "J"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    /** {@code Vec3i setLongX(long v)} - long view, sign-extended into hi. */
    private static InsnList longSetter(String axis) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 1));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, axis + "Lo", "J"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 1));
        il.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, 63));
        il.add(new InsnNode(Opcodes.LSHR));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, axis + "Hi", "J"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static MethodNode wideGetter(String name, String field) {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, name, "()J", null, null);
        mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, field, "J"));
        mn.instructions.add(new InsnNode(Opcodes.LRETURN));
        return mn;
    }

    private static MethodNode wideSetter(String name, String axis) {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, name, "(JJ)" + RET, null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 1));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, axis + "Lo", "J"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 3));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, axis + "Hi", "J"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    // ------------------------------------------------------------------
    // 2-limb arithmetic
    // ------------------------------------------------------------------

    /** A pair of limbs, emitted on demand (object field / int arg / long arg). */
    private interface Limbs {
        void lo(InsnList il);

        void hi(InsnList il);
    }

    private static Limbs fieldLimb(int slot, String axis) {
        return new Limbs() {
            @Override
            public void lo(InsnList il) {
                il.add(new VarInsnNode(Opcodes.ALOAD, slot));
                il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, axis + "Lo", "J"));
            }

            @Override
            public void hi(InsnList il) {
                il.add(new VarInsnNode(Opcodes.ALOAD, slot));
                il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, axis + "Hi", "J"));
            }
        };
    }

    private static Limbs intLimb(int slot) {
        return new Limbs() {
            @Override
            public void lo(InsnList il) {
                il.add(new VarInsnNode(Opcodes.ILOAD, slot));
                il.add(new InsnNode(Opcodes.I2L));
            }

            @Override
            public void hi(InsnList il) {
                il.add(new VarInsnNode(Opcodes.ILOAD, slot));
                il.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, 31));
                il.add(new InsnNode(Opcodes.ISHR));
                il.add(new InsnNode(Opcodes.I2L));
            }
        };
    }

    private static Limbs longLimb(int slot) {
        return longPairLimb(slot, -1);
    }

    private static Limbs longPairLimb(int loSlot, int hiSlot) {
        return new Limbs() {
            @Override
            public void lo(InsnList il) {
                il.add(new VarInsnNode(Opcodes.LLOAD, loSlot));
            }

            @Override
            public void hi(InsnList il) {
                if (hiSlot >= 0) {
                    il.add(new VarInsnNode(Opcodes.LLOAD, hiSlot));
                } else {
                    il.add(new VarInsnNode(Opcodes.LLOAD, loSlot));
                    il.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, 63));
                    il.add(new InsnNode(Opcodes.LSHR));
                }
            }
        };
    }

    /** Leaves {@code [lo, hi]} of {@code a + b} (unsigned carry). */
    private static void emitAdd(InsnList il, Limbs a, Limbs b) {
        a.lo(il);
        b.lo(il);
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new InsnNode(Opcodes.DUP2));
        a.lo(il);
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long",
            "compareUnsigned", "(JJ)I", false));
        LabelNode noCarry = new LabelNode();
        LabelNode end = new LabelNode();
        il.add(new JumpInsnNode(Opcodes.IFGE, noCarry));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new JumpInsnNode(Opcodes.GOTO, end));
        il.add(noCarry);
        il.add(new InsnNode(Opcodes.LCONST_0));
        il.add(end);
        a.hi(il);
        b.hi(il);
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new InsnNode(Opcodes.LADD));
    }

    /** Leaves {@code [lo, hi]} of {@code a - b} (unsigned borrow). */
    private static void emitSub(InsnList il, Limbs a, Limbs b, int loSlot, int borrowSlot, int hiSlot) {
        a.lo(il);
        b.lo(il);
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new VarInsnNode(Opcodes.LSTORE, loSlot));

        a.lo(il);
        b.lo(il);
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long",
            "compareUnsigned", "(JJ)I", false));
        LabelNode noBorrow = new LabelNode();
        LabelNode end = new LabelNode();
        il.add(new JumpInsnNode(Opcodes.IFGE, noBorrow));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new JumpInsnNode(Opcodes.GOTO, end));
        il.add(noBorrow);
        il.add(new InsnNode(Opcodes.LCONST_0));
        il.add(end);
        il.add(new VarInsnNode(Opcodes.LSTORE, borrowSlot));

        a.hi(il);
        b.hi(il);
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new VarInsnNode(Opcodes.LLOAD, borrowSlot));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new VarInsnNode(Opcodes.LSTORE, hiSlot));

        il.add(new VarInsnNode(Opcodes.LLOAD, loSlot));
        il.add(new VarInsnNode(Opcodes.LLOAD, hiSlot));
    }

    /**
     * Leaves {@code [lo, hi]} of the truncated 128-bit product {@code a * b}:
     * {@code hi = multiplyHigh(aLo,bLo) + aHi*bLo + aLo*bHi}.
     */
    private static void emitMul(InsnList il, Limbs a, Limbs b, int loSlot, int hiSlot, boolean push) {
        a.lo(il);
        b.lo(il);
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new VarInsnNode(Opcodes.LSTORE, loSlot));

        a.lo(il);
        b.lo(il);
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math",
            "multiplyHigh", "(JJ)J", false));
        a.hi(il);
        b.lo(il);
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new InsnNode(Opcodes.LADD));
        a.lo(il);
        b.hi(il);
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, hiSlot));

        if (push) {
            il.add(new VarInsnNode(Opcodes.LLOAD, loSlot));
            il.add(new VarInsnNode(Opcodes.LLOAD, hiSlot));
        }
    }

    private static InsnList offsetInts() {
        InsnList body = new InsnList();
        for (int i = 0; i < 3; i++) {
            emitAdd(body, fieldLimb(0, AXIS[i]), intLimb(i + 1));
        }
        return body;
    }

    private static InsnList offsetVec() {
        InsnList body = new InsnList();
        for (int i = 0; i < 3; i++) {
            emitAdd(body, fieldLimb(0, AXIS[i]), fieldLimb(1, AXIS[i]));
        }
        return body;
    }

    private static InsnList subtractVec() {
        InsnList body = new InsnList();
        for (int i = 0; i < 3; i++) {
            emitSub(body, fieldLimb(0, AXIS[i]), fieldLimb(1, AXIS[i]), 2, 4, 6);
        }
        return body;
    }

    private static InsnList multiplyInt() {
        InsnList body = new InsnList();
        body.add(new VarInsnNode(Opcodes.ILOAD, 1));
        body.add(new InsnNode(Opcodes.I2L));
        body.add(new VarInsnNode(Opcodes.LSTORE, 2));
        for (int i = 0; i < 3; i++) {
            emitMul(body, fieldLimb(0, AXIS[i]), longLimb(2), 4, 6, true);
        }
        return body;
    }

    private static InsnList relativeDir() {
        InsnList body = new InsnList();
        for (int i = 0; i < 3; i++) {
            body.add(new VarInsnNode(Opcodes.ALOAD, 1));
            body.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Direction",
                "getStep" + AXIS_U[i], "()I", false));
            body.add(new InsnNode(Opcodes.I2L));
            body.add(new VarInsnNode(Opcodes.ILOAD, 2));
            body.add(new InsnNode(Opcodes.I2L));
            body.add(new InsnNode(Opcodes.LMUL));
            body.add(new VarInsnNode(Opcodes.LSTORE, 3));
            body.add(new VarInsnNode(Opcodes.LLOAD, 3));
            body.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, 63));
            body.add(new InsnNode(Opcodes.LSHR));
            body.add(new VarInsnNode(Opcodes.LSTORE, 5));
            emitAdd(body, fieldLimb(0, AXIS[i]), longPairLimb(3, 5));
        }
        return body;
    }

    private static InsnList crossVec() {
        String[] selfA = {"y", "z", "x"};
        String[] selfB = {"z", "x", "y"};
        String[] otherA = {"z", "x", "y"};
        String[] otherB = {"y", "z", "x"};
        InsnList body = new InsnList();
        for (int i = 0; i < 3; i++) {
            emitMul(body, fieldLimb(0, selfA[i]), fieldLimb(1, otherA[i]), 2, 4, false);
            emitMul(body, fieldLimb(0, selfB[i]), fieldLimb(1, otherB[i]), 6, 8, false);
            emitSub(body, longPairLimb(2, 4), longPairLimb(6, 8), 10, 12, 14);
        }
        return body;
    }

    private static InsnList newVec6(InsnList body) {
        InsnList il = new InsnList();
        il.add(new TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(body);
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJJJJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    // ------------------------------------------------------------------
    // equals / hashCode / compareTo / toString
    // ------------------------------------------------------------------

    private static InsnList equalsBody() {
        InsnList il = new InsnList();
        LabelNode notSame = new LabelNode();
        LabelNode notVec = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new JumpInsnNode(Opcodes.IF_ACMPNE, notSame));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(notSame);
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new TypeInsnNode(Opcodes.INSTANCEOF, TARGET));
        il.add(new JumpInsnNode(Opcodes.IFEQ, notVec));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, TARGET));
        il.add(new VarInsnNode(Opcodes.ASTORE, 2));
        for (int i = 0; i < 3; i++) {
            for (String suffix : new String[]{"Lo", "Hi"}) {
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, AXIS[i] + suffix, "J"));
                il.add(new VarInsnNode(Opcodes.ALOAD, 2));
                il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, AXIS[i] + suffix, "J"));
                il.add(new InsnNode(Opcodes.LCMP));
                il.add(new JumpInsnNode(Opcodes.IFNE, notVec));
            }
        }
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(notVec);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    private static InsnList hashCodeBody() {
        InsnList il = new InsnList();
        il.add(new InsnNode(Opcodes.LCONST_0));
        il.add(new VarInsnNode(Opcodes.LSTORE, 1));
        for (int i = 0; i < 3; i++) {
            il.add(new VarInsnNode(Opcodes.LLOAD, 1));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, AXIS[i] + "Hi", "J"));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, AXIS[i] + "Lo", "J"));
            il.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, 63));
            il.add(new InsnNode(Opcodes.LSHR));
            il.add(new InsnNode(Opcodes.LXOR));
            il.add(new LdcInsnNode(HASH_PRIME[i]));
            il.add(new InsnNode(Opcodes.LMUL));
            il.add(new InsnNode(Opcodes.LADD));
            il.add(new VarInsnNode(Opcodes.LSTORE, 1));
        }
        // Base term identical to the previous int-view formula: integer
        // arithmetic is mod 2^32, so the long expression cast to int matches.
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "yLo", "J"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "zLo", "J"));
        il.add(new LdcInsnNode(31L));
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new LdcInsnNode(31L));
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "xLo", "J"));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ISTORE, 3));
        LabelNode canonical = new LabelNode();
        il.add(new VarInsnNode(Opcodes.LLOAD, 1));
        il.add(new InsnNode(Opcodes.LCONST_0));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFEQ, canonical));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, 31));
        il.add(new InsnNode(Opcodes.IMUL));
        il.add(new VarInsnNode(Opcodes.LLOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long", "hashCode", "(J)I", false));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(canonical);
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    private static InsnList compareToBody() {
        // Vanilla order: Y, then Z, then X.
        String[] order = {"y", "z", "x"};
        InsnList il = new InsnList();
        for (String axis : order) {
            LabelNode next = new LabelNode();
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, axis + "Lo", "J"));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, axis + "Hi", "J"));
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, axis + "Lo", "J"));
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, axis + "Hi", "J"));
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "compareWide",
                "(JJJJ)I", false));
            il.add(new InsnNode(Opcodes.DUP));
            il.add(new JumpInsnNode(Opcodes.IFEQ, next));
            il.add(new InsnNode(Opcodes.IRETURN));
            il.add(next);
        }
        il.add(new InsnNode(Opcodes.IRETURN));
        return il;
    }

    /** {@code compareWide(lo1,hi1,lo2,hi2)}: signed 128-bit compare. */
    private static MethodNode compareWideHelper() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
            "compareWide", "(JJJJ)I", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.LLOAD, 2));
        il.add(new VarInsnNode(Opcodes.LLOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long", "compare", "(JJ)I", false));
        LabelNode low = new LabelNode();
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new JumpInsnNode(Opcodes.IFEQ, low));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(low);
        il.add(new VarInsnNode(Opcodes.LLOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long",
            "compareUnsigned", "(JJ)I", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    private static InsnList toStringBody() {
        InsnList il = new InsnList();
        il.add(new TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder",
            "<init>", "()V", false));
        appendString(il, "(");
        for (int i = 0; i < 3; i++) {
            if (i > 0) {
                appendString(il, ", ");
            }
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, AXIS[i] + "Lo", "J"));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, AXIS[i] + "Hi", "J"));
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "axisString",
                "(JJ)Ljava/lang/String;", false));
            appendStringBuilder(il);
        }
        appendString(il, ")");
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
            "toString", "()Ljava/lang/String;", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    /** {@code axisString(lo,hi)}: decimal within 64 bits, hex limbs above. */
    private static MethodNode axisStringHelper() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
            "axisString", "(JJ)Ljava/lang/String;", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.LLOAD, 2));
        il.add(new VarInsnNode(Opcodes.LLOAD, 0));
        il.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, 63));
        il.add(new InsnNode(Opcodes.LSHR));
        il.add(new InsnNode(Opcodes.LCMP));
        LabelNode wide = new LabelNode();
        il.add(new JumpInsnNode(Opcodes.IFNE, wide));
        il.add(new VarInsnNode(Opcodes.LLOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long",
            "toString", "(J)Ljava/lang/String;", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        il.add(wide);
        il.add(new TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder",
            "<init>", "()V", false));
        appendString(il, "0x");
        il.add(new VarInsnNode(Opcodes.LLOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long",
            "toHexString", "(J)Ljava/lang/String;", false));
        appendStringBuilder(il);
        appendString(il, "_");
        il.add(new VarInsnNode(Opcodes.LLOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long",
            "toHexString", "(J)Ljava/lang/String;", false));
        appendStringBuilder(il);
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
            "toString", "()Ljava/lang/String;", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    private static void appendString(InsnList il, String s) {
        il.add(new LdcInsnNode(s));
        appendStringBuilder(il);
    }

    private static void appendStringBuilder(InsnList il) {
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
            "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
    }
}
