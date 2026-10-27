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
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Makes the collision iterator visit the <em>real</em> (long) block columns, so
 * blocks east of {@code x = 2^31} still collide.
 *
 * <p>Vanilla computes the cursor extents as ints:</p>
 * <pre>
 * int x0 = Mth.floor(box.minX - 1.0E-7) - 1;
 * int x1 = Mth.floor(box.maxX + 1.0E-7) + 1;
 * this.cursor = new Cursor3D(x0, y0, z0, x1, y1, z1);
 * </pre>
 * <p>At {@code box.maxX >= 2^31} (block 2147483648 = chunk 134217728) the
 * {@code +1} wraps to {@code Integer.MIN_VALUE}, the cursor range inverts and
 * {@code Cursor3D.advance()} returns false on the first call: the iterator
 * yields nothing, so entities fall through the far side of the seam and no
 * block collision is ever reported.</p>
 *
 * <p>This patch keeps the vanilla {@code Cursor3D} but feeds it <em>relative</em>
 * extents ({@code 0..x1-x0}) and remembers the origin in two long fields
 * ({@code farlands$frameX/farlands$frameZ}); {@code computeNext()} adds the
 * frame back to get the true long block coordinate, which is then used for</p>
 * <ul>
 *   <li>the chunk lookup ({@code getChunk(JJ)} - chunk 134217728 still fits an
 *       int, so the {@code getChunkForCollisions(int,int)} interface is
 *       untouched);</li>
 *   <li>the {@code pos} handed to the caller (wide {@code set(JJJ)});</li>
 *   <li>the {@code AABB.intersects} / {@code VoxelShape.move} arguments
 *       (true doubles, not the int-truncated view).</li>
 * </ul>
 *
 * <p>At normal coordinates the frame-relative split is numerically identical to
 * vanilla, so normal-world behavior is unchanged.</p>
 */
public final class BlockCollisionsPatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/BlockCollisions";
    private static final String AABB = "net/minecraft/world/phys/AABB";
    private static final String VOXEL_SHAPE = "net/minecraft/world/phys/shapes/VoxelShape";
    private static final String MTH = "net/minecraft/util/Mth";
    private static final String CURSOR = "net/minecraft/core/Cursor3D";
    private static final String MUTABLE = "net/minecraft/core/BlockPos$MutableBlockPos";
    private static final String COLLISION_GETTER = "net/minecraft/world/level/CollisionGetter";
    private static final String CHUNK_POS = "net/minecraft/world/level/ChunkPos";
    private static final String BLOCK_GETTER = "net/minecraft/world/level/BlockGetter";

    private static final String FRAME_X = "farlands$frameX";
    private static final String FRAME_Z = "farlands$frameZ";

    private static final String CTOR_DESC = "(Lnet/minecraft/world/level/CollisionGetter;"
        + "Lnet/minecraft/world/phys/shapes/CollisionContext;Lnet/minecraft/world/phys/AABB;Z"
        + "Ljava/util/function/BiFunction;)V";
    private static final String COMPUTE_NEXT_DESC = "()Ljava/lang/Object;";
    private static final String GET_CHUNK_DESC = "(II)Lnet/minecraft/world/level/BlockGetter;";

    /** computeNext() locals: 1=x, 2=y, 3=z (relative), 9=realX, 11=realZ. */
    private static final int L_X = 1;
    private static final int L_Y = 2;
    private static final int L_Z = 3;
    private static final int L_REAL_X = 9;
    private static final int L_REAL_Z = 11;

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        // Idempotency: key on our own field, never on a method name that could
        // exist for another reason.
        if (hasField(node, FRAME_X)) {
            return original;
        }
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, FRAME_X, "J", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, FRAME_Z, "J", null, null));

        MethodNode ctor = find(node, "<init>", CTOR_DESC);
        if (ctor == null) {
            throw new IllegalStateException(TARGET + "#<init>" + CTOR_DESC + " not found");
        }
        patchConstructor(ctor);

        MethodNode computeNext = find(node, "computeNext", COMPUTE_NEXT_DESC);
        if (computeNext == null) {
            throw new IllegalStateException(TARGET + "#computeNext not found");
        }
        patchComputeNext(computeNext);

        MethodNode getChunk = find(node, "getChunk", GET_CHUNK_DESC);
        if (getChunk == null) {
            throw new IllegalStateException(TARGET + "#getChunk" + GET_CHUNK_DESC + " not found");
        }
        patchGetChunk(getChunk);

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    // ------------------------------------------------------------------
    // constructor: long extents -> frame fields + relative Cursor3D
    // ------------------------------------------------------------------

    /**
     * Replaces
     * <pre>
     * int x0 = floor(box.minX - e) - 1; ... int z1 = floor(box.maxZ + e) + 1;
     * this.cursor = new Cursor3D(x0, y0, z0, x1, y1, z1);
     * </pre>
     * with long extents, two frame fields and a relative cursor
     * ({@code 0..x1-x0}). Y is untouched (a vertical extent cannot exceed int).
     */
    private static void patchConstructor(MethodNode mn) {
        AbstractInsnNode minX = null;
        AbstractInsnNode cursorPut = null;
        int index = 0;
        int minXIndex = -1;
        for (AbstractInsnNode n : mn.instructions) {
            if (minX == null && n instanceof FieldInsnNode f && f.getOpcode() == Opcodes.GETFIELD
                && AABB.equals(f.owner) && "minX".equals(f.name) && "D".equals(f.desc)) {
                minX = n;
                minXIndex = index;
            } else if (n instanceof FieldInsnNode f && f.getOpcode() == Opcodes.PUTFIELD
                && TARGET.equals(f.owner) && "cursor".equals(f.name) && index > minXIndex) {
                cursorPut = n;
                break;
            }
            index++;
        }
        if (minX == null || cursorPut == null) {
            throw new IllegalStateException(TARGET + ": unexpected constructor shape");
        }
        // Remove the whole vanilla extent computation (starting at the ALOAD
        // that pushes `this.box` for box.minX) plus the vanilla `putfield
        // cursor`: our replacement emits the same assignment with the relative
        // cursor. Collect the range first, then insert while every node is
        // still attached (ASM's insertBefore must never see a detached node),
        // and only then drop the stale instructions.
        AbstractInsnNode start = minX.getPrevious();
        if (!(start instanceof VarInsnNode v) || v.getOpcode() != Opcodes.ALOAD) {
            throw new IllegalStateException(TARGET + ": unexpected box load before box.minX");
        }
        java.util.List<AbstractInsnNode> remove = new java.util.ArrayList<>();
        for (AbstractInsnNode n = start; n != null; n = n.getNext()) {
            remove.add(n);
            if (n == cursorPut) {
                break;
            }
        }
        if (!remove.contains(cursorPut)) {
            throw new IllegalStateException(TARGET + ": cursor field assignment not in extent range");
        }
        mn.instructions.insertBefore(cursorPut, constructorBody());
        for (AbstractInsnNode n : remove) {
            mn.instructions.remove(n);
        }
    }

    private static InsnList constructorBody() {
        InsnList il = new InsnList();
        // long x0 = lfloor(box.minX - 1e-7) - 1
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, AABB, "minX", "D"));
        il.add(new LdcInsnNode(1.0E-7));
        il.add(new InsnNode(Opcodes.DSUB));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MTH, "lfloor", "(D)J", false));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new VarInsnNode(Opcodes.LSTORE, 6));
        // long x1 = lfloor(box.maxX + 1e-7) + 1
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, AABB, "maxX", "D"));
        il.add(new LdcInsnNode(1.0E-7));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MTH, "lfloor", "(D)J", false));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 8));
        // int y0 = floor(box.minY - 1e-7) - 1
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, AABB, "minY", "D"));
        il.add(new LdcInsnNode(1.0E-7));
        il.add(new InsnNode(Opcodes.DSUB));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MTH, "floor", "(D)I", false));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.ISUB));
        il.add(new VarInsnNode(Opcodes.ISTORE, 10));
        // int y1 = floor(box.maxY + 1e-7) + 1
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, AABB, "maxY", "D"));
        il.add(new LdcInsnNode(1.0E-7));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MTH, "floor", "(D)I", false));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new VarInsnNode(Opcodes.ISTORE, 11));
        // long z0 = lfloor(box.minZ - 1e-7) - 1
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, AABB, "minZ", "D"));
        il.add(new LdcInsnNode(1.0E-7));
        il.add(new InsnNode(Opcodes.DSUB));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MTH, "lfloor", "(D)J", false));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new VarInsnNode(Opcodes.LSTORE, 12));
        // long z1 = lfloor(box.maxZ + 1e-7) + 1
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, AABB, "maxZ", "D"));
        il.add(new LdcInsnNode(1.0E-7));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MTH, "lfloor", "(D)J", false));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 14));
        // this.farlands$frameX = x0; this.farlands$frameZ = z0;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 6));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, FRAME_X, "J"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 12));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, FRAME_Z, "J"));
        // this.cursor = new Cursor3D(0, y0, 0, (int)(x1-x0), y1, (int)(z1-z0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, CURSOR));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 10));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 8));
        il.add(new VarInsnNode(Opcodes.LLOAD, 6));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ILOAD, 11));
        il.add(new VarInsnNode(Opcodes.LLOAD, 14));
        il.add(new VarInsnNode(Opcodes.LLOAD, 12));
        il.add(new InsnNode(Opcodes.LSUB));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, CURSOR, "<init>", "(IIIIII)V", false));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "cursor", "L" + CURSOR + ";"));
        return il;
    }

    // ------------------------------------------------------------------
    // getChunk(long, long)
    // ------------------------------------------------------------------

    /**
     * Rewrites {@code getChunk(int,int)} into {@code getChunk(long,long)}: the
     * section coordinate is {@code x >> 4} on the true coordinate, which for the
     * first far chunk (134217728) still fits an int, so the collision getter
     * interface and the chunk cache key stay untouched.
     */
    private static void patchGetChunk(MethodNode mn) {
        mn.instructions.clear();
        mn.localVariables = null;
        mn.tryCatchBlocks.clear();
        mn.desc = "(JJ)L" + BLOCK_GETTER + ";";
        InsnList il = mn.instructions;
        LabelNode cacheMiss = new LabelNode();
        // int sx = (int)(x >> 4); int sz = (int)(z >> 4);
        // NB: LSHR takes a long value and an int shift count.
        il.add(new VarInsnNode(Opcodes.LLOAD, 1));
        il.add(new InsnNode(Opcodes.ICONST_4));
        il.add(new InsnNode(Opcodes.LSHR));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ISTORE, 5));
        il.add(new VarInsnNode(Opcodes.LLOAD, 3));
        il.add(new InsnNode(Opcodes.ICONST_4));
        il.add(new InsnNode(Opcodes.LSHR));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.ISTORE, 6));
        // long key = ChunkPos.pack(sx, sz);
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CHUNK_POS, "pack", "(II)J", false));
        il.add(new VarInsnNode(Opcodes.LSTORE, 7));
        // if (cachedBlockGetter != null && cachedBlockGetterPos == key) return it;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "cachedBlockGetter", "L" + BLOCK_GETTER + ";"));
        il.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFNULL, cacheMiss));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "cachedBlockGetterPos", "J"));
        il.add(new VarInsnNode(Opcodes.LLOAD, 7));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFNE, cacheMiss));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "cachedBlockGetter", "L" + BLOCK_GETTER + ";"));
        il.add(new InsnNode(Opcodes.ARETURN));
        // BlockGetter bg = collisionGetter.getChunkForCollisions(sx, sz);
        il.add(cacheMiss);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "collisionGetter", "L" + COLLISION_GETTER + ";"));
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, COLLISION_GETTER,
            "getChunkForCollisions", "(II)L" + BLOCK_GETTER + ";", true));
        il.add(new VarInsnNode(Opcodes.ASTORE, 9));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 9));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "cachedBlockGetter", "L" + BLOCK_GETTER + ";"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.LLOAD, 7));
        il.add(new FieldInsnNode(Opcodes.PUTFIELD, TARGET, "cachedBlockGetterPos", "J"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 9));
        il.add(new InsnNode(Opcodes.ARETURN));
    }

    // ------------------------------------------------------------------
    // computeNext(): relative cursor -> real long coordinates
    // ------------------------------------------------------------------

    private static void patchComputeNext(MethodNode mn) {
        // 1. After `z = cursor.nextZ()` insert realX/realZ = frame + relative.
        AbstractInsnNode nextZ = null;
        for (AbstractInsnNode n : mn.instructions) {
            if (n instanceof MethodInsnNode m && CURSOR.equals(m.owner) && "nextZ".equals(m.name)) {
                nextZ = n;
                break;
            }
        }
        if (nextZ == null || !(nextZ.getNext() instanceof VarInsnNode zStore)
            || zStore.getOpcode() != Opcodes.ISTORE || zStore.var != L_Z) {
            throw new IllegalStateException(TARGET + ": unexpected nextZ() sequence");
        }
        InsnList frameAdd = new InsnList();
        frameAdd.add(new VarInsnNode(Opcodes.ALOAD, 0));
        frameAdd.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, FRAME_X, "J"));
        frameAdd.add(new VarInsnNode(Opcodes.ILOAD, L_X));
        frameAdd.add(new InsnNode(Opcodes.I2L));
        frameAdd.add(new InsnNode(Opcodes.LADD));
        frameAdd.add(new VarInsnNode(Opcodes.LSTORE, L_REAL_X));
        frameAdd.add(new VarInsnNode(Opcodes.ALOAD, 0));
        frameAdd.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, FRAME_Z, "J"));
        frameAdd.add(new VarInsnNode(Opcodes.ILOAD, L_Z));
        frameAdd.add(new InsnNode(Opcodes.I2L));
        frameAdd.add(new InsnNode(Opcodes.LADD));
        frameAdd.add(new VarInsnNode(Opcodes.LSTORE, L_REAL_Z));
        mn.instructions.insert(zStore, frameAdd);

        // 2. getChunk(x, z) -> getChunk(realX, realZ)
        MethodInsnNode chunkCall = null;
        for (AbstractInsnNode n : mn.instructions) {
            if (n instanceof MethodInsnNode m && TARGET.equals(m.owner) && "getChunk".equals(m.name)
                && GET_CHUNK_DESC.equals(m.desc)) {
                chunkCall = m;
                break;
            }
        }
        if (chunkCall == null || !(chunkCall.getPrevious() instanceof VarInsnNode zLoad)
            || zLoad.var != L_Z || zLoad.getOpcode() != Opcodes.ILOAD
            || !(zLoad.getPrevious() instanceof VarInsnNode xLoad)
            || xLoad.var != L_X || xLoad.getOpcode() != Opcodes.ILOAD) {
            throw new IllegalStateException(TARGET + ": unexpected getChunk(II) call shape");
        }
        mn.instructions.set(xLoad, new VarInsnNode(Opcodes.LLOAD, L_REAL_X));
        mn.instructions.set(zLoad, new VarInsnNode(Opcodes.LLOAD, L_REAL_Z));
        chunkCall.desc = "(JJ)L" + BLOCK_GETTER + ";";

        // 3. pos.set(x, y, z) -> pos.set(realX, (long)y, realZ)
        MethodInsnNode posSet = null;
        for (AbstractInsnNode n : mn.instructions) {
            if (n instanceof MethodInsnNode m && MUTABLE.equals(m.owner) && "set".equals(m.name)
                && ("(III)L" + MUTABLE + ";").equals(m.desc)) {
                posSet = m;
                break;
            }
        }
        if (posSet == null) {
            throw new IllegalStateException(TARGET + ": pos.set(III) call not found");
        }
        AbstractInsnNode pz = posSet.getPrevious();
        AbstractInsnNode py = pz == null ? null : pz.getPrevious();
        AbstractInsnNode px = py == null ? null : py.getPrevious();
        AbstractInsnNode posField = px == null ? null : px.getPrevious();
        AbstractInsnNode thisLoad = posField == null ? null : posField.getPrevious();
        if (!(pz instanceof VarInsnNode v3) || v3.var != L_Z || v3.getOpcode() != Opcodes.ILOAD
            || !(py instanceof VarInsnNode v2) || v2.var != L_Y || v2.getOpcode() != Opcodes.ILOAD
            || !(px instanceof VarInsnNode v1) || v1.var != L_X || v1.getOpcode() != Opcodes.ILOAD
            || !(posField instanceof FieldInsnNode pf) || pf.getOpcode() != Opcodes.GETFIELD
            || !"pos".equals(pf.name)
            || !(thisLoad instanceof VarInsnNode v0) || v0.getOpcode() != Opcodes.ALOAD || v0.var != 0) {
            throw new IllegalStateException(TARGET + ": unexpected pos.set(III) argument shape");
        }
        InsnList setArgs = new InsnList();
        setArgs.add(new VarInsnNode(Opcodes.ALOAD, 0));
        setArgs.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "pos",
            "L" + MUTABLE + ";"));
        setArgs.add(new VarInsnNode(Opcodes.LLOAD, L_REAL_X));
        setArgs.add(new VarInsnNode(Opcodes.ILOAD, L_Y));
        setArgs.add(new InsnNode(Opcodes.I2L));
        setArgs.add(new VarInsnNode(Opcodes.LLOAD, L_REAL_Z));
        mn.instructions.insertBefore(posSet, setArgs);
        for (AbstractInsnNode n : new AbstractInsnNode[]{thisLoad, posField, px, py, pz}) {
            mn.instructions.remove(n);
        }
        posSet.desc = "(JJJ)L" + MUTABLE + ";";

        // 4. intersects/move arguments use the real doubles.
        for (AbstractInsnNode n : mn.instructions.toArray()) {
            if (!(n instanceof MethodInsnNode min) || min.getOpcode() != Opcodes.INVOKEVIRTUAL) {
                continue;
            }
            if (AABB.equals(min.owner) && "intersects".equals(min.name) && "(DDDDDD)Z".equals(min.desc)) {
                patchIntersectsArgs(mn, min);
            } else if (VOXEL_SHAPE.equals(min.owner) && "move".equals(min.name)
                && "(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/shapes/VoxelShape;".equals(min.desc)) {
                patchMoveArgs(mn, min);
            }
        }
    }

    private static void patchIntersectsArgs(MethodNode mn, MethodInsnNode invoke) {
        // Expected 18-instruction argument sequence directly before the invoke,
        // walking backwards from the invoke (opcode, local var or -1):
        int[][] expected = {
            {Opcodes.DADD, -1}, {Opcodes.DCONST_1, -1}, {Opcodes.I2D, -1}, {Opcodes.ILOAD, 3},
            {Opcodes.DADD, -1}, {Opcodes.DCONST_1, -1}, {Opcodes.I2D, -1}, {Opcodes.ILOAD, 2},
            {Opcodes.DADD, -1}, {Opcodes.DCONST_1, -1}, {Opcodes.I2D, -1}, {Opcodes.ILOAD, 1},
            {Opcodes.I2D, -1}, {Opcodes.ILOAD, 3},
            {Opcodes.I2D, -1}, {Opcodes.ILOAD, 2},
            {Opcodes.I2D, -1}, {Opcodes.ILOAD, 1},
        };
        AbstractInsnNode cur = invoke.getPrevious();
        java.util.ArrayList<AbstractInsnNode> remove = new java.util.ArrayList<>();
        for (int pos = 0; pos < expected.length; pos++) {
            if (cur == null) {
                throw new IllegalStateException(TARGET + ": unexpected intersects arg pattern at " + pos);
            }
            int op = expected[pos][0];
            int var = expected[pos][1];
            boolean ok = cur.getOpcode() == op
                && (var < 0 || (cur instanceof VarInsnNode v && v.var == var));
            if (!ok) {
                System.err.println(TARGET + ": pattern mismatch at pos " + pos
                    + " expected op=" + op + " var=" + var + " found op=" + cur.getOpcode()
                    + " class=" + cur.getClass().getSimpleName());
                throw new IllegalStateException(TARGET + ": unexpected intersects arg pattern at " + pos);
            }
            remove.add(cur);
            cur = cur.getPrevious();
        }
        InsnList repl = new InsnList();
        repl.add(new VarInsnNode(Opcodes.LLOAD, L_REAL_X));
        repl.add(new InsnNode(Opcodes.L2D));
        repl.add(new VarInsnNode(Opcodes.ILOAD, 2));
        repl.add(new InsnNode(Opcodes.I2D));
        repl.add(new VarInsnNode(Opcodes.LLOAD, L_REAL_Z));
        repl.add(new InsnNode(Opcodes.L2D));
        repl.add(new VarInsnNode(Opcodes.LLOAD, L_REAL_X));
        repl.add(new InsnNode(Opcodes.L2D));
        repl.add(new InsnNode(Opcodes.DCONST_1));
        repl.add(new InsnNode(Opcodes.DADD));
        repl.add(new VarInsnNode(Opcodes.ILOAD, 2));
        repl.add(new InsnNode(Opcodes.I2D));
        repl.add(new InsnNode(Opcodes.DCONST_1));
        repl.add(new InsnNode(Opcodes.DADD));
        repl.add(new VarInsnNode(Opcodes.LLOAD, L_REAL_Z));
        repl.add(new InsnNode(Opcodes.L2D));
        repl.add(new InsnNode(Opcodes.DCONST_1));
        repl.add(new InsnNode(Opcodes.DADD));
        mn.instructions.insertBefore(invoke, repl);
        for (AbstractInsnNode n : remove) {
            mn.instructions.remove(n);
        }
    }

    private static void patchMoveArgs(MethodNode mn, MethodInsnNode invoke) {
        // Expected: ALOAD 0, GETFIELD pos, then the invoke.
        AbstractInsnNode getfield = invoke.getPrevious();
        AbstractInsnNode aload = getfield == null ? null : getfield.getPrevious();
        if (!(getfield instanceof FieldInsnNode f && f.getOpcode() == Opcodes.GETFIELD
                && "pos".equals(f.name))
            || !(aload instanceof VarInsnNode v && v.getOpcode() == Opcodes.ALOAD && v.var == 0)) {
            throw new IllegalStateException(TARGET + ": unexpected move arg pattern");
        }
        InsnList repl = new InsnList();
        repl.add(new VarInsnNode(Opcodes.LLOAD, L_REAL_X));
        repl.add(new InsnNode(Opcodes.L2D));
        repl.add(new VarInsnNode(Opcodes.ILOAD, 2));
        repl.add(new InsnNode(Opcodes.I2D));
        repl.add(new VarInsnNode(Opcodes.LLOAD, L_REAL_Z));
        repl.add(new InsnNode(Opcodes.L2D));
        mn.instructions.insertBefore(invoke, repl);
        mn.instructions.remove(getfield);
        mn.instructions.remove(aload);
        invoke.desc = "(DDD)Lnet/minecraft/world/phys/shapes/VoxelShape;";
    }

    // ------------------------------------------------------------------

    private static boolean hasField(ClassNode node, String name) {
        for (FieldNode f : node.fields) {
            if (name.equals(f.name)) {
                return true;
            }
        }
        return false;
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
