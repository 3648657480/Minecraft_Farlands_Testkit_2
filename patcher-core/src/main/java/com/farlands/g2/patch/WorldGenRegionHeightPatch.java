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
import org.objectweb.asm.tree.VarInsnNode;

/**
 * W5-C: makes {@code WorldGenRegion.getHeight(Types,int,int)} wrap-aware.
 *
 * <p>Vanilla derives the chunk as {@code SectionPos.blockToSectionCoord(x)}
 * from the int coordinates. A caller that carries the real position in a wide
 * {@code BlockPos} can only pass the int view (e.g. {@code HeightmapPlacement}
 * or {@code SurfaceRelativeThresholdFilter}); past block 2^31 that view wraps
 * to the negative half-axis, the heightmap of a different chunk is queried
 * (currently masked only by {@link WgrPatch}'s center-chunk fallback), and
 * neighbour-chunk reads across the seam silently return the wrong column.</p>
 *
 * <p>Here the int is reconstructed to the real coordinate nearest to the
 * region center's wide origin ({@code center.getPos().xLong() << 4}) before
 * the chunk lookup. The reconstruction is unique because the region only
 * spans a few chunks: {@code wx = x + k*2^32} with k chosen so {@code wx} is
 * nearest to the center. At normal coordinates {@code k == 0} and every
 * instruction reduces to the vanilla one, so the result is bit-identical.</p>
 */
public final class WorldGenRegionHeightPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/server/level/WorldGenRegion";
    private static final String CHUNKACCESS = "net/minecraft/world/level/chunk/ChunkAccess";
    private static final String CHUNKPOS = "net/minecraft/world/level/ChunkPos";
    private static final String HTYPES = "net/minecraft/world/level/levelgen/Heightmap$Types";
    private static final String DESC = "(L" + HTYPES + ";II)I";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        MethodNode m = find(node, "getHeight", DESC);
        if (m == null) {
            throw new IllegalStateException(TARGET + "#getHeight" + DESC + " not found");
        }
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof MethodInsnNode mi && CHUNKPOS.equals(mi.owner)
                && "xLong".equals(mi.name)) {
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
     * {@code long refX = this.center.getPos().xLong() << 4;
     *  long wx = unwrap(x, refX);
     *  long refZ = this.center.getPos().zLong() << 4;
     *  long wz = unwrap(z, refZ);
     *  int cx = (int)(wx >> 4); int cz = (int)(wz >> 4);
     *  warnIfReadOutsideWriteZone(cx, cz);
     *  return getChunk(cx, cz).getHeight(type, x & 15, z & 15) + 1;}
     *
     * <p>{@code unwrap(v, ref) = v + (((ref - v + 2^31) >> 32) << 32)}. The
     * local {@code & 15} is unaffected by the {@code k*2^32} shift, so the
     * vanilla masked read is reused.</p>
     */
    private static InsnList body() {
        InsnList il = new InsnList();
        // long refX = this.center.getPos().xLong() << 4
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "center", "L" + CHUNKACCESS + ";"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNKACCESS, "getPos",
            "()L" + CHUNKPOS + ";", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNKPOS, "xLong", "()J", false));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 4));
        il.add(new InsnNode(Opcodes.LSHL));
        il.add(new VarInsnNode(Opcodes.LSTORE, 6));
        // long wx = unwrap(x, refX)
        il.add(new VarInsnNode(Opcodes.LLOAD, 6));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new LdcInsnNode(2147483648L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 32));
        il.add(new InsnNode(Opcodes.LSHR));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 32));
        il.add(new InsnNode(Opcodes.LSHL));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 8));
        // long refZ = this.center.getPos().zLong() << 4
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "center", "L" + CHUNKACCESS + ";"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNKACCESS, "getPos",
            "()L" + CHUNKPOS + ";", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNKPOS, "zLong", "()J", false));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 4));
        il.add(new InsnNode(Opcodes.LSHL));
        il.add(new VarInsnNode(Opcodes.LSTORE, 10));
        // long wz = unwrap(z, refZ)
        il.add(new VarInsnNode(Opcodes.LLOAD, 10));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new LdcInsnNode(2147483648L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 32));
        il.add(new InsnNode(Opcodes.LSHR));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 32));
        il.add(new InsnNode(Opcodes.LSHL));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 12));
        // int cx = (int)(wx >> 4); int cz = (int)(wz >> 4)
        il.add(new VarInsnNode(Opcodes.LLOAD, 8));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 4));
        il.add(new InsnNode(Opcodes.LSHR));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ISTORE, 4));
        il.add(new VarInsnNode(Opcodes.LLOAD, 12));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 4));
        il.add(new InsnNode(Opcodes.LSHR));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ISTORE, 5));
        // warnIfReadOutsideWriteZone(cx, cz)
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "warnIfReadOutsideWriteZone",
            "(II)V", false));
        // return getChunk(cx, cz).getHeight(type, x & 15, z & 15) + 1
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "getChunk",
            "(II)L" + CHUNKACCESS + ";", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 15));
        il.add(new InsnNode(Opcodes.IAND));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 15));
        il.add(new InsnNode(Opcodes.IAND));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNKACCESS, "getHeight", DESC, false));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new InsnNode(Opcodes.IRETURN));
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
