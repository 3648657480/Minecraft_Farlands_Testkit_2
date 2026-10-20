package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Set;

/**
 * W5 consumer widening: {@code new BlockPos(origin.getX(), y, origin.getZ())}
 * keeps the wide X/Z of {@code origin} instead of rebuilding from the int view.
 *
 * <p>Matches the exact bytecode shape</p>
 * <pre>
 *   getX()I  ... y(int) ...  getZ()I  BlockPos.&lt;init&gt;(III)V
 * </pre>
 * <p>and rewrites it to</p>
 * <pre>
 *   getLongX()J ... y(int) ... I2L  getLongZ()J  BlockPos.&lt;init&gt;(JJJ)V
 * </pre>
 * <p>Only simple y expressions are touched (no long arithmetic in between and
 * no int arithmetic applied to the getX result). At normal coordinates
 * {@code getLongX == getX}, so the result is bit-identical.</p>
 */
public final class WidePosAtYPatch implements ClassPatch {

    private static final String BLOCKPOS = "net/minecraft/core/BlockPos";
    private static final String VEC3I = "net/minecraft/core/Vec3i";

    private static final Set<String> TARGETS = Set.of(
        "net/minecraft/world/level/levelgen/feature/IcebergFeature",
        "net/minecraft/world/level/levelgen/structure/templatesystem/GravityProcessor",
        "net/minecraft/world/level/levelgen/structure/structures/OceanRuinPieces$OceanRuinPiece",
        "net/minecraft/world/level/levelgen/structure/structures/ShipwreckPieces$ShipwreckPiece",
        "net/minecraft/world/level/levelgen/feature/foliageplacers/MegaPineFoliagePlacer"
    );

    @Override
    public boolean matches(String internalName) {
        return TARGETS.contains(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        boolean changed = false;
        for (MethodNode m : node.methods) {
            changed |= patchMethod(m);
        }
        if (!changed) {
            return original;
        }
        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static boolean patchMethod(MethodNode m) {
        boolean changed = false;
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode ctor)
                || ctor.getOpcode() != Opcodes.INVOKESPECIAL
                || !BLOCKPOS.equals(ctor.owner)
                || !"<init>".equals(ctor.name)
                || !"(III)V".equals(ctor.desc)) {
                continue;
            }
            if (tryRewrite(m, ctor)) {
                changed = true;
            }
        }
        return changed;
    }

    private static boolean tryRewrite(MethodNode method, MethodInsnNode ctor) {
        AbstractInsnNode z = prevReal(ctor);
        if (!isGet(z, "getZ")) {
            return false;
        }
        AbstractInsnNode zRecv = prevReal(z);
        AbstractInsnNode zStart = null;
        if (zRecv instanceof VarInsnNode vi && vi.getOpcode() == Opcodes.ALOAD) {
            zStart = zRecv;
        } else if (zRecv instanceof org.objectweb.asm.tree.FieldInsnNode fi
            && fi.getOpcode() == Opcodes.GETFIELD) {
            AbstractInsnNode before = prevReal(fi);
            if (before instanceof VarInsnNode vi2 && vi2.getOpcode() == Opcodes.ALOAD) {
                zStart = before;
            }
        }
        if (zStart == null) {
            return false;
        }

        // find getX producer before the y expression
        AbstractInsnNode x = null;
        for (AbstractInsnNode p = prevReal(zStart); p != null; p = prevReal(p)) {
            if (isGet(p, "getX")) {
                x = p;
                break;
            }
            if (p instanceof MethodInsnNode mi && isGet(mi, "getX")) {
                x = p;
                break;
            }
        }
        if (x == null) {
            return false;
        }

        // x must be the direct first argument: the instruction after getX must not
        // consume it with int arithmetic.
        AbstractInsnNode afterX = nextReal(x);
        if (afterX != null && isIntArith(afterX.getOpcode())) {
            return false;
        }
        // the y expression must stay int: reject long ops / long accessors between
        for (AbstractInsnNode p = nextReal(x); p != null && p != zStart; p = nextReal(p)) {
            int op = p.getOpcode();
            if (op == Opcodes.LADD || op == Opcodes.LSUB || op == Opcodes.LMUL
                || op == Opcodes.L2I || op == Opcodes.I2L) {
                return false;
            }
            if (p instanceof MethodInsnNode mi && (mi.name.equals("getLongX") || mi.name.equals("getLongZ"))) {
                return false;
            }
        }

        // rewrite
        ((MethodInsnNode) x).name = "getLongX";
        ((MethodInsnNode) x).desc = "()J";
        ((MethodInsnNode) z).name = "getLongZ";
        ((MethodInsnNode) z).desc = "()J";
        method.instructions.insertBefore(zStart, new InsnNode(Opcodes.I2L));
        ctor.desc = "(JJJ)V";
        return true;
    }

    private static boolean isGet(AbstractInsnNode n, String name) {
        if (!(n instanceof MethodInsnNode mi)) {
            return false;
        }
        return (VEC3I.equals(mi.owner) || BLOCKPOS.equals(mi.owner))
            && name.equals(mi.name) && "()I".equals(mi.desc);
    }

    private static boolean isIntArith(int op) {
        return op == Opcodes.IADD || op == Opcodes.ISUB || op == Opcodes.IMUL
            || op == Opcodes.IDIV || op == Opcodes.IREM || op == Opcodes.INEG
            || op == Opcodes.ISHL || op == Opcodes.ISHR || op == Opcodes.IUSHR
            || op == Opcodes.IAND || op == Opcodes.IOR || op == Opcodes.IXOR;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode n) {
        AbstractInsnNode p = n.getNext();
        while (p != null && (p.getOpcode() < 0)) {
            p = p.getNext();
        }
        return p;
    }

    private static AbstractInsnNode prevReal(AbstractInsnNode n) {
        AbstractInsnNode p = n.getPrevious();
        while (p != null && (p.getOpcode() < 0)) {
            p = p.getPrevious();
        }
        return p;
    }
}

