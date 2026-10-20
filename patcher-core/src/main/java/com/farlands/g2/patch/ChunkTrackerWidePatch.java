package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * W4-2 slice 2b: wide hooks for {@code ChunkTracker} (the chunk-node subclass
 * of the distance graph; {@code DynamicGraphMinFixedPointWidePatch} carries the
 * algorithm).
 *
 * <p>Neighbour expansion goes through {@code WideChunkPos.neighbor} - the
 * field-wise unpack/offset/repack that avoids the sign-extension carry a plain
 * +-1 on the packed key would cause. The sentinel (vanilla
 * {@code ChunkPos.INVALID_CHUNK_POS}) becomes the wide key of its unpacked
 * coordinates.</p>
 *
 * <p>The level hooks ({@code farlands$getLevelW}/{@code farlands$setLevelW}/
 * {@code farlands$getLevelFromSourceW}) currently bridge to the vanilla long
 * implementations by re-packing the node with the int-domain {@code pack(int,
 * int)}; for the int domain that bridge is bit-exact (the truncation point is
 * the subclass source maps, to be widened in slice 3).</p>
 */
public final class ChunkTrackerWidePatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/server/level/ChunkTracker";
    private static final String CORE = "net/minecraft/world/level/lighting/DynamicGraphMinFixedPoint";
    private static final String WK = "com/scalarwide/mc/WideChunkPos";
    private static final String W512 = "com/scalarwide/mc/WideChunkPos";
    private static final String W512_D = "Lcom/scalarwide/mc/WideChunkPos;";
    private static final String CHUNK_POS = "net/minecraft/world/level/ChunkPos";

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
        if (node.methods.stream().anyMatch(m -> "farlands$updateW".equals(m.name))) {
            return original;
        }

        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "farlands$sentinelW", W512_D, null, null));

        node.methods.add(trackerSentinelW());
        node.methods.add(isSourceW());
        node.methods.add(checkNeighborsAfterUpdateW());
        node.methods.add(getComputedLevelW());
        node.methods.add(computeLevelFromNeighborW());
        node.methods.add(getLevelFromSourceW());
        node.methods.add(updateW());

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static MethodNode m(int access, String name, String desc) {
        return new MethodNode(access, name, desc, null, null);
    }

    /** Pushes {@code WideChunkPos.neighbor(node, dx, dz)} for local slots. */
    private static void neighbor(InsnList il, int nodeSlot, int dxSlot, int dzSlot) {
        il.add(new VarInsnNode(Opcodes.ALOAD, nodeSlot));
        il.add(new VarInsnNode(Opcodes.ILOAD, dxSlot));
        il.add(new VarInsnNode(Opcodes.ILOAD, dzSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, WK, "neighbor", "(" + W512_D + "II)" + W512_D, false));
    }

    private static void callSentinel(InsnList il) {
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "farlands$trackerSentinelW", "()" + W512_D, false));
    }

    /** {@code WideChunkPos.pack((int) x(node), (int) z(node))} on the stack. */
    private static void packTruncated(InsnList il, int nodeSlot) {
        il.add(new VarInsnNode(Opcodes.ALOAD, nodeSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, WK, "x", "(" + W512_D + ")J", false));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ALOAD, nodeSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, WK, "z", "(" + W512_D + ")J", false));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CHUNK_POS, "pack", "(II)J", false));
    }

    private static MethodNode trackerSentinelW() {
        MethodNode mn = m(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "farlands$trackerSentinelW", "()" + W512_D);
        InsnList il = mn.instructions;
        LabelNode build = new LabelNode();
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, TARGET, "farlands$sentinelW", W512_D));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new JumpInsnNode(Opcodes.IFNULL, build));
        il.add(new InsnNode(Opcodes.ARETURN));
        il.add(build);
        il.add(new InsnNode(Opcodes.POP));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, CHUNK_POS, "INVALID_CHUNK_POS", "J"));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CHUNK_POS, "unpack", "(J)L" + CHUNK_POS + ";", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNK_POS, "xLong", "()J", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNK_POS, "zLong", "()J", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, WK, "pack", "(JJ)" + W512_D, false));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new FieldInsnNode(Opcodes.PUTSTATIC, TARGET, "farlands$sentinelW", W512_D));
        il.add(new InsnNode(Opcodes.ARETURN));
        return mn;
    }

    private static MethodNode isSourceW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED, "farlands$isSourceW", "(" + W512_D + ")Z");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        callSentinel(il);
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, W512, "equals", "(Ljava/lang/Object;)Z", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    private static MethodNode checkNeighborsAfterUpdateW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED, "farlands$checkNeighborsAfterUpdateW", "(" + W512_D + "IZ)V");
        InsnList il = mn.instructions;
        LabelNode body = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode ldx = new LabelNode();
        LabelNode ldz = new LabelNode();
        LabelNode nextDz = new LabelNode();
        LabelNode nextDx = new LabelNode();
        // if (!onlyDecrease || level < levelCount - 2) { ... }
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new JumpInsnNode(Opcodes.IFEQ, body));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, CORE, "levelCount", "I"));
        il.add(new InsnNode(Opcodes.ICONST_2));
        il.add(new InsnNode(Opcodes.ISUB));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPLT, body));
        il.add(new JumpInsnNode(Opcodes.GOTO, end));
        il.add(body);
        il.add(new InsnNode(Opcodes.ICONST_M1));
        il.add(new VarInsnNode(Opcodes.ISTORE, 4));
        il.add(ldx);
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGT, end));
        il.add(new InsnNode(Opcodes.ICONST_M1));
        il.add(new VarInsnNode(Opcodes.ISTORE, 5));
        il.add(ldz);
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGT, nextDx));
        neighbor(il, 1, 4, 5);
        il.add(new VarInsnNode(Opcodes.ASTORE, 6));
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, W512, "equals", "(Ljava/lang/Object;)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFNE, nextDz));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CORE, "farlands$checkNeighborW", "(" + W512_D + W512_D + "IZ)V", false));
        il.add(nextDz);
        il.add(new IincInsnNode(5, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, ldz));
        il.add(nextDx);
        il.add(new IincInsnNode(4, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, ldx));
        il.add(end);
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    private static MethodNode getComputedLevelW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED, "farlands$getComputedLevelW", "(" + W512_D + W512_D + "I)I");
        InsnList il = mn.instructions;
        LabelNode ldx = new LabelNode();
        LabelNode ldz = new LabelNode();
        LabelNode nextDz = new LabelNode();
        LabelNode nextDx = new LabelNode();
        LabelNode afterSub = new LabelNode();
        LabelNode afterMin = new LabelNode();
        LabelNode ret = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new VarInsnNode(Opcodes.ISTORE, 4));
        il.add(new InsnNode(Opcodes.ICONST_M1));
        il.add(new VarInsnNode(Opcodes.ISTORE, 5));
        il.add(ldx);
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGT, ret));
        il.add(new InsnNode(Opcodes.ICONST_M1));
        il.add(new VarInsnNode(Opcodes.ISTORE, 6));
        il.add(ldz);
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGT, nextDx));
        neighbor(il, 1, 5, 6);
        il.add(new VarInsnNode(Opcodes.ASTORE, 7));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, W512, "equals", "(Ljava/lang/Object;)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, afterSub));
        callSentinel(il);
        il.add(new VarInsnNode(Opcodes.ASTORE, 7));
        il.add(afterSub);
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, W512, "equals", "(Ljava/lang/Object;)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFNE, nextDz));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CORE, "farlands$getLevelW", "(" + W512_D + ")I", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CORE, "farlands$computeLevelFromNeighborW",
            "(" + W512_D + W512_D + "I)I", false));
        il.add(new VarInsnNode(Opcodes.ISTORE, 8));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPLE, afterMin));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new VarInsnNode(Opcodes.ISTORE, 4));
        il.add(afterMin);
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new JumpInsnNode(Opcodes.IFNE, nextDz));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(nextDz);
        il.add(new IincInsnNode(6, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, ldz));
        il.add(nextDx);
        il.add(new IincInsnNode(5, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, ldx));
        il.add(ret);
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    private static MethodNode computeLevelFromNeighborW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED, "farlands$computeLevelFromNeighborW", "(" + W512_D + W512_D + "I)I");
        InsnList il = mn.instructions;
        LabelNode fromSource = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        callSentinel(il);
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, W512, "equals", "(Ljava/lang/Object;)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFNE, fromSource));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(fromSource);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "farlands$getLevelFromSourceW", "(" + W512_D + ")I", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    private static MethodNode getLevelFromSourceW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED, "farlands$getLevelFromSourceW", "(" + W512_D + ")I");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        packTruncated(il, 1);
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "getLevelFromSource", "(J)I", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    private static MethodNode getLevelW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED, "farlands$getLevelW", "(" + W512_D + ")I");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        packTruncated(il, 1);
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "getLevel", "(J)I", false));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    private static MethodNode setLevelW() {
        MethodNode mn = m(Opcodes.ACC_PROTECTED, "farlands$setLevelW", "(" + W512_D + "I)V");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        packTruncated(il, 1);
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "setLevel", "(JI)V", false));
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }

    private static MethodNode updateW() {
        MethodNode mn = m(Opcodes.ACC_PUBLIC, "farlands$updateW", "(" + W512_D + "IZ)V");
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        callSentinel(il);
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CORE, "farlands$checkEdgeW",
            "(" + W512_D + W512_D + "IZ)V", false));
        il.add(new InsnNode(Opcodes.RETURN));
        return mn;
    }
}
