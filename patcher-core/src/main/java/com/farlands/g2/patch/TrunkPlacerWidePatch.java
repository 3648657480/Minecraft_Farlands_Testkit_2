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
 * W5 consumer widening for the trunk placers that rebuild positions from the
 * origin's int view:
 *
 * <pre>
 *   int tx = origin.getX(); int tz = origin.getZ();
 *   ... new BlockPos(tx, yy, tz) / new BlockPos(x + ox, ey, z + oz) ...
 * </pre>
 *
 * <p>Each {@code new BlockPos(a,b,c)} in {@code placeTrunk} is rewritten to
 * {@code WidePos.fromOrigin(origin, a, b, c)}, which recovers the wide X/Z from
 * the origin plus the exact local delta. The expressions stay int; only the
 * constructor's base changes. At normal coordinates the value is identical.</p>
 *
 * <p>Currently unreachable at far coordinates because {@code TreeFeature} is
 * skipped by {@code TreeFeatureSeamGuardMixin}; this patch is prepared for the
 * day that guard is lifted.</p>
 */
public final class TrunkPlacerWidePatch implements ClassPatch {

    private static final String BLOCKPOS = "net/minecraft/core/BlockPos";
    private static final String WIDEPOS = "com/farlands/g2/util/WidePos";

    private static final String DARK_OAK =
        "net/minecraft/world/level/levelgen/feature/trunkplacers/DarkOakTrunkPlacer";
    private static final String FORKING =
        "net/minecraft/world/level/levelgen/feature/trunkplacers/ForkingTrunkPlacer";

    @Override
    public boolean matches(String internalName) {
        return DARK_OAK.equals(internalName) || FORKING.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        boolean changed = false;
        for (MethodNode m : node.methods) {
            if ("placeTrunk".equals(m.name) && m.desc.startsWith(
                "(Lnet/minecraft/world/level/WorldGenLevel;Ljava/util/function/BiConsumer;")) {
                int originSlot = (m.access & Opcodes.ACC_STATIC) != 0 ? 4 : 5;
                changed |= rewrite(m, originSlot);
            }
        }
        if (!changed) {
            return original;
        }
        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static boolean rewrite(MethodNode m, int originSlot) {
        boolean changed = false;
        java.util.List<TypeInsnNode> sites = new java.util.ArrayList<>();
        for (AbstractInsnNode p = m.instructions.getFirst(); p != null; p = p.getNext()) {
            if (p instanceof TypeInsnNode nw && nw.getOpcode() == Opcodes.NEW && BLOCKPOS.equals(nw.desc)) {
                sites.add(nw);
            }
        }
        for (TypeInsnNode nw : sites) {
            AbstractInsnNode dup = nextReal(nw);
            if (!(dup instanceof InsnNode di) || di.getOpcode() != Opcodes.DUP) {
                continue;
            }
            // Find this constructor's INVOKESPECIAL before the next NEW BlockPos.
            MethodInsnNode ctor = null;
            for (AbstractInsnNode q = nextReal(dup); q != null; q = nextReal(q)) {
                if (q instanceof TypeInsnNode t2 && t2.getOpcode() == Opcodes.NEW && BLOCKPOS.equals(t2.desc)) {
                    break;
                }
                if (q instanceof MethodInsnNode mi
                    && mi.getOpcode() == Opcodes.INVOKESPECIAL
                    && BLOCKPOS.equals(mi.owner)
                    && "<init>".equals(mi.name)
                    && "(III)V".equals(mi.desc)) {
                    ctor = mi;
                    break;
                }
            }
            if (ctor == null) {
                continue;
            }

            InsnList il = new InsnList();
            il.add(new VarInsnNode(Opcodes.ALOAD, originSlot));
            m.instructions.insertBefore(nw, il);
            m.instructions.remove(nw);
            m.instructions.remove(dup);

            ctor.setOpcode(Opcodes.INVOKESTATIC);
            ctor.owner = WIDEPOS;
            ctor.name = "fromOrigin";
            ctor.desc = "(L" + BLOCKPOS + ";III)L" + BLOCKPOS + ";";
            ctor.itf = false;
            changed = true;
        }
        return changed;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode n) {
        if (n == null) {
            return null;
        }
        AbstractInsnNode p = n.getNext();
        while (p != null && p.getOpcode() < 0) {
            p = p.getNext();
        }
        return p;
    }
}
