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
import org.objectweb.asm.tree.VarInsnNode;

/**
 * W4-2 slice 2a: wide-node shadow API for the ticket/POI distance graph core.
 *
 * <p>{@code DynamicGraphMinFixedPoint} serves both the chunk trackers and the
 * lighting engines. The lighting subclasses key nodes by 64-bit packed block
 * positions, so changing the vanilla {@code long} signatures would drag the
 * whole lighting chain into the widening (explicitly out of scope). Instead a
 * complete wide twin of the algorithm is injected: same state machine, but
 * nodes are {@code com.scalarwide.Wide512} chunk keys and the maps/queues are
 * the ScalarWide mc structures {@code Wide512ByteMap} and
 * {@code Wide512OrderedSet} (the 68-line {@code LeveledPriorityQueue} logic is
 * inlined here, so that class stays untouched for lighting).</p>
 *
 * <p>The wide hooks ({@code farlands$isSourceW}, {@code farlands$getLevelW},
 * {@code farlands$setLevelW}, {@code farlands$getComputedLevelW},
 * {@code farlands$computeLevelFromNeighborW},
 * {@code farlands$checkNeighborsAfterUpdateW}) default to benign bodies and are
 * overridden by {@code ChunkTrackerWidePatch}. The wide path is dormant until
 * the request-entry slices wire it up; for the int domain every wide value is
 * bit-identical to the long one.</p>
 */
public final class DynamicGraphMinFixedPointWidePatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/lighting/DynamicGraphMinFixedPoint";
    private static final String W512 = "com/scalarwide/Wide512";
    private static final String W512_D = "Lcom/scalarwide/Wide512;";
    private static final String SET = "com/scalarwide/mc/Wide512OrderedSet";
    private static final String SET_D = "Lcom/scalarwide/mc/Wide512OrderedSet;";
    private static final String MAP = "com/scalarwide/mc/Wide512ByteMap";
    private static final String MAP_D = "Lcom/scalarwide/mc/Wide512ByteMap;";
    private static final String MTH = "net/minecraft/util/Mth";

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
        if (node.methods.stream().anyMatch(m -> "farlands$runUpdatesW".equals(m.name))) {
            return original;
        }

        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "farlands$queueW", "[" + SET_D, null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "farlands$computedLevelsW", MAP_D, null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "farlands$firstQueuedLevelW", "I", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_VOLATILE, "farlands$hasWorkW", "Z", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "farlands$sourceW", W512_D, null, null));

        injectCtorInit(node);

        node.methods.add(initWide());
        node.methods.add(enqueueW());
        node.methods.add(dequeueW());
        node.methods.add(removeFirstW());
        node.methods.add(checkFirstQueuedLevelW());
        node.methods.add(isQueueEmptyW());
        node.methods.add(calculatePriorityW());
        node.methods.add(removeFromQueueW());
        node.methods.add(removeIfW());
        node.methods.add(checkNodeW());
        node.methods.add(checkEdgeW4());
        node.methods.add(checkEdgeW6());
        node.methods.add(checkNeighborW());
        node.methods.add(runUpdatesW());
        node.methods.add(getQueueSizeW());
        node.methods.add(hasWideWork());
        node.methods.add(sourceSentinelW());
        node.methods.add(isSourceW());
        node.methods.add(getComputedLevelW());
        node.methods.add(checkNeighborsAfterUpdateW());
        node.methods.add(getLevelW());
        node.methods.add(setLevelW());
        node.methods.add(computeLevelFromNeighborW());

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static MethodNode m(int access, String name, String desc) {
        return new MethodNode(access, name, desc, null, null);
    }

    private static void getf(InsnList il, String owner, String name, String desc) {
        il.add(new FieldInsnNode(Opcodes.GETFIELD, owner, name, desc));
    }

    private static void putf(InsnList il, String owner, String name, String desc) {
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, owner, name, desc));
    }

    private static void getLevelCount(InsnList il) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "levelCount", "I");
    }

    private static void getLevelCountMinus1(InsnList il) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "levelCount", "I");
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.ISUB));
    }

    /** {@code Mth.clamp(value, 0, this.levelCount - 1)} where value is on the stack. */
    private static void clampLevel(InsnList il) {
        il.add(new InsnNode(Opcodes.ICONST_0));
        getLevelCountMinus1(il);
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MTH, "clamp", "(III)I", false));
    }

    private static void pushInt(InsnList il, int v) {
        if (v >= -1 && v <= 5) {
            il.add(new InsnNode(Opcodes.ICONST_0 + v));
        } else if (v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE) {
            il.add(new IntInsnNode(Opcodes.BIPUSH, v));
        } else if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE) {
            il.add(new IntInsnNode(Opcodes.SIPUSH, v));
        } else {
            il.add(new LdcInsnNode(v));
        }
    }

    private static void putHasWorkW(InsnList il) {
        LabelNode falseL = new LabelNode();
        LabelNode setL = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "farlands$isQueueEmptyW", "()Z", false));
        il.add(new JumpInsnNode(Opcodes.IFNE, falseL));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new JumpInsnNode(Opcodes.GOTO, setL));
        il.add(falseL);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(setL);
        putf(il, TARGET, "farlands$hasWorkW", "Z");
    }

    private static void injectCtorInit(ClassNode node) {
        MethodNode ctor = null;
        for (MethodNode mn : node.methods) {
            if ("<init>".equals(mn.name) && "(III)V".equals(mn.desc)) {
                ctor = mn;
                break;
            }
        }
        if (ctor == null) {
            throw new IllegalStateException(TARGET + " constructor (III)V not found");
        }
        AbstractInsnNode ret = ctor.instructions.getLast();
        while (ret != null && !(ret instanceof InsnNode && ret.getOpcode() == Opcodes.RETURN)) {
            ret = ret.getPrevious();
        }
        if (ret == null) {
            throw new IllegalStateException(TARGET + " constructor RETURN not found");
        }
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "farlands$initWide", "(I)V", false));
        ctor.instructions.insertBefore(ret, il);
    }

    // ------------------------------------------------------------------
    // wide state init + queue logic (LeveledPriorityQueue inlined)
    // ------------------------------------------------------------------

    private static MethodNode initWide() {
        MethodNode mn = m(Opcodes.ACC_PRIVATE, "farlands$initWide", "(I)V");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getLevelCount(il);
        putf(il, TARGET, "farlands$firstQueuedLevelW", "I");
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getLevelCount(il);
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.ANEWARRAY, SET));
        putf(il, TARGET, "farlands$queueW", "[" + SET_D);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 2));
        LabelNode loop = new LabelNode();
        LabelNode end = new LabelNode();
        il.add(loop);
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        getLevelCount(il);
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGE, end));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$queueW", "[" + SET_D);
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, SET));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, SET, "<init>", "()V", false));
        il.add(new InsnNode(Opcodes.AASTORE));
        il.add(new org.objectweb.asm.tree.IincInsnNode(2, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, loop));
        il.add(end);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, MAP));
        il.add(new InsnNode(Opcodes.DUP));
        pushInt(il, 16);
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(II)I", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, MAP, "<init>", "(I)V", false));
        putf(il, TARGET, "farlands$computedLevelsW", MAP_D);
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    private static MethodNode enqueueW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL, "farlands$enqueueW", "(" + W512_D + "I)V");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$queueW", "[" + SET_D);
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new InsnNode(Opcodes.AALOAD));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SET, "add", "(" + W512_D + ")Z", false));
        il.add(new InsnNode(Opcodes.POP));
        LabelNode skip = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$firstQueuedLevelW", "I");
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPLE, skip));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        putf(il, TARGET, "farlands$firstQueuedLevelW", "I");
        il.add(skip);
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    private static MethodNode dequeueW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL, "farlands$dequeueW", "(" + W512_D + "II)V");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$queueW", "[" + SET_D);
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new InsnNode(Opcodes.AALOAD));
        il.add(new VarInsnNode(Opcodes.ASTORE, 4));
        LabelNode end = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SET, "remove", "(" + W512_D + ")Z", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SET, "isEmpty", "()Z", false));
        il.add(new JumpInsnNode(Opcodes.IFNE, end));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$firstQueuedLevelW", "I");
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPNE, end));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "farlands$checkFirstQueuedLevelW", "(I)V", false));
        il.add(end);
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    private static MethodNode removeFirstW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL, "farlands$removeFirstLongW", "()" + W512_D);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$queueW", "[" + SET_D);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$firstQueuedLevelW", "I");
        il.add(new InsnNode(Opcodes.AALOAD));
        il.add(new VarInsnNode(Opcodes.ASTORE, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SET, "removeFirst", "()" + W512_D, false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 2));
        LabelNode skip = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SET, "isEmpty", "()Z", false));
        il.add(new JumpInsnNode(Opcodes.IFNE, skip));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getLevelCount(il);
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "farlands$checkFirstQueuedLevelW", "(I)V", false));
        il.add(skip);
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    private static MethodNode checkFirstQueuedLevelW() {
        MethodNode mn = m(Opcodes.ACC_PRIVATE, "farlands$checkFirstQueuedLevelW", "(I)V");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$firstQueuedLevelW", "I");
        il.add(new VarInsnNode(Opcodes.ISTORE, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        putf(il, TARGET, "farlands$firstQueuedLevelW", "I");
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new VarInsnNode(Opcodes.ISTORE, 3));
        LabelNode cond = new LabelNode();
        LabelNode next = new LabelNode();
        LabelNode end = new LabelNode();
        il.add(cond);
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGE, end));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$queueW", "[" + SET_D);
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new InsnNode(Opcodes.AALOAD));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SET, "isEmpty", "()Z", false));
        il.add(new JumpInsnNode(Opcodes.IFNE, next));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        putf(il, TARGET, "farlands$firstQueuedLevelW", "I");
        il.add(new JumpInsnNode(Opcodes.GOTO, end));
        il.add(next);
        il.add(new org.objectweb.asm.tree.IincInsnNode(3, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, cond));
        il.add(end);
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    private static MethodNode isQueueEmptyW() {
        MethodNode mn = m(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "farlands$isQueueEmptyW", "()Z");
        InsnList il = mn.instructions;
        LabelNode yes = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$firstQueuedLevelW", "I");
        getLevelCount(il);
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGE, yes));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(yes);
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    private static MethodNode calculatePriorityW() {
        MethodNode mn = m(Opcodes.ACC_PRIVATE, "farlands$calculatePriorityW", "(II)I");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(II)I", false));
        getLevelCountMinus1(il);
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(II)I", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    // ------------------------------------------------------------------
    // algorithm mirror (Wide512 nodes)
    // ------------------------------------------------------------------

    private static MethodNode removeFromQueueW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL, "farlands$removeFromQueueW", "(" + W512_D + ")V");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$computedLevelsW", MAP_D);
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new InsnNode(Opcodes.ICONST_M1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MAP, "remove", "(" + W512_D + "B)B", false));
        pushInt(il, 255);
        il.add(new InsnNode(Opcodes.IAND));
        il.add(new VarInsnNode(Opcodes.ISTORE, 2));
        LabelNode end = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        pushInt(il, 255);
        il.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, end));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$getLevelW", "(" + W512_D + ")I", false));
        il.add(new VarInsnNode(Opcodes.ISTORE, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "farlands$calculatePriorityW", "(II)I", false));
        il.add(new VarInsnNode(Opcodes.ISTORE, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        getLevelCount(il);
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$dequeueW", "(" + W512_D + "II)V", false));
        putHasWorkW(il);
        il.add(end);
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    private static MethodNode removeIfW() {
        MethodNode mn = m(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "farlands$removeIfW", "(Ljava/util/function/Predicate;)V");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$computedLevelsW", MAP_D);
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MAP, "keys", "()Ljava/util/List;", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 2));
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, "java/util/ArrayList"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 3));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 4));
        LabelNode c1 = new LabelNode();
        LabelNode b1 = new LabelNode();
        LabelNode n1 = new LabelNode();
        il.add(c1);
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "size", "()I", true));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGE, b1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true));
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.CHECKCAST, W512));
        il.add(new VarInsnNode(Opcodes.ASTORE, 5));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/function/Predicate", "test", "(Ljava/lang/Object;)Z", true));
        il.add(new JumpInsnNode(Opcodes.IFEQ, n1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true));
        il.add(new InsnNode(Opcodes.POP));
        il.add(n1);
        il.add(new org.objectweb.asm.tree.IincInsnNode(4, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, c1));
        il.add(b1);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 6));
        LabelNode c2 = new LabelNode();
        LabelNode end = new LabelNode();
        il.add(c2);
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "size", "()I", true));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGE, end));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true));
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.CHECKCAST, W512));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$removeFromQueueW", "(" + W512_D + ")V", false));
        il.add(new org.objectweb.asm.tree.IincInsnNode(6, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, c2));
        il.add(end);
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    private static MethodNode checkNodeW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL, "farlands$checkNodeW", "(" + W512_D + ")V");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        getLevelCountMinus1(il);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$checkEdgeW",
            "(" + W512_D + W512_D + "IZ)V", false));
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    private static MethodNode checkEdgeW4() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL, "farlands$checkEdgeW",
            "(" + W512_D + W512_D + "IZ)V");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$getLevelW", "(" + W512_D + ")I", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$computedLevelsW", MAP_D);
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new InsnNode(Opcodes.ICONST_M1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MAP, "getOrDefault", "(" + W512_D + "B)B", false));
        pushInt(il, 255);
        il.add(new InsnNode(Opcodes.IAND));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "farlands$checkEdgeW",
            "(" + W512_D + W512_D + "IIIZ)V", false));
        putHasWorkW(il);
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    private static MethodNode checkEdgeW6() {
        MethodNode mn = m(Opcodes.ACC_PRIVATE, "farlands$checkEdgeW",
            "(" + W512_D + W512_D + "IIIZ)V");
        InsnList il = mn.instructions;
        LabelNode end = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$isSourceW", "(" + W512_D + ")Z", false));
        il.add(new JumpInsnNode(Opcodes.IFNE, end));

        // newLevelFrom = clamp(newLevelFrom, 0, levelCount-1)
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        clampLevel(il);
        il.add(new VarInsnNode(Opcodes.ISTORE, 3));
        // levelTo = clamp(levelTo, 0, levelCount-1)
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        clampLevel(il);
        il.add(new VarInsnNode(Opcodes.ISTORE, 4));
        // boolean wasConsistent = oldComputedLevel == 255;
        LabelNode wcTrue = new LabelNode();
        LabelNode wcDone = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        pushInt(il, 255);
        il.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, wcTrue));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new JumpInsnNode(Opcodes.GOTO, wcDone));
        il.add(wcTrue);
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(wcDone);
        il.add(new VarInsnNode(Opcodes.ISTORE, 7));
        // if (wasConsistent) oldComputedLevel = levelTo;
        LabelNode notConsistent = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ILOAD, 7));
        il.add(new JumpInsnNode(Opcodes.IFEQ, notConsistent));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new VarInsnNode(Opcodes.ISTORE, 5));
        il.add(notConsistent);

        // int newComputedLevel
        LabelNode notDecreased = new LabelNode();
        LabelNode calcDone = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        il.add(new JumpInsnNode(Opcodes.IFEQ, notDecreased));
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(II)I", false));
        il.add(new VarInsnNode(Opcodes.ISTORE, 8));
        il.add(new JumpInsnNode(Opcodes.GOTO, calcDone));
        il.add(notDecreased);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$getComputedLevelW",
            "(" + W512_D + W512_D + "I)I", false));
        clampLevel(il);
        il.add(new VarInsnNode(Opcodes.ISTORE, 8));
        il.add(calcDone);

        // int oldPriority = calculatePriority(levelTo, oldComputedLevel);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "farlands$calculatePriorityW", "(II)I", false));
        il.add(new VarInsnNode(Opcodes.ISTORE, 9));

        // if (levelTo != newComputedLevel) { ... } else if (!wasConsistent) { ... }
        LabelNode elseBranch = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, elseBranch));

        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "farlands$calculatePriorityW", "(II)I", false));
        il.add(new VarInsnNode(Opcodes.ISTORE, 10));
        LabelNode skipDequeue = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ILOAD, 9));
        il.add(new VarInsnNode(Opcodes.ILOAD, 10));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, skipDequeue));
        il.add(new VarInsnNode(Opcodes.ILOAD, 7));
        il.add(new JumpInsnNode(Opcodes.IFNE, skipDequeue));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 9));
        il.add(new VarInsnNode(Opcodes.ILOAD, 10));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$dequeueW", "(" + W512_D + "II)V", false));
        il.add(skipDequeue);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 10));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$enqueueW", "(" + W512_D + "I)V", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$computedLevelsW", MAP_D);
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new InsnNode(Opcodes.I2B));
        il.add(new InsnNode(Opcodes.ICONST_M1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MAP, "put", "(" + W512_D + "BB)B", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new JumpInsnNode(Opcodes.GOTO, end));
        il.add(elseBranch);
        il.add(new VarInsnNode(Opcodes.ILOAD, 7));
        il.add(new JumpInsnNode(Opcodes.IFNE, end));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 9));
        getLevelCount(il);
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$dequeueW", "(" + W512_D + "II)V", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$computedLevelsW", MAP_D);
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new InsnNode(Opcodes.ICONST_M1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MAP, "remove", "(" + W512_D + "B)B", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(end);
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    private static MethodNode checkNeighborW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL, "farlands$checkNeighborW",
            "(" + W512_D + W512_D + "IZ)V");
        InsnList il = mn.instructions;
        // int storedOldComputedLevel = computedLevels.get(to) & 255
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$computedLevelsW", MAP_D);
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new InsnNode(Opcodes.ICONST_M1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MAP, "getOrDefault", "(" + W512_D + "B)B", false));
        pushInt(il, 255);
        il.add(new InsnNode(Opcodes.IAND));
        il.add(new VarInsnNode(Opcodes.ISTORE, 5));
        // int levelFrom = clamp(computeLevelFromNeighbor(from, to, level), 0, levelCount-1)
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$computeLevelFromNeighborW",
            "(" + W512_D + W512_D + "I)I", false));
        clampLevel(il);
        il.add(new VarInsnNode(Opcodes.ISTORE, 6));

        LabelNode elseBranch = new LabelNode();
        LabelNode end = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new JumpInsnNode(Opcodes.IFEQ, elseBranch));
        // checkEdge(from, to, levelFrom, getLevel(to), storedOld, onlyDecreased=true)
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$getLevelW", "(" + W512_D + ")I", false));
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$checkEdgeW", "(" + W512_D + W512_D + "IIIZ)V", false));
        il.add(new JumpInsnNode(Opcodes.GOTO, end));

        il.add(elseBranch);
        // boolean wasConsistent = storedOld == 255
        LabelNode wcTrue = new LabelNode();
        LabelNode wcDone = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        pushInt(il, 255);
        il.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, wcTrue));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new JumpInsnNode(Opcodes.GOTO, wcDone));
        il.add(wcTrue);
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(wcDone);
        il.add(new VarInsnNode(Opcodes.ISTORE, 7));
        // int oldComputedLevel
        LabelNode useStored = new LabelNode();
        LabelNode ocDone = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ILOAD, 7));
        il.add(new JumpInsnNode(Opcodes.IFEQ, useStored));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$getLevelW", "(" + W512_D + ")I", false));
        clampLevel(il);
        il.add(new VarInsnNode(Opcodes.ISTORE, 8));
        il.add(new JumpInsnNode(Opcodes.GOTO, ocDone));
        il.add(useStored);
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new VarInsnNode(Opcodes.ISTORE, 8));
        il.add(ocDone);
        // if (levelFrom == oldComputedLevel) checkEdge(from,to,levelCount-1, wasConsistent?old:getLevel(to), storedOld, onlyDecreased)
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPNE, end));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        getLevelCountMinus1(il);
        LabelNode useOld = new LabelNode();
        LabelNode haveLevel = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ILOAD, 7));
        il.add(new JumpInsnNode(Opcodes.IFEQ, useOld));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new JumpInsnNode(Opcodes.GOTO, haveLevel));
        il.add(useOld);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$getLevelW", "(" + W512_D + ")I", false));
        il.add(haveLevel);
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$checkEdgeW", "(" + W512_D + W512_D + "IIIZ)V", false));
        il.add(end);
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    private static MethodNode runUpdatesW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL, "farlands$runUpdatesW", "(I)I");
        InsnList il = mn.instructions;
        LabelNode begin = new LabelNode();
        LabelNode loop = new LabelNode();
        LabelNode done = new LabelNode();
        LabelNode elseBranch = new LabelNode();
        LabelNode skipEnq = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$isQueueEmptyW", "()Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, begin));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(begin);
        il.add(loop);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$isQueueEmptyW", "()Z", false));
        il.add(new JumpInsnNode(Opcodes.IFNE, done));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new JumpInsnNode(Opcodes.IFLE, done));
        il.add(new org.objectweb.asm.tree.IincInsnNode(1, -1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$removeFirstLongW", "()" + W512_D, false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$getLevelW", "(" + W512_D + ")I", false));
        clampLevel(il);
        il.add(new VarInsnNode(Opcodes.ISTORE, 3));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$computedLevelsW", MAP_D);
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new InsnNode(Opcodes.ICONST_M1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MAP, "remove", "(" + W512_D + "B)B", false));
        pushInt(il, 255);
        il.add(new InsnNode(Opcodes.IAND));
        il.add(new VarInsnNode(Opcodes.ISTORE, 4));
        // if (computedLevel < level)
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGE, elseBranch));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$setLevelW", "(" + W512_D + "I)V", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$checkNeighborsAfterUpdateW", "(" + W512_D + "IZ)V", false));
        il.add(new JumpInsnNode(Opcodes.GOTO, loop));
        // else if (computedLevel > level)
        il.add(elseBranch);
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPLE, loop));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        getLevelCountMinus1(il);
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$setLevelW", "(" + W512_D + "I)V", false));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        getLevelCountMinus1(il);
        il.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, skipEnq));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getLevelCountMinus1(il);
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "farlands$calculatePriorityW", "(II)I", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$enqueueW", "(" + W512_D + "I)V", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$computedLevelsW", MAP_D);
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new InsnNode(Opcodes.I2B));
        il.add(new InsnNode(Opcodes.ICONST_M1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MAP, "put", "(" + W512_D + "BB)B", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(skipEnq);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$checkNeighborsAfterUpdateW", "(" + W512_D + "IZ)V", false));
        il.add(new JumpInsnNode(Opcodes.GOTO, loop));
        il.add(done);
        putHasWorkW(il);
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    private static MethodNode getQueueSizeW() {
        MethodNode mn = m(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "farlands$getQueueSizeW", "()I");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$computedLevelsW", MAP_D);
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MAP, "size", "()I", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    private static MethodNode hasWideWork() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL, "farlands$hasWideWork", "()Z");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        getf(il, TARGET, "farlands$hasWorkW", "Z");
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    // ------------------------------------------------------------------
    // wide hooks (defaults; overridden by ChunkTrackerWidePatch)
    // ------------------------------------------------------------------

    private static MethodNode sourceSentinelW() {
        MethodNode mn = m(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "farlands$sourceSentinelW", "()" + W512_D);
        InsnList il = mn.instructions;
        LabelNode build = new LabelNode();
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, TARGET, "farlands$sourceW", W512_D));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new JumpInsnNode(Opcodes.IFNULL, build));
        il.add(new InsnNode(Opcodes.ARETURN));
        il.add(build);
        il.add(new InsnNode(Opcodes.POP));
        il.add(new LdcInsnNode(Long.MAX_VALUE));
        il.add(new LdcInsnNode(Long.MAX_VALUE));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/scalarwide/mc/WideChunkKey", "pack",
            "(JJ)" + W512_D, false));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new FieldInsnNode(Opcodes.PUTSTATIC, TARGET, "farlands$sourceW", W512_D));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    private static MethodNode isSourceW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED, "farlands$isSourceW", "(" + W512_D + ")Z");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "farlands$sourceSentinelW", "()" + W512_D, false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, W512, "equals", "(Ljava/lang/Object;)Z", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    private static MethodNode getComputedLevelW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED, "farlands$getComputedLevelW", "(" + W512_D + W512_D + "I)I");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    private static MethodNode checkNeighborsAfterUpdateW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED, "farlands$checkNeighborsAfterUpdateW", "(" + W512_D + "IZ)V");
        mn.instructions.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    private static MethodNode getLevelW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED, "farlands$getLevelW", "(" + W512_D + ")I");
        InsnList il = mn.instructions;
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    private static MethodNode setLevelW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED, "farlands$setLevelW", "(" + W512_D + "I)V");
        mn.instructions.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    private static MethodNode computeLevelFromNeighborW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED, "farlands$computeLevelFromNeighborW", "(" + W512_D + W512_D + "I)I");
        InsnList il = mn.instructions;
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }
}
