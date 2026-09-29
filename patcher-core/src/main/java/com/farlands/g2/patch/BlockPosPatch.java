package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Widens {@code BlockPos} position-deriving arithmetic to the long domain
 * and adds a (JJJ) constructor, matching the {@link Vec3iPatch} foundation.
 *
 * <p>The int accessors keep wrap semantics, so every existing consumer is
 * unchanged; wide values survive {@code offset/multiply/subtract/relative/
 * cross} and are readable through {@code getLongX/Y/Z()}.</p>
 */
public final class BlockPosPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/core/BlockPos";
    private static final String MUTABLE = "net/minecraft/core/BlockPos$MutableBlockPos";
    private static final String VEC3I = "net/minecraft/core/Vec3i";
    private static final String MUTABLE_RET = "Lnet/minecraft/core/BlockPos$MutableBlockPos;";
    private static final String SET_LONG = "(JJJ)" + MUTABLE_RET;

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName) || MUTABLE.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        if (MUTABLE.equals(node.name)) {
            // Wide constructor: BlockPos.mutable() and other value-producing
            // conversions must be able to carry the long fields. Without it
            // they fall back to the (III) constructor and the int accessors,
            // silently wrapping a reach that crosses block 2^31 (Column.scan
            // then probes two different columns and reports a negative range).
            if (find(node, "<init>", "(JJJ)V") == null) {
                node.methods.add(mutableLongConstructor());
            }
            // Reused-position setter in the long domain: store the three wide
            // fields directly. The inherited BlockPos(JJJ) constructor exists,
            // but the mutable must be updated in place.
            if (find(node, "set", SET_LONG) == null) {
                node.methods.add(longSetter());
            }
            // Route the value-producing mutators through the wide fields so a
            // feature that derived a coordinate beyond the int range (e.g. a
            // reach that crosses block 2^31) does not wrap before it is stored.
            rewrite(node, "setWithOffset",
                "(Lnet/minecraft/core/Vec3i;Lnet/minecraft/core/Direction;)" + MUTABLE_RET,
                setWithOffsetDir());
            rewrite(node, "setWithOffset",
                "(Lnet/minecraft/core/Vec3i;III)" + MUTABLE_RET,
                setWithOffsetInts());
            rewrite(node, "setWithOffset",
                "(Lnet/minecraft/core/Vec3i;Lnet/minecraft/core/Vec3i;)" + MUTABLE_RET,
                setWithOffsetVec());
            rewrite(node, "set", "(Lnet/minecraft/core/Vec3i;)" + MUTABLE_RET, setVec());
            rewrite(node, "move", "(III)" + MUTABLE_RET, moveInts());
            rewrite(node, "move", "(Lnet/minecraft/core/Direction;I)" + MUTABLE_RET, moveDir());
            rewrite(node, "move", "(Lnet/minecraft/core/Vec3i;)" + MUTABLE_RET, moveVec());
            ClassWriter mw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
            node.accept(mw);
            return mw.toByteArray();
        }

        if (find(node, "<init>", "(JJJ)V") == null) {
            node.methods.add(longConstructor());
            rewrite(node, "offset", "(III)Lnet/minecraft/core/BlockPos;",
                newInstance("offset", new int[]{0, 1, 2}));
            rewrite(node, "multiply", "(I)Lnet/minecraft/core/BlockPos;",
                newInstance("multiply", new int[]{0, 1, 2}));
            rewrite(node, "subtract", "(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/core/BlockPos;",
                subtractBody());
            rewrite(node, "relative", "(Lnet/minecraft/core/Direction;I)Lnet/minecraft/core/BlockPos;",
                relativeBody());
            rewrite(node, "cross", "(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/core/BlockPos;",
                crossBody());
            // Value-producing conversions that still went through the int
            // accessors. `mutable()` is the Column.scan path (the initial
            // predicate probe uses the wide BlockPos while the directional
            // scans use the int-truncated mutable, so at the 2^31 seam they
            // read two different columns and Column$Range throws "negative
            // height"); `atY`/`relative` carry the wide X/Z through the
            // speleothem write path. At normal coordinates the long equals the
            // int, so all three are identity.
            rewrite(node, "mutable", "()Lnet/minecraft/core/BlockPos$MutableBlockPos;",
                mutableBody());
            rewrite(node, "atY", "(I)Lnet/minecraft/core/BlockPos;", atYBody());
            rewrite(node, "relative", "(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;",
                relativeOneBody());
            // The copy constructor is the truncation point of every
            // value-producing conversion that still routes through
            // `new BlockPos(vec)`, most importantly
            // MutableBlockPos.immutable() (VegetationPatchFeature stores the
            // ground column it returns in a Set and places at it later).
            // Copy the widened fields directly; at normal coordinates the long
            // equals the int getter it replaced.
            rewrite(node, "<init>", "(Lnet/minecraft/core/Vec3i;)V", vecCopyBody());
        }
        // betweenClosed(a, b) computes its extents with the int getters, so two
        // corners that straddle block 2^31 (a = origin-r, b = origin+r with
        // origin = 2^31) produce a span that overflows int and an iterator over
        // billions of positions (DiskFeature spins until the watchdog kills the
        // tick). Compute the extents in the wide domain and clamp to the int
        // range; at normal coordinates this is identity.
        rewrite(node, "betweenClosed",
            "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Ljava/lang/Iterable;",
            betweenClosedWide());

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    /**
     * {@code MutableBlockPos.set(long,long,long)}: writes the three widened
     * {@code Vec3i} fields through {@code Vec3i.setLongX/Y/Z}. The int
     * {@code set(III)} keeps its wrap semantics; this is the wide path used by
     * the ore vein loop.
     */
    private static MethodNode longSetter() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "set",
            "(JJJ)Lnet/minecraft/core/BlockPos$MutableBlockPos;", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "setLongX", "(J)Lnet/minecraft/core/Vec3i;", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "setLongY", "(J)Lnet/minecraft/core/Vec3i;", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "setLongZ", "(J)Lnet/minecraft/core/Vec3i;", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    /**
     * Builds {@code this.set(x, y, z)} where the three long values are pushed
     * by the given emitters against {@code this} (slot 0).
     */
    private static InsnList setCall(AxisEmitter x, AxisEmitter y, AxisEmitter z) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        x.emit(il);
        y.emit(il);
        z.emit(il);
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MUTABLE, "set", SET_LONG, false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    @FunctionalInterface
    private interface AxisEmitter {
        void emit(InsnList il);
    }

    /** {@code v.getLongA() + delta} where delta is an int local / constant. */
    private static AxisEmitter argLong(String axis, int slot) {
        return il -> {
            il.add(new VarInsnNode(Opcodes.ALOAD, slot));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I, "getLong" + axis, "()J", false));
        };
    }

    private static AxisEmitter addInt(AxisEmitter base, int slot) {
        return il -> {
            base.emit(il);
            il.add(new VarInsnNode(Opcodes.ILOAD, slot));
            il.add(new InsnNode(Opcodes.I2L));
            il.add(new InsnNode(Opcodes.LADD));
        };
    }

    private static AxisEmitter addStep(AxisEmitter base, String stepAxis, int dirSlot, int amountSlot) {
        return il -> {
            base.emit(il);
            il.add(new VarInsnNode(Opcodes.ALOAD, dirSlot));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Direction",
                "getStep" + stepAxis, "()I", false));
            il.add(new InsnNode(Opcodes.I2L));
            il.add(new VarInsnNode(Opcodes.ILOAD, amountSlot));
            il.add(new InsnNode(Opcodes.I2L));
            il.add(new InsnNode(Opcodes.LMUL));
            il.add(new InsnNode(Opcodes.LADD));
        };
    }

    /** {@code base + direction.getStepA()} (single step, no distance). */
    private static AxisEmitter addStepOnce(AxisEmitter base, String stepAxis, int dirSlot) {
        return il -> {
            base.emit(il);
            il.add(new VarInsnNode(Opcodes.ALOAD, dirSlot));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Direction",
                "getStep" + stepAxis, "()I", false));
            il.add(new InsnNode(Opcodes.I2L));
            il.add(new InsnNode(Opcodes.LADD));
        };
    }

    private static InsnList setWithOffsetDir() {
        return setCall(addStepOnce(argLong("X", 1), "X", 2),
            addStepOnce(argLong("Y", 1), "Y", 2), addStepOnce(argLong("Z", 1), "Z", 2));
    }

    private static InsnList setWithOffsetInts() {
        return setCall(addInt(argLong("X", 1), 2), addInt(argLong("Y", 1), 3),
            addInt(argLong("Z", 1), 4));
    }

    private static InsnList setWithOffsetVec() {
        return setCall(addVec(argLong("X", 1), "X", 2), addVec(argLong("Y", 1), "Y", 2),
            addVec(argLong("Z", 1), "Z", 2));
    }

    private static AxisEmitter addVec(AxisEmitter base, String axis, int slot) {
        return il -> {
            base.emit(il);
            il.add(new VarInsnNode(Opcodes.ALOAD, slot));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I, "getLong" + axis, "()J", false));
            il.add(new InsnNode(Opcodes.LADD));
        };
    }

    private static InsnList setVec() {
        return setCall(argLong("X", 1), argLong("Y", 1), argLong("Z", 1));
    }

    private static InsnList moveInts() {
        return setCall(addInt(selfLong("X"), 1), addInt(selfLong("Y"), 2),
            addInt(selfLong("Z"), 3));
    }

    private static InsnList moveDir() {
        return setCall(addStep(selfLong("X"), "X", 1, 2), addStep(selfLong("Y"), "Y", 1, 2),
            addStep(selfLong("Z"), "Z", 1, 2));
    }

    private static InsnList moveVec() {
        return setCall(addVec(selfLong("X"), "X", 1), addVec(selfLong("Y"), "Y", 1),
            addVec(selfLong("Z"), "Z", 1));
    }

    private static AxisEmitter selfLong(String axis) {
        return il -> {
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I, "getLong" + axis, "()J", false));
        };
    }

    /**
     * Wide-safe {@code betweenClosed(BlockPos, BlockPos)}: min/max of the two
     * corners in long, each clamped into the int range, then delegated to the
     * vanilla int iterator. At normal coords the long min/max equal the int
     * ones, so the produced box (and its iteration order) is unchanged.
     */
    private static InsnList betweenClosedWide() {
        InsnList il = new InsnList();
        for (int pass = 0; pass < 2; pass++) {
            boolean min = pass == 0;
            for (String axis : new String[]{"X", "Y", "Z"}) {
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I, "getLong" + axis, "()J", false));
                il.add(new VarInsnNode(Opcodes.ALOAD, 1));
                il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I, "getLong" + axis, "()J", false));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", min ? "min" : "max", "(JJ)J", false));
                il.add(new LdcInsnNode(Integer.MIN_VALUE));
                il.add(new LdcInsnNode(Integer.MAX_VALUE));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "clamp", "(JII)I", false));
            }
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "betweenClosed",
            "(IIIIII)Ljava/lang/Iterable;", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static MethodNode longConstructor() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(JJJ)V", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 1));
        il.add(new VarInsnNode(Opcodes.LLOAD, 3));
        il.add(new VarInsnNode(Opcodes.LLOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "net/minecraft/core/Vec3i", "<init>", "(JJJ)V", false));
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    private static InsnList newInstance(String op, int[] axes) {
        InsnList il = new InsnList();
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        for (int i = 0; i < 3; i++) {
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i",
                "getLong" + "XYZ".substring(axes[i], axes[i] + 1), "()J", false));
            if ("multiply".equals(op)) {
                il.add(new VarInsnNode(Opcodes.ILOAD, 1));
                il.add(new InsnNode(Opcodes.I2L));
                il.add(new InsnNode(Opcodes.LMUL));
            } else {
                il.add(new VarInsnNode(Opcodes.ILOAD, i + 1));
                il.add(new InsnNode(Opcodes.I2L));
                il.add(new InsnNode(Opcodes.LADD));
            }
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static InsnList subtractBody() {
        InsnList il = new InsnList();
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        for (int i = 0; i < 3; i++) {
            String axis = "XYZ".substring(i, i + 1);
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLong" + axis, "()J", false));
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLong" + axis, "()J", false));
            il.add(new InsnNode(Opcodes.LSUB));
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static InsnList relativeBody() {
        InsnList il = new InsnList();
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        for (int i = 0; i < 3; i++) {
            String axis = "XYZ".substring(i, i + 1);
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLong" + axis, "()J", false));
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Direction",
                "getStep" + axis, "()I", false));
            il.add(new InsnNode(Opcodes.I2L));
            il.add(new VarInsnNode(Opcodes.ILOAD, 2));
            il.add(new InsnNode(Opcodes.I2L));
            il.add(new InsnNode(Opcodes.LMUL));
            il.add(new InsnNode(Opcodes.LADD));
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    /**
     * {@code new MutableBlockPos(getLongX(), getLongY(), getLongZ())}. The
     * wide constructor keeps a reach beyond the int range intact instead of
     * truncating it through {@code getX()}.
     */
    private static InsnList mutableBody() {
        InsnList il = new InsnList();
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, MUTABLE));
        il.add(new InsnNode(Opcodes.DUP));
        for (String axis : new String[]{"X", "Y", "Z"}) {
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I, "getLong" + axis, "()J", false));
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, MUTABLE, "<init>", "(JJJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    /** {@code new BlockPos(getLongX(), (long) y, getLongZ())} (wide X/Z). */
    private static InsnList atYBody() {
        InsnList il = new InsnList();
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I, "getLongX", "()J", false));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I, "getLongZ", "()J", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    /** Single-step {@code relative(Direction)} in the wide domain. */
    private static InsnList relativeOneBody() {
        InsnList il = new InsnList();
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        for (String axis : new String[]{"X", "Y", "Z"}) {
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I, "getLong" + axis, "()J", false));
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Direction",
                "getStep" + axis, "()I", false));
            il.add(new InsnNode(Opcodes.I2L));
            il.add(new InsnNode(Opcodes.LADD));
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    /**
     * {@code this(v.getLongX(), v.getLongY(), v.getLongZ())}: the copy
     * constructor keeps the widened coordinates instead of re-reading the
     * wrapped int getters.
     */
    private static InsnList vecCopyBody() {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        for (String axis : new String[]{"X", "Y", "Z"}) {
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I, "getLong" + axis, "()J", false));
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJ)V", false));
        il.add(new InsnNode(Opcodes.RETURN));
        return il;
    }

    /** {@code MutableBlockPos(JJJ)} delegating to {@code BlockPos(JJJ)}. */
    private static MethodNode mutableLongConstructor() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(JJJ)V", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 1));
        il.add(new VarInsnNode(Opcodes.LLOAD, 3));
        il.add(new VarInsnNode(Opcodes.LLOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJ)V", false));
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    private static InsnList crossBody() {
        InsnList il = new InsnList();
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongY", "()J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongZ", "()J", false));
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongZ", "()J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongY", "()J", false));
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongZ", "()J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongX", "()J", false));
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongX", "()J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongZ", "()J", false));
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongX", "()J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongY", "()J", false));
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongY", "()J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Vec3i", "getLongX", "()J", false));
        il.add(new InsnNode(Opcodes.LMUL));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(JJJ)V", false));
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
}
