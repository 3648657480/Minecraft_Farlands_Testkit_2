package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * W5-C: keeps {@code HeightmapPlacement.getPositions} in the wide domain.
 *
 * <p>Vanilla reads the candidate with {@code pos.getX()/getZ()} (int view,
 * wraps at block 2^31) and rebuilds the result with {@code new
 * BlockPos(x, y, z)}, so a candidate past the seam loses its real X/Z before
 * the feature ever sees it (the wrapped result is then rejected by the write
 * zone and the feature is silently dropped). Here the query is still made
 * through the int height API (whose {@code WorldGenRegion} implementation is
 * made wrap-aware by {@link WorldGenRegionHeightPatch}), and the result is
 * built with {@code pos.atY(y)}, which carries the long X/Z. At normal
 * coordinates {@code getLongX() == getX()}, {@code getLongZ() == getZ()} and
 * {@code atY(y) == new BlockPos(x, y, z)}, so the produced stream is
 * bit-identical.</p>
 */
public final class HeightmapPlacementPatch implements ClassPatch {

    private static final String TARGET =
        "net/minecraft/world/level/levelgen/placement/HeightmapPlacement";
    private static final String BLOCKPOS = "net/minecraft/core/BlockPos";
    private static final String VEC3I = "net/minecraft/core/Vec3i";
    private static final String CTX =
        "net/minecraft/world/level/levelgen/placement/PlacementContext";
    private static final String HTYPES = "net/minecraft/world/level/levelgen/Heightmap$Types";
    private static final String DESC = "(L" + CTX + ";Lnet/minecraft/util/RandomSource;L"
        + BLOCKPOS + ";)Ljava/util/stream/Stream;";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        MethodNode m = find(node, "getPositions", DESC);
        if (m == null) {
            throw new IllegalStateException(TARGET + "#getPositions" + DESC + " not found");
        }
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof MethodInsnNode mi && BLOCKPOS.equals(mi.owner)
                && "getLongX".equals(mi.name)) {
                return original; // already patched
            }
        }

        m.instructions.clear();
        m.localVariables = null;
        m.tryCatchBlocks.clear();
        m.instructions.add(body());

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    /**
     * {@code long wx = pos.getLongX(); long wz = pos.getLongZ();
     *  int y = ctx.getHeight(this.heightmap, (int) wx, (int) wz);
     *  if (y > ctx.getMinY()) return Stream.of(pos.atY(y));
     *  return Stream.of();}
     */
    private static InsnList body() {
        InsnList il = new InsnList();
        // long wx = pos.getLongX()
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I, "getLongX", "()J", false));
        il.add(new VarInsnNode(Opcodes.LSTORE, 4));
        // long wz = pos.getLongZ()
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I, "getLongZ", "()J", false));
        il.add(new VarInsnNode(Opcodes.LSTORE, 6));
        // int y = ctx.getHeight(this.heightmap, (int) wx, (int) wz)
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new org.objectweb.asm.tree.FieldInsnNode(Opcodes.GETFIELD, TARGET, "heightmap",
            "L" + HTYPES + ";"));
        il.add(new VarInsnNode(Opcodes.LLOAD, 4));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.LLOAD, 6));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CTX, "getHeight",
            "(L" + HTYPES + ";II)I", false));
        il.add(new VarInsnNode(Opcodes.ISTORE, 8));
        // if (y <= ctx.getMinY()) -> empty
        LabelNode empty = new LabelNode();
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CTX, "getMinY", "()I", false));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPLE, empty));
        // return Stream.of(pos.atY(y));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "atY",
            "(I)L" + BLOCKPOS + ";", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/stream/Stream", "of",
            "(Ljava/lang/Object;)Ljava/util/stream/Stream;", true)); // Stream.of is a static interface method: itf=true
        il.add(new InsnNode(Opcodes.ARETURN));
        // return Stream.of();
        il.add(empty);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new TypeInsnNode(Opcodes.ANEWARRAY, BLOCKPOS));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/stream/Stream", "of",
            "([Ljava/lang/Object;)Ljava/util/stream/Stream;", true));
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
}
