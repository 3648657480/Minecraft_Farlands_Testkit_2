package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Repacks {@code SectionPos.asLong} from 22+22+20 bits to 29+29+6 bits
 * (x: bits 35-63, z: bits 6-34, y: bits 0-5).
 *
 * <p>27+27+10 was still one bit too small: the 2^31 block boundary is section
 * 2^27, and a signed 27-bit section field can only hold -2^26..2^26-1, so
 * section 2^27-1 aliased to -1. The lossy key collided for exactly the chunks
 * the boundary test generates (134217727 = 2^27-1), corrupting section
 * caches (BulkSectionAccess), lighting storage and everything keyed on the
 * packed long. 29 bits hold -2^28..2^28-1 sections (-4.29G..+4.29G blocks),
 * i.e. the whole int block domain with room past 2^31; the 6-bit y covers
 * -32..31 sections (-512..496 blocks), far beyond any world height.</p>
 *
 * <p>Because every producer and consumer of the packed long goes through the
 * {@code asLong/x(J)/y(J)/z(J)} accessors, the layout change is opaque. The
 * remaining int-shaped producers ({@code asLong(BlockPos)},
 * {@code blockToSectionCoord}) are reworked to read the wide block fields so
 * the key no longer aliases at the boundary.</p>
 */
public final class SectionPosPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/core/SectionPos";
    private static final String BLOCKPOS = "net/minecraft/core/BlockPos";
    private static final String VEC3I = "net/minecraft/core/Vec3i";
    private static final String CHUNKPOS = "net/minecraft/world/level/ChunkPos";
    private static final String WIDE_KEY = "com/scalarwide/mc/WideSectionKey2560";
    private static final long X_MASK = 0x1FFFFFFFL; // 29 bits
    private static final long Y_MASK = 0x3FL;       // 6 bits
    private static final int X_SHIFT = 35;
    private static final int Z_SHIFT = 6;
    private static final int Y_SHIFT = 0;

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        // publish the widened wrap so the mod can adapt its epoch math
        if (node.fields.stream().noneMatch(f -> "farlands$wrap".equals(f.name))) {
            node.fields.add(new org.objectweb.asm.tree.FieldNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "farlands$wrap", "I", null, 134217728));
        }

        rewrite(node, "asLong", "(III)J", pack());
        rewrite(node, "x", "(J)I", new InsnList() {{
            add(new VarInsnNode(Opcodes.LLOAD, 0));
            add(new IntInsnNode(Opcodes.BIPUSH, X_SHIFT));
            add(new InsnNode(Opcodes.LSHR));
            add(new InsnNode(Opcodes.L2I));
            add(new InsnNode(Opcodes.IRETURN));
        }});
        rewrite(node, "z", "(J)I", new InsnList() {{
            add(new VarInsnNode(Opcodes.LLOAD, 0));
            add(new IntInsnNode(Opcodes.BIPUSH, 64 - X_SHIFT));
            add(new InsnNode(Opcodes.LSHL));
            add(new IntInsnNode(Opcodes.BIPUSH, X_SHIFT));
            add(new InsnNode(Opcodes.LSHR));
            add(new InsnNode(Opcodes.L2I));
            add(new InsnNode(Opcodes.IRETURN));
        }});
        rewrite(node, "y", "(J)I", new InsnList() {{
            add(new VarInsnNode(Opcodes.LLOAD, 0));
            add(new IntInsnNode(Opcodes.BIPUSH, 64 - Z_SHIFT));
            add(new InsnNode(Opcodes.LSHL));
            add(new IntInsnNode(Opcodes.BIPUSH, 64 - Z_SHIFT));
            add(new InsnNode(Opcodes.LSHR));
            add(new InsnNode(Opcodes.L2I));
            add(new InsnNode(Opcodes.IRETURN));
        }});
        // column key: zero the y bits (bits 0..5 in the widened layout)
        rewrite(node, "getZeroNode", "(J)J", new InsnList() {{
            add(new VarInsnNode(Opcodes.LLOAD, 0));
            add(new LdcInsnNode(-64L));
            add(new InsnNode(Opcodes.LAND));
            add(new InsnNode(Opcodes.LRETURN));
        }});

        // SectionPos extends Vec3i, whose fields Vec3iPatch widened to long with
        // getLongX/Y/Z(). Vanilla origin() = sectionToBlockCoord(x()) = (int)s<<4,
        // which wraps to Integer.MIN_VALUE at section 2^27 (block 2^31). Derive
        // the block corner from the wide section coords instead. center() builds
        // on origin() and BlockPos.offset (already widened), so it inherits this.
        rewrite(node, "origin", "()Lnet/minecraft/core/BlockPos;", wideOrigin());

        // asLong(BlockPos) and of(BlockPos) were producers still reading the
        // wrapped int view; derive the section coords from the wide block
        // fields.
        rewrite(node, "asLong", "(Lnet/minecraft/core/BlockPos;)J", asLongBlockPos());
        rewrite(node, "of", "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/SectionPos;",
            ofBlockPos());
        // of(ChunkPos, sectionY): chunkPos.x()/z() are int views; build the
        // 2560-bit key from xLong()/zLong() so section 2^49 keeps its identity.
        rewrite(node, "of", "(Lnet/minecraft/world/level/ChunkPos;I)Lnet/minecraft/core/SectionPos;",
            ofChunkPos());

        // blockToSectionCoord(double) and posToSectionCoord(double):
        // vanilla uses Mth.floor(d) which casts to int via (int)Math.floor(d).
        // At d >= 2^31, the cast clamps to Integer.MAX_VALUE, pinning camera and
        // section resolution to section 134217727. Use Mth.lfloor(d) >> 4 instead.
        rewrite(node, "blockToSectionCoord", "(D)I", new InsnList() {{
            add(new VarInsnNode(Opcodes.DLOAD, 0));
            add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/util/Mth", "lfloor", "(D)J", false));
            add(new IntInsnNode(Opcodes.BIPUSH, 4));
            add(new InsnNode(Opcodes.LSHR));
            add(new InsnNode(Opcodes.L2I));
            add(new InsnNode(Opcodes.IRETURN));
        }});
        rewrite(node, "posToSectionCoord", "(D)I", new InsnList() {{
            add(new VarInsnNode(Opcodes.DLOAD, 0));
            add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "blockToSectionCoord", "(D)I", false));
            add(new InsnNode(Opcodes.IRETURN));
        }});

        // Wide section-coord decode used by consumers that must not wrap at
        // block 2^31 (e.g. BulkSectionAccess resolving the owning chunk).
        if (!hasLongBlockToSection(node)) {
            node.methods.add(longBlockToSection());
        }

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    private static boolean hasLongBlockToSection(ClassNode node) {
        return node.methods.stream().anyMatch(m -> "blockToSectionCoord".equals(m.name) && "(J)I".equals(m.desc));
    }

    /** {@code (int)(blockX >> 4)} in the wide domain (floor division by 16). */
    private static MethodNode longBlockToSection() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "blockToSectionCoord", "(J)I", null, null);
        InsnList il = mn.instructions;
        il.add(new VarInsnNode(Opcodes.LLOAD, 0));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 4));
        il.add(new InsnNode(Opcodes.LSHR));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new InsnNode(Opcodes.IRETURN));
        return mn;
    }

    /**
     * {@code asLong((int)(getLongX()>>4), (int)(getLongY()>>4), (int)(getLongZ()>>4))}
     * - every axis converted from block to section coordinates in the wide
     * domain. The section coords fit an int for the whole int block domain
     * (and past 2^31 in the wide fields), so the packed key is exact.
     */
    private static InsnList asLongBlockPos() {
        InsnList il = new InsnList();
        for (String axis : new String[]{"X", "Y", "Z"}) {
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I, "getLong" + axis, "()J", false));
            il.add(new IntInsnNode(Opcodes.BIPUSH, 4));
            il.add(new InsnNode(Opcodes.LSHR));
            il.add(new InsnNode(Opcodes.L2I));
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "asLong", "(III)J", false));
        il.add(new InsnNode(Opcodes.LRETURN));
        return il;
    }

    /** {@code new SectionPos((int)(getLongX()>>4), ...)} (wide section). */
    private static InsnList ofBlockPos() {
        InsnList il = new InsnList();
        il.add(new TypeInsnNode(Opcodes.NEW, TARGET));
        il.add(new InsnNode(Opcodes.DUP));
        for (String axis : new String[]{"X", "Y", "Z"}) {
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I, "getLong" + axis, "()J", false));
            il.add(new IntInsnNode(Opcodes.BIPUSH, 4));
            il.add(new InsnNode(Opcodes.LSHR));
            il.add(new InsnNode(Opcodes.L2I));
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, TARGET, "<init>", "(III)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    /** {@code SectionPos.of(WideSectionKey2560.of(chunkPos.xLong(), y, chunkPos.zLong()))}. */
    private static InsnList ofChunkPos() {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNKPOS, "xLong", "()J", false));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNKPOS, "zLong", "()J", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, WIDE_KEY, "of",
            "(JJJ)Lcom/scalarwide/mc/WideSectionKey2560;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "of",
            "(Lcom/scalarwide/mc/WideSectionKey2560;)Lnet/minecraft/core/SectionPos;", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    /** {@code new BlockPos(getLongX()<<4, getLongY()<<4, getLongZ()<<4)} (wide corner). */
    private static InsnList wideOrigin() {
        InsnList il = new InsnList();
        il.add(new TypeInsnNode(Opcodes.NEW, BLOCKPOS));
        il.add(new InsnNode(Opcodes.DUP));
        for (String axis : new String[]{"X", "Y", "Z"}) {
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, VEC3I, "getLong" + axis, "()J", false));
            il.add(new IntInsnNode(Opcodes.BIPUSH, 4));
            il.add(new InsnNode(Opcodes.LSHL));
        }
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, BLOCKPOS, "<init>", "(JJJ)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    private static InsnList pack() {
        InsnList il = new InsnList();
        il.add(new InsnNode(Opcodes.LCONST_0));
        il.add(new VarInsnNode(Opcodes.LSTORE, 3));
        // x << 35
        il.add(new VarInsnNode(Opcodes.LLOAD, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 0));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new LdcInsnNode(X_MASK));
        il.add(new InsnNode(Opcodes.LAND));
        il.add(new IntInsnNode(Opcodes.BIPUSH, X_SHIFT));
        il.add(new InsnNode(Opcodes.LSHL));
        il.add(new InsnNode(Opcodes.LOR));
        il.add(new VarInsnNode(Opcodes.LSTORE, 3));
        // y << 0
        il.add(new VarInsnNode(Opcodes.LLOAD, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 1));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new LdcInsnNode(Y_MASK));
        il.add(new InsnNode(Opcodes.LAND));
        il.add(new InsnNode(Opcodes.LOR));
        il.add(new VarInsnNode(Opcodes.LSTORE, 3));
        // z << 6
        il.add(new VarInsnNode(Opcodes.LLOAD, 3));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new LdcInsnNode(X_MASK));
        il.add(new InsnNode(Opcodes.LAND));
        il.add(new IntInsnNode(Opcodes.BIPUSH, Z_SHIFT));
        il.add(new InsnNode(Opcodes.LSHL));
        il.add(new InsnNode(Opcodes.LOR));
        il.add(new VarInsnNode(Opcodes.LSTORE, 3));
        il.add(new VarInsnNode(Opcodes.LLOAD, 3));
        il.add(new InsnNode(Opcodes.LRETURN));
        return il;
    }

    private static void rewrite(ClassNode node, String name, String desc, InsnList body) {
        MethodNode m = null;
        for (MethodNode candidate : node.methods) {
            if (name.equals(candidate.name) && desc.equals(candidate.desc)) {
                m = candidate;
                break;
            }
        }
        if (m == null) {
            throw new IllegalStateException(TARGET + "#" + name + desc + " not found");
        }
        m.instructions.clear();
        m.localVariables = null;
        m.tryCatchBlocks.clear();
        m.instructions.add(body);
    }
}
