package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * W5-E: widens the chunk-generation creature spawn at the 2^31 seam.
 *
 * <p>Vanilla {@code NaturalSpawner.spawnMobsForChunkGeneration} starts from
 * {@code ChunkPos.getMinBlockX()/getMinBlockZ()} (int) and keeps the spawn
 * candidate in the chunk window {@code [xo, xo+16)} with a random walk:
 * after every attempt that reaches the bottom of the attempts loop,
 * {@code x/z} are jittered and the {@code while (x < xo || x >= xo + 16 ||
 * z < zo || z >= zo + 16)} loop resamples them from {@code startX/startZ}.
 * That bound test is int arithmetic. At chunk {@code 134217727} the int
 * {@code xo} is {@code 2147483632}, so {@code xo + 16} wraps to
 * {@code Integer.MIN_VALUE} and the loop can <em>never</em> see {@code x}
 * inside the window: the thread spins in {@code RandomSource.nextInt}
 * (NaturalSpawner.java:522/523) and the server watchdog reports STUCK.
 * The negative side has the mirror case at chunk {@code -134217729}, whose
 * int {@code xo} wraps to {@code +2147483632}. The same int start also
 * places the candidates at the wrapped (wrong) side for every chunk beyond
 * {@code +/-2^27}, silently dropping/relocating the spawns.</p>
 *
 * <p>Both affected methods are rebuilt in the long domain:</p>
 * <ul>
 *   <li>{@code spawnMobsForChunkGeneration}: {@code xo/zo} come from
 *       {@code ChunkPos.getMinBlockXLong()/getMinBlockZLong()}, and
 *       {@code x/z/startX/startZ} plus the jitter and the window test run as
 *       long. The window then cannot overflow and the loop terminates.</li>
 *   <li>{@code getTopNonCollidingPos}: the x/z parameters become long and the
 *       return position is built with the widened
 *       {@code MutableBlockPos(long,long,long)} constructor, so the height
 *       query and the block reads resolve the real column (the
 *       {@code WorldGenRegion} read entry points already consume
 *       {@code getLongX/getLongZ}).</li>
 * </ul>
 *
 * <p>W5-F extends the same method family to the positions the spawn step
 * derives <em>from the candidate doubles</em>. The candidate x/z are real
 * (long) values, but every conversion back to a {@code BlockPos} used the
 * int domain:</p>
 * <ul>
 *   <li>{@code NaturalSpawner} passed {@code BlockPos.containing(fx, y, fz)}
 *       to {@code SpawnPlacements.checkSpawnRules}: {@code Mth.floor(double)}
 *       wraps at {@code +/-2^31} and the predicate reads the mirrored
 *       negative chunk. The position is now built as a wide
 *       {@code BlockPos(JJJ)} from {@code Math.floor}.</li>
 *   <li>{@code Entity.setPosRaw} cached {@code blockPosition} from
 *       {@code Mth.floor(x)} ints, so every entity-derived query
 *       ({@code PathfinderMob.checkSpawnRules}/{@code getWalkTargetValue},
 *       {@code Mob.finalizeSpawn}, ...) resolved the wrapped chunk. The cache
 *       is now built from {@code Mth.lfloor(x/y/z)} with the wide
 *       {@code BlockPos(JJJ)} constructor. At normal coordinates the long
 *       equals the int it replaced.</li>
 *   <li>{@code LevelReader.containsAnyLiquid} looped int
 *       {@code Mth.floor(minX) .. Mth.ceil(maxX)}: past 2^31 the wrapped
 *       floor and the saturating ceil form a ~4.29e9-iteration loop that
 *       reads wrapped chunks - the spawn-step STUCK watchdog stack
 *       ({@code Mob.checkSpawnObstruction} -&gt; {@code containsAnyLiquid}).
 *       The loop now runs in the long domain ({@code Mth.lfloor}/
 *       {@code Mth.ceilLong} + wide {@code MutableBlockPos.set}).</li>
 *   <li>{@code WorldGenRegion.addFreshEntity} derived the target section
 *       from {@code entity.getBlockX()}, which wraps past 2^31 and is not in
 *       the generation cache. It now uses {@code Mth.lfloor(entity.getX()) >> 4}.</li>
 * </ul>
 *
 * <p>RNG call order, loop order and every int/double expression are kept
 * exactly as vanilla; at normal coordinates the long equals the int it
 * replaced (values, comparisons and produced positions), so the spawn
 * stream is bit-identical there.</p>
 */
public final class NaturalSpawnerWidePatch implements ClassPatch {

    private static final String TARGET = "net/minecraft/world/level/NaturalSpawner";
    private static final String WORLD_GEN_REGION = "net/minecraft/server/level/WorldGenRegion";
    private static final String HOLDER = "net/minecraft/core/Holder";
    private static final String BIOME = "net/minecraft/world/level/biome/Biome";
    private static final String CHUNKPOS = "net/minecraft/world/level/ChunkPos";
    private static final String RANDOM = "net/minecraft/util/RandomSource";
    private static final String WEIGHTED_LIST = "net/minecraft/util/random/WeightedList";
    private static final String MOB_SETTINGS = "net/minecraft/world/level/biome/MobSpawnSettings";
    private static final String SPAWNER_DATA =
        "net/minecraft/world/level/biome/MobSpawnSettings$SpawnerData";
    private static final String ENTITY_TYPE = "net/minecraft/world/entity/EntityType";
    private static final String SPAWN_PLACEMENTS = "net/minecraft/world/entity/SpawnPlacements";
    private static final String SPAWN_PLACEMENT_TYPE =
        "net/minecraft/world/entity/SpawnPlacementType";
    private static final String SERVER_ACCESSOR = "net/minecraft/world/level/ServerLevelAccessor";
    private static final String SERVER_LEVEL = "net/minecraft/server/level/ServerLevel";
    private static final String LEVEL = "net/minecraft/world/level/Level";
    private static final String LEVEL_READER = "net/minecraft/world/level/LevelReader";
    private static final String LEVEL_ACCESSOR = "net/minecraft/world/level/LevelAccessor";
    private static final String BLOCKPOS = "net/minecraft/core/BlockPos";
    private static final String MUTABLE = "net/minecraft/core/BlockPos$MutableBlockPos";
    private static final String BLOCKSTATE = "net/minecraft/world/level/block/state/BlockState";
    private static final String GAMERULES = "net/minecraft/world/level/gamerules/GameRules";
    private static final String GAMERULE = "net/minecraft/world/level/gamerules/GameRule";
    private static final String MOB_CATEGORY = "net/minecraft/world/entity/MobCategory";
    private static final String ENTITY = "net/minecraft/world/entity/Entity";
    private static final String MOB = "net/minecraft/world/entity/Mob";
    private static final String SPAWN_GROUP_DATA = "net/minecraft/world/entity/SpawnGroupData";
    private static final String SPAWN_REASON = "net/minecraft/world/entity/EntitySpawnReason";
    private static final String HEIGHTMAP_TYPES =
        "net/minecraft/world/level/levelgen/Heightmap$Types";
    private static final String DIFFICULTY = "net/minecraft/world/DifficultyInstance";
    private static final String DIMENSION_TYPE = "net/minecraft/world/level/dimension/DimensionType";
    private static final String DIRECTION = "net/minecraft/core/Direction";
    private static final String AABB = "net/minecraft/world/phys/AABB";
    private static final String OPTIONAL = "java/util/Optional";
    private static final String MTH = "net/minecraft/util/Mth";
    private static final String MATH = "java/lang/Math";
    private static final String LOGGER = "org/slf4j/Logger";

    private static final String GENERATE_DESC =
        "(L" + SERVER_ACCESSOR + ";L" + HOLDER + ";L" + CHUNKPOS + ";L" + RANDOM + ";)V";
    /** Vanilla {@code getTopNonCollidingPos(LevelReader, EntityType, int, int)}. */
    private static final String TOP_POS_OLD =
        "(L" + LEVEL_READER + ";L" + ENTITY_TYPE + ";II)L" + BLOCKPOS + ";";
    /** Wide {@code getTopNonCollidingPos(LevelReader, EntityType, long, long)}. */
    private static final String TOP_POS_NEW =
        "(L" + LEVEL_READER + ";L" + ENTITY_TYPE + ";JJ)L" + BLOCKPOS + ";";

    @Override
    public boolean matches(String internalName) {
        return TARGET.equals(internalName) || ENTITY.equals(internalName)
            || LEVEL_READER.equals(internalName) || WORLD_GEN_REGION.equals(internalName);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        if (ENTITY.equals(node.name)) {
            if (!widenSetPosRaw(node)) {
                return original;
            }
            ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
            node.accept(cw);
            return cw.toByteArray();
        }
        if (LEVEL_READER.equals(node.name)) {
            if (!widenContainsAnyLiquid(node)) {
                return original;
            }
            ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
            node.accept(cw);
            return cw.toByteArray();
        }
        if (WORLD_GEN_REGION.equals(node.name)) {
            if (!widenAddFreshEntity(node)) {
                return original;
            }
            ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
            node.accept(cw);
            return cw.toByteArray();
        }

        MethodNode spawn = find(node, "spawnMobsForChunkGeneration", GENERATE_DESC);
        if (spawn == null) {
            throw new IllegalStateException(TARGET + "#spawnMobsForChunkGeneration not found");
        }
        for (AbstractInsnNode insn : spawn.instructions) {
            if (insn instanceof MethodInsnNode mi && CHUNKPOS.equals(mi.owner)
                    && "getMinBlockXLong".equals(mi.name)) {
                return original; // already patched
            }
        }

        MethodNode top = find(node, "getTopNonCollidingPos", TOP_POS_OLD);
        if (top == null) {
            throw new IllegalStateException(TARGET + "#getTopNonCollidingPos not found");
        }

        SpawnBody body = spawnBody();
        spawn.instructions = body.instructions();
        spawn.localVariables = null;
        spawn.tryCatchBlocks.clear();
        spawn.tryCatchBlocks.add(body.tryCatch());
        spawn.maxLocals = 40;

        // The descriptor changes, so the generic Signature attribute
        // ({@code EntityType<?>, int, int}) is no longer consistent; drop it
        // (the JVM ignores it, but stale metadata confuses decompilers/javap).
        top.desc = TOP_POS_NEW;
        top.signature = null;
        top.instructions = topPosBody();
        top.localVariables = null;
        top.tryCatchBlocks.clear();
        top.maxLocals = 12;

        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }

    @Override
    public String describe(String internalName) {
        return "NaturalSpawnerWidePatch (wide creature spawn at 2^31)";
    }

    private record SpawnBody(InsnList instructions, TryCatchBlockNode tryCatch) {
    }

    // ------------------------------------------------------------------
    // spawnMobsForChunkGeneration
    // ------------------------------------------------------------------

    /**
     * Long-domain rebuild. Locals: 4 mobSettings, 5 mobs, 6/7 long xo,
     * 8/9 long zo, 10 Optional, 11 SpawnerData, 12 int count,
     * 13 SpawnGroupData, 14/15 long x, 16/17 long z, 18/19 long startX,
     * 20/21 long startZ, 22 int i, 23 boolean success, 24 int attempts,
     * 25 BlockPos pos, 26 float width, 27/28 double fx, 29/30 double fz,
     * 31 Entity entity, 32 Exception/Mob.
     */
    private static SpawnBody spawnBody() {
        InsnList il = new InsnList();
        LabelNode afterChecks = new LabelNode();
        LabelNode loop = new LabelNode();
        LabelNode haveData = new LabelNode();
        LabelNode forLoop = new LabelNode();
        LabelNode attemptsLoop = new LabelNode();
        LabelNode iNext = new LabelNode();
        LabelNode jitter = new LabelNode();
        LabelNode resampleCheck = new LabelNode();
        LabelNode resample = new LabelNode();
        LabelNode attemptNext = new LabelNode();
        LabelNode collisionOk = new LabelNode();
        LabelNode rulesOk = new LabelNode();
        LabelNode tryStart = new LabelNode();
        LabelNode tryEnd = new LabelNode();
        LabelNode catchHandler = new LabelNode();
        LabelNode notNull = new LabelNode();
        LabelNode snapTo = new LabelNode();
        LabelNode ret = new LabelNode();

        // MobSpawnSettings mobSettings = biome.value().getMobSettings();
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, HOLDER, "value",
            "()Ljava/lang/Object;", true));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, BIOME));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BIOME, "getMobSettings",
            "()L" + MOB_SETTINGS + ";", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 4));
        // WeightedList mobs = mobSettings.getMobs(MobCategory.CREATURE);
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, MOB_CATEGORY, "CREATURE",
            "L" + MOB_CATEGORY + ";"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MOB_SETTINGS, "getMobs",
            "(L" + MOB_CATEGORY + ";)L" + WEIGHTED_LIST + ";", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 5));
        // if (mobs.isEmpty() || !level.getLevel().getGameRules().get(SPAWN_MOBS)) return;
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WEIGHTED_LIST, "isEmpty", "()Z", false));
        il.add(new JumpInsnNode(Opcodes.IFNE, ret));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, SERVER_ACCESSOR, "getLevel",
            "()L" + SERVER_LEVEL + ";", true));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SERVER_LEVEL, "getGameRules",
            "()L" + GAMERULES + ";", false));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, GAMERULES, "SPAWN_MOBS",
            "L" + GAMERULE + ";"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, GAMERULES, "get",
            "(L" + GAMERULE + ";)Ljava/lang/Object;", false));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Boolean"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue",
            "()Z", false));
        il.add(new JumpInsnNode(Opcodes.IFNE, afterChecks));
        il.add(ret);
        il.add(new InsnNode(Opcodes.RETURN));

        // long xo = chunkPos.getMinBlockXLong(); long zo = ...;
        il.add(afterChecks);
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNKPOS, "getMinBlockXLong", "()J", false));
        il.add(new VarInsnNode(Opcodes.LSTORE, 6));
        il.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CHUNKPOS, "getMinBlockZLong", "()J", false));
        il.add(new VarInsnNode(Opcodes.LSTORE, 8));

        // while (random.nextFloat() < mobSettings.getCreatureProbability()) { ... }
        il.add(loop);
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, RANDOM, "nextFloat", "()F", true));
        il.add(new VarInsnNode(Opcodes.ALOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MOB_SETTINGS, "getCreatureProbability",
            "()F", false));
        il.add(new InsnNode(Opcodes.FCMPG));
        il.add(new JumpInsnNode(Opcodes.IFGE, ret));

        // Optional next = mobs.getRandom(random);
        il.add(new VarInsnNode(Opcodes.ALOAD, 5));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WEIGHTED_LIST, "getRandom",
            "(L" + RANDOM + ";)L" + OPTIONAL + ";", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 10));
        il.add(new VarInsnNode(Opcodes.ALOAD, 10));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, OPTIONAL, "isEmpty", "()Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, haveData));
        il.add(new JumpInsnNode(Opcodes.GOTO, loop));

        // SpawnerData spawnerData = (SpawnerData) next.get();
        il.add(haveData);
        il.add(new VarInsnNode(Opcodes.ALOAD, 10));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, OPTIONAL, "get",
            "()Ljava/lang/Object;", false));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, SPAWNER_DATA));
        il.add(new VarInsnNode(Opcodes.ASTORE, 11));
        // int count = minCount + random.nextInt(1 + maxCount - minCount);
        il.add(new VarInsnNode(Opcodes.ALOAD, 11));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SPAWNER_DATA, "minCount", "()I", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 11));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SPAWNER_DATA, "maxCount", "()I", false));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new VarInsnNode(Opcodes.ALOAD, 11));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SPAWNER_DATA, "minCount", "()I", false));
        il.add(new InsnNode(Opcodes.ISUB));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, RANDOM, "nextInt", "(I)I", true));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new VarInsnNode(Opcodes.ISTORE, 12));
        // SpawnGroupData groupSpawnData = null;
        il.add(new InsnNode(Opcodes.ACONST_NULL));
        il.add(new VarInsnNode(Opcodes.ASTORE, 13));
        // long x = xo + random.nextInt(16); long z = zo + random.nextInt(16);
        il.add(new VarInsnNode(Opcodes.LLOAD, 6));
        il.add(randomInt(16));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 14));
        il.add(new VarInsnNode(Opcodes.LLOAD, 8));
        il.add(randomInt(16));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, 16));
        // long startX = x; long startZ = z;
        il.add(new VarInsnNode(Opcodes.LLOAD, 14));
        il.add(new VarInsnNode(Opcodes.LSTORE, 18));
        il.add(new VarInsnNode(Opcodes.LLOAD, 16));
        il.add(new VarInsnNode(Opcodes.LSTORE, 20));

        // for (int i = 0; i < count; i++)
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 22));
        il.add(forLoop);
        il.add(new VarInsnNode(Opcodes.ILOAD, 22));
        il.add(new VarInsnNode(Opcodes.ILOAD, 12));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGE, loop));
        // boolean success = false; int attempts = 0;
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 23));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 24));

        // while (!success && attempts < 4)
        il.add(attemptsLoop);
        il.add(new VarInsnNode(Opcodes.ILOAD, 23));
        il.add(new JumpInsnNode(Opcodes.IFNE, iNext));
        il.add(new VarInsnNode(Opcodes.ILOAD, 24));
        il.add(new InsnNode(Opcodes.ICONST_4));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGE, iNext));

        // BlockPos pos = getTopNonCollidingPos(level, type, x, z);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 11));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SPAWNER_DATA, "type",
            "()L" + ENTITY_TYPE + ";", false));
        il.add(new VarInsnNode(Opcodes.LLOAD, 14));
        il.add(new VarInsnNode(Opcodes.LLOAD, 16));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TARGET, "getTopNonCollidingPos",
            TOP_POS_NEW, false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 25));

        // if (canSummon() && isSpawnPositionOk(type, level, pos)) { ... }
        il.add(new VarInsnNode(Opcodes.ALOAD, 11));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SPAWNER_DATA, "type",
            "()L" + ENTITY_TYPE + ";", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY_TYPE, "canSummon", "()Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, jitter));
        il.add(new VarInsnNode(Opcodes.ALOAD, 11));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SPAWNER_DATA, "type",
            "()L" + ENTITY_TYPE + ";", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 25));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, SPAWN_PLACEMENTS, "isSpawnPositionOk",
            "(L" + ENTITY_TYPE + ";L" + LEVEL_READER + ";L" + BLOCKPOS + ";)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, jitter));

        // float width = type.getWidth();
        il.add(new VarInsnNode(Opcodes.ALOAD, 11));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SPAWNER_DATA, "type",
            "()L" + ENTITY_TYPE + ";", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY_TYPE, "getWidth", "()F", false));
        il.add(new VarInsnNode(Opcodes.FSTORE, 26));
        // double fx = Mth.clamp((double) x, (double) xo + width, (double) xo + 16.0 - width);
        il.add(new VarInsnNode(Opcodes.LLOAD, 14));
        il.add(new InsnNode(Opcodes.L2D));
        il.add(new VarInsnNode(Opcodes.LLOAD, 6));
        il.add(new InsnNode(Opcodes.L2D));
        il.add(new VarInsnNode(Opcodes.FLOAD, 26));
        il.add(new InsnNode(Opcodes.F2D));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(new VarInsnNode(Opcodes.LLOAD, 6));
        il.add(new InsnNode(Opcodes.L2D));
        il.add(new LdcInsnNode(16.0d));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(new VarInsnNode(Opcodes.FLOAD, 26));
        il.add(new InsnNode(Opcodes.F2D));
        il.add(new InsnNode(Opcodes.DSUB));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MTH, "clamp", "(DDD)D", false));
        il.add(new VarInsnNode(Opcodes.DSTORE, 27));
        // double fz = Mth.clamp((double) z, (double) zo + width, (double) zo + 16.0 - width);
        il.add(new VarInsnNode(Opcodes.LLOAD, 16));
        il.add(new InsnNode(Opcodes.L2D));
        il.add(new VarInsnNode(Opcodes.LLOAD, 8));
        il.add(new InsnNode(Opcodes.L2D));
        il.add(new VarInsnNode(Opcodes.FLOAD, 26));
        il.add(new InsnNode(Opcodes.F2D));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(new VarInsnNode(Opcodes.LLOAD, 8));
        il.add(new InsnNode(Opcodes.L2D));
        il.add(new LdcInsnNode(16.0d));
        il.add(new InsnNode(Opcodes.DADD));
        il.add(new VarInsnNode(Opcodes.FLOAD, 26));
        il.add(new InsnNode(Opcodes.F2D));
        il.add(new InsnNode(Opcodes.DSUB));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MTH, "clamp", "(DDD)D", false));
        il.add(new VarInsnNode(Opcodes.DSTORE, 29));

        // if (!level.noCollision(type.getSpawnAABB(fx, pos.getY(), fz))) continue;
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 11));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SPAWNER_DATA, "type",
            "()L" + ENTITY_TYPE + ";", false));
        il.add(new VarInsnNode(Opcodes.DLOAD, 27));
        il.add(posY(25));
        il.add(new VarInsnNode(Opcodes.DLOAD, 29));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY_TYPE, "getSpawnAABB",
            "(DDD)L" + AABB + ";", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, SERVER_ACCESSOR, "noCollision",
            "(L" + AABB + ";)Z", true));
        il.add(new JumpInsnNode(Opcodes.IFNE, collisionOk));
        il.add(new JumpInsnNode(Opcodes.GOTO, attemptNext));
        il.add(collisionOk);

        // if (!SpawnPlacements.checkSpawnRules(type, level, CHUNK_GENERATION,
        //         BlockPos.containing(fx, pos.getY(), fz), level.getRandom())) continue;
        // W5-F: wide containing - past 2^31 the vanilla int floor wraps and the
        // predicate reads the mirrored negative chunk instead of this one.
        il.add(new VarInsnNode(Opcodes.ALOAD, 11));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SPAWNER_DATA, "type",
            "()L" + ENTITY_TYPE + ";", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, SPAWN_REASON, "CHUNK_GENERATION",
            "L" + SPAWN_REASON + ";"));
        il.add(wideContaining(27, 25, 29));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, SERVER_ACCESSOR, "getRandom",
            "()L" + RANDOM + ";", true));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, SPAWN_PLACEMENTS, "checkSpawnRules",
            "(L" + ENTITY_TYPE + ";L" + SERVER_ACCESSOR + ";L" + SPAWN_REASON + ";L"
                + BLOCKPOS + ";L" + RANDOM + ";)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFNE, rulesOk));
        il.add(new JumpInsnNode(Opcodes.GOTO, attemptNext));
        il.add(rulesOk);

        // Entity entity;
        // try { entity = type.create(level.getLevel(), NATURAL); }
        // catch (Exception e) { LOGGER.warn("Failed to create mob", e); continue; }
        il.add(tryStart);
        il.add(new VarInsnNode(Opcodes.ALOAD, 11));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, SPAWNER_DATA, "type",
            "()L" + ENTITY_TYPE + ";", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, SERVER_ACCESSOR, "getLevel",
            "()L" + SERVER_LEVEL + ";", true));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, SPAWN_REASON, "NATURAL",
            "L" + SPAWN_REASON + ";"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY_TYPE, "create",
            "(L" + LEVEL + ";L" + SPAWN_REASON + ";)L" + ENTITY + ";", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 31));
        il.add(tryEnd);
        il.add(new JumpInsnNode(Opcodes.GOTO, notNull));
        il.add(catchHandler);
        il.add(new VarInsnNode(Opcodes.ASTORE, 32));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, TARGET, "LOGGER", "L" + LOGGER + ";"));
        il.add(new LdcInsnNode("Failed to create mob"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 32));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, LOGGER, "warn",
            "(Ljava/lang/String;Ljava/lang/Throwable;)V", true));
        il.add(new JumpInsnNode(Opcodes.GOTO, attemptNext));

        // if (entity == null) continue;
        il.add(notNull);
        il.add(new VarInsnNode(Opcodes.ALOAD, 31));
        il.add(new JumpInsnNode(Opcodes.IFNONNULL, snapTo));
        il.add(new JumpInsnNode(Opcodes.GOTO, attemptNext));

        // entity.snapTo(fx, pos.getY(), fz, random.nextFloat() * 360.0F, 0.0F);
        il.add(snapTo);
        il.add(new VarInsnNode(Opcodes.ALOAD, 31));
        il.add(new VarInsnNode(Opcodes.DLOAD, 27));
        il.add(posY(25));
        il.add(new VarInsnNode(Opcodes.DLOAD, 29));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, RANDOM, "nextFloat", "()F", true));
        il.add(new LdcInsnNode(360.0f));
        il.add(new InsnNode(Opcodes.FMUL));
        il.add(new InsnNode(Opcodes.FCONST_0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY, "snapTo", "(DDDFF)V", false));

        // if (entity instanceof Mob mob) { ... }
        il.add(new VarInsnNode(Opcodes.ALOAD, 31));
        il.add(new TypeInsnNode(Opcodes.INSTANCEOF, MOB));
        il.add(new JumpInsnNode(Opcodes.IFEQ, jitter));
        il.add(new VarInsnNode(Opcodes.ALOAD, 31));
        il.add(new TypeInsnNode(Opcodes.CHECKCAST, MOB));
        il.add(new VarInsnNode(Opcodes.ASTORE, 32));
        // if (!mob.checkSpawnRules(level, CHUNK_GENERATION)) { jitter }
        il.add(new VarInsnNode(Opcodes.ALOAD, 32));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, SPAWN_REASON, "CHUNK_GENERATION",
            "L" + SPAWN_REASON + ";"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MOB, "checkSpawnRules",
            "(L" + LEVEL_ACCESSOR + ";L" + SPAWN_REASON + ";)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, jitter));
        // if (!mob.checkSpawnObstruction(level)) { jitter }
        il.add(new VarInsnNode(Opcodes.ALOAD, 32));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MOB, "checkSpawnObstruction",
            "(L" + LEVEL_READER + ";)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, jitter));
        // groupSpawnData = mob.finalizeSpawn(level,
        //     level.getCurrentDifficultyAt(mob.blockPosition()), CHUNK_GENERATION, groupSpawnData);
        il.add(new VarInsnNode(Opcodes.ALOAD, 32));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 32));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MOB, "blockPosition",
            "()L" + BLOCKPOS + ";", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, SERVER_ACCESSOR,
            "getCurrentDifficultyAt", "(L" + BLOCKPOS + ";)L" + DIFFICULTY + ";", true));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, SPAWN_REASON, "CHUNK_GENERATION",
            "L" + SPAWN_REASON + ";"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 13));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MOB, "finalizeSpawn",
            "(L" + SERVER_ACCESSOR + ";L" + DIFFICULTY + ";L" + SPAWN_REASON + ";L"
                + SPAWN_GROUP_DATA + ";)L" + SPAWN_GROUP_DATA + ";", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 13));
        // level.addFreshEntityWithPassengers(mob);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 32));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, SERVER_ACCESSOR,
            "addFreshEntityWithPassengers", "(L" + ENTITY + ";)V", true));
        // success = true;
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new VarInsnNode(Opcodes.ISTORE, 23));

        // Bottom of the attempts loop: jitter x/z, resample while out of the window.
        il.add(jitter);
        il.add(jitterLong(14));
        il.add(jitterLong(16));
        il.add(resampleCheck);
        il.add(new VarInsnNode(Opcodes.LLOAD, 14));
        il.add(new VarInsnNode(Opcodes.LLOAD, 6));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFLT, resample));
        il.add(new VarInsnNode(Opcodes.LLOAD, 14));
        il.add(new VarInsnNode(Opcodes.LLOAD, 6));
        il.add(new LdcInsnNode(16L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFGE, resample));
        il.add(new VarInsnNode(Opcodes.LLOAD, 16));
        il.add(new VarInsnNode(Opcodes.LLOAD, 8));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFLT, resample));
        il.add(new VarInsnNode(Opcodes.LLOAD, 16));
        il.add(new VarInsnNode(Opcodes.LLOAD, 8));
        il.add(new LdcInsnNode(16L));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFLT, attemptNext));
        il.add(resample);
        il.add(resampleLong(18, 14));
        il.add(resampleLong(20, 16));
        il.add(new JumpInsnNode(Opcodes.GOTO, resampleCheck));

        il.add(attemptNext);
        il.add(new IincInsnNode(24, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, attemptsLoop));
        il.add(iNext);
        il.add(new IincInsnNode(22, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, forLoop));

        return new SpawnBody(il,
            new TryCatchBlockNode(tryStart, tryEnd, catchHandler, "java/lang/Exception"));
    }

    /** {@code random.nextInt(bound)} pushed as an int. */
    private static InsnList randomInt(int bound) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new IntInsnNode(Opcodes.BIPUSH, bound));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, RANDOM, "nextInt", "(I)I", true));
        il.add(new InsnNode(Opcodes.I2L));
        return il;
    }

    /** {@code target += random.nextInt(5) - random.nextInt(5)} with long semantics. */
    private static InsnList jitterLong(int targetSlot) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.LLOAD, targetSlot));
        il.add(intDiff());
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, targetSlot));
        return il;
    }

    /** {@code target = base + random.nextInt(5) - random.nextInt(5)}. */
    private static InsnList resampleLong(int baseSlot, int targetSlot) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.LLOAD, baseSlot));
        il.add(intDiff());
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, targetSlot));
        return il;
    }

    /** {@code (random.nextInt(5) - random.nextInt(5))} as a long. */
    private static InsnList intDiff() {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, RANDOM, "nextInt", "(I)I", true));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new IntInsnNode(Opcodes.BIPUSH, 5));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, RANDOM, "nextInt", "(I)I", true));
        il.add(new InsnNode(Opcodes.ISUB));
        il.add(new InsnNode(Opcodes.I2L));
        return il;
    }

    /** {@code ((BlockPos) local).getY()} as double. */
    private static InsnList posY(int slot) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, slot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getY", "()I", false));
        il.add(new InsnNode(Opcodes.I2D));
        return il;
    }

    /**
     * Wide {@code BlockPos.containing(fx, y, z)}:
     * {@code new BlockPos(Math.floor(fx), (long) pos.getY(), Math.floor(fz))}.
     * {@code fx}/{@code fz} are double locals, {@code posSlot} holds the
     * candidate {@code BlockPos} whose integer (already real) y is reused.
     */
    private static InsnList wideContaining(int fxSlot, int posSlot, int fzSlot) {
        InsnList il = new InsnList();
        il.add(new TypeInsnNode(Opcodes.NEW, BLOCKPOS));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.DLOAD, fxSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MATH, "floor", "(D)D", false));
        il.add(new InsnNode(Opcodes.D2L));
        il.add(new VarInsnNode(Opcodes.ALOAD, posSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getY", "()I", false));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.DLOAD, fzSlot));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MATH, "floor", "(D)D", false));
        il.add(new InsnNode(Opcodes.D2L));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, BLOCKPOS, "<init>", "(JJJ)V", false));
        return il;
    }

    // ------------------------------------------------------------------
    // getTopNonCollidingPos
    // ------------------------------------------------------------------

    /**
     * {@code int y = level.getHeight(SpawnPlacements.getHeightmapType(type),
     *      (int) x, (int) z);
     *  MutableBlockPos pos = new MutableBlockPos(x, y, z);
     *  if (level.dimensionType().hasCeiling()) { ...unchanged... }
     *  return SpawnPlacements.getPlacementType(type).adjustSpawnPosition(level, pos);}
     */
    private static InsnList topPosBody() {
        InsnList il = new InsnList();
        LabelNode ceiling1 = new LabelNode();
        LabelNode ceiling2 = new LabelNode();
        LabelNode done = new LabelNode();

        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, SPAWN_PLACEMENTS, "getHeightmapType",
            "(L" + ENTITY_TYPE + ";)L" + HEIGHTMAP_TYPES + ";", false));
        il.add(new VarInsnNode(Opcodes.LLOAD, 2));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new VarInsnNode(Opcodes.LLOAD, 4));
        il.add(new InsnNode(Opcodes.L2I));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, LEVEL_READER, "getHeight",
            "(L" + HEIGHTMAP_TYPES + ";II)I", true));
        il.add(new VarInsnNode(Opcodes.ISTORE, 6));
        // pos = new MutableBlockPos(x, y, z)
        il.add(new TypeInsnNode(Opcodes.NEW, MUTABLE));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.LLOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 6));
        il.add(new InsnNode(Opcodes.I2L));
        il.add(new VarInsnNode(Opcodes.LLOAD, 4));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, MUTABLE, "<init>", "(JJJ)V", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 7));
        // if (level.dimensionType().hasCeiling()) { ... }
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, LEVEL_READER, "dimensionType",
            "()L" + DIMENSION_TYPE + ";", true));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, DIMENSION_TYPE, "hasCeiling", "()Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, done));
        il.add(ceiling1);
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new FieldInsnNode(Opcodes.GETSTATIC, DIRECTION, "DOWN", "L" + DIRECTION + ";"));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MUTABLE, "move",
            "(L" + DIRECTION + ";)L" + MUTABLE + ";", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, LEVEL_READER, "getBlockState",
            "(L" + BLOCKPOS + ";)L" + BLOCKSTATE + ";", true));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKSTATE, "isAir", "()Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, ceiling1));
        il.add(ceiling2);
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, LEVEL_READER, "getBlockState",
            "(L" + BLOCKPOS + ";)L" + BLOCKSTATE + ";", true));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKSTATE, "isAir", "()Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, done));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKPOS, "getY", "()I", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, LEVEL_READER, "getMinY", "()I", true));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGT, ceiling2));
        il.add(done);
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, SPAWN_PLACEMENTS, "getPlacementType",
            "(L" + ENTITY_TYPE + ";)L" + SPAWN_PLACEMENT_TYPE + ";", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MUTABLE, "immutable",
            "()L" + BLOCKPOS + ";", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, SPAWN_PLACEMENT_TYPE,
            "adjustSpawnPosition", "(L" + LEVEL_READER + ";L" + BLOCKPOS + ";)L" + BLOCKPOS + ";",
            true));
        il.add(new InsnNode(Opcodes.ARETURN));
        return il;
    }

    // ------------------------------------------------------------------
    // Entity.setPosRaw - wide blockPosition cache
    // ------------------------------------------------------------------

    /**
     * {@code Entity.setPosRaw(DDD)} derives the cached {@code blockPosition}
     * from {@code Mth.floor(x/y/z)} ints. Past 2^31 those wrap and every
     * entity-derived query (mob spawn rules, finalizeSpawn, entity add)
     * resolves the mirrored negative chunk; the spawn step then reads
     * outside the write zone and {@code containsAnyLiquid} loops over the
     * wrapped AABB range. Replace only the {@code new BlockPos(i, j, k)}
     * construction with the wide {@code Mth.lfloor} values; the int floors
     * stay for the surrounding vanilla logic. At normal coordinates the long
     * equals the int it replaced, so the cached position is unchanged.
     */
    private static boolean widenSetPosRaw(ClassNode node) {
        MethodNode m = find(node, "setPosRaw", "(DDD)V");
        if (m == null) {
            throw new IllegalStateException(ENTITY + "#setPosRaw(DDD) not found");
        }
        AbstractInsnNode ctor = null;
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof MethodInsnNode mi && mi.getOpcode() == Opcodes.INVOKESPECIAL
                    && BLOCKPOS.equals(mi.owner) && "<init>".equals(mi.name)
                    && "(III)V".equals(mi.desc)) {
                AbstractInsnNode next = insn.getNext();
                while (next != null && next.getOpcode() < 0) {
                    next = next.getNext(); // skip labels/line numbers
                }
                if (next instanceof FieldInsnNode fi && fi.getOpcode() == Opcodes.PUTFIELD
                        && ENTITY.equals(fi.owner) && "blockPosition".equals(fi.name)) {
                    ctor = insn;
                }
            }
        }
        if (ctor == null) {
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof MethodInsnNode mi && MTH.equals(mi.owner)
                        && "lfloor".equals(mi.name)) {
                    return false; // already patched
                }
            }
            throw new IllegalStateException(ENTITY + "#setPosRaw: BlockPos(III) ctor not found");
        }
        AbstractInsnNode load9 = ctor.getPrevious();
        AbstractInsnNode load8 = load9 == null ? null : load9.getPrevious();
        AbstractInsnNode load7 = load8 == null ? null : load8.getPrevious();
        AbstractInsnNode dup = load7 == null ? null : load7.getPrevious();
        AbstractInsnNode newInsn = dup == null ? null : dup.getPrevious();
        boolean shape = load9 instanceof VarInsnNode a && a.getOpcode() == Opcodes.ILOAD && a.var == 9
            && load8 instanceof VarInsnNode b && b.getOpcode() == Opcodes.ILOAD && b.var == 8
            && load7 instanceof VarInsnNode c && c.getOpcode() == Opcodes.ILOAD && c.var == 7
            && dup != null && dup.getOpcode() == Opcodes.DUP
            && newInsn != null && newInsn.getOpcode() == Opcodes.NEW;
        if (!shape) {
            throw new IllegalStateException(ENTITY + "#setPosRaw: unexpected BlockPos ctor shape");
        }
        InsnList repl = new InsnList();
        repl.add(new TypeInsnNode(Opcodes.NEW, BLOCKPOS));
        repl.add(new InsnNode(Opcodes.DUP));
        for (int slot : new int[]{1, 3, 5}) {
            repl.add(new VarInsnNode(Opcodes.DLOAD, slot));
            repl.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MTH, "lfloor", "(D)J", false));
        }
        repl.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, BLOCKPOS, "<init>", "(JJJ)V", false));
        m.instructions.insertBefore(newInsn, repl);
        for (AbstractInsnNode n = newInsn; n != null; ) {
            AbstractInsnNode next = n.getNext();
            m.instructions.remove(n);
            if (n == ctor) {
                break;
            }
            n = next;
        }
        m.localVariables = null;
        return true;
    }

    // ------------------------------------------------------------------
    // LevelReader.containsAnyLiquid - long loop bounds
    // ------------------------------------------------------------------

    /**
     * {@code containsAnyLiquid(AABB)}:
     * {@code long minX = Mth.lfloor(box.minX), maxX = Mth.ceilLong(box.maxX), ...;
     *  MutableBlockPos pos = new MutableBlockPos();
     *  for (long x = minX; x < maxX; x++) for (long y ...) for (long z ...) {
     *      pos.set(x, y, z);
     *      if (!this.getBlockState(pos).getFluidState().isEmpty()) return true;
     *  } return false;}
     *
     * <p>Past 2^31 the wrapped int {@code Mth.floor} lower bound and the
     * saturating {@code Mth.ceil} upper bound form a ~4.29e9 iteration loop
     * (spawn-step watchdog STUCK). The long bounds describe the small real
     * box the AABB actually spans, and the wide position keeps the block
     * reads in the real chunk. Normal coordinates are identical.</p>
     */
    private static boolean widenContainsAnyLiquid(ClassNode node) {
        MethodNode m = find(node, "containsAnyLiquid", "(L" + AABB + ";)Z");
        if (m == null) {
            throw new IllegalStateException(LEVEL_READER + "#containsAnyLiquid not found");
        }
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof MethodInsnNode mi && MTH.equals(mi.owner)
                    && "ceilLong".equals(mi.name)) {
                return false; // already patched
            }
        }
        String fluid = "net/minecraft/world/level/material/FluidState";
        LabelNode loopX = new LabelNode();
        LabelNode loopY = new LabelNode();
        LabelNode loopZ = new LabelNode();
        LabelNode nextY = new LabelNode();
        LabelNode nextX = new LabelNode();
        LabelNode done = new LabelNode();

        InsnList il = new InsnList();
        longSlot(il, 2, "minX");
        longSlot(il, 4, "maxX");
        longSlot(il, 6, "minY");
        longSlot(il, 8, "maxY");
        longSlot(il, 10, "minZ");
        longSlot(il, 12, "maxZ");
        // MutableBlockPos pos = new MutableBlockPos();
        il.add(new TypeInsnNode(Opcodes.NEW, MUTABLE));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, MUTABLE, "<init>", "()V", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 14));
        // x = minX
        il.add(new VarInsnNode(Opcodes.LLOAD, 2));
        il.add(new VarInsnNode(Opcodes.LSTORE, 15));
        il.add(loopX);
        il.add(new VarInsnNode(Opcodes.LLOAD, 15));
        il.add(new VarInsnNode(Opcodes.LLOAD, 4));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFGE, done));
        il.add(new VarInsnNode(Opcodes.LLOAD, 6));
        il.add(new VarInsnNode(Opcodes.LSTORE, 17));
        il.add(loopY);
        il.add(new VarInsnNode(Opcodes.LLOAD, 17));
        il.add(new VarInsnNode(Opcodes.LLOAD, 8));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFGE, nextX));
        il.add(new VarInsnNode(Opcodes.LLOAD, 10));
        il.add(new VarInsnNode(Opcodes.LSTORE, 19));
        il.add(loopZ);
        il.add(new VarInsnNode(Opcodes.LLOAD, 19));
        il.add(new VarInsnNode(Opcodes.LLOAD, 12));
        il.add(new InsnNode(Opcodes.LCMP));
        il.add(new JumpInsnNode(Opcodes.IFGE, nextY));
        il.add(new VarInsnNode(Opcodes.ALOAD, 14));
        il.add(new VarInsnNode(Opcodes.LLOAD, 15));
        il.add(new VarInsnNode(Opcodes.LLOAD, 17));
        il.add(new VarInsnNode(Opcodes.LLOAD, 19));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MUTABLE, "set",
            "(JJJ)L" + MUTABLE + ";", false));
        il.add(new InsnNode(Opcodes.POP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 14));
        il.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, LEVEL_READER, "getBlockState",
            "(L" + BLOCKPOS + ";)L" + BLOCKSTATE + ";", true));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BLOCKSTATE, "getFluidState",
            "()L" + fluid + ";", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, fluid, "isEmpty", "()Z", false));
        LabelNode continueZ = new LabelNode();
        il.add(new JumpInsnNode(Opcodes.IFNE, continueZ));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(continueZ);
        longIncrement(il, 19);
        il.add(new JumpInsnNode(Opcodes.GOTO, loopZ));
        il.add(nextY);
        longIncrement(il, 17);
        il.add(new JumpInsnNode(Opcodes.GOTO, loopY));
        il.add(nextX);
        longIncrement(il, 15);
        il.add(new JumpInsnNode(Opcodes.GOTO, loopX));
        il.add(done);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));

        m.instructions.clear();
        m.localVariables = null;
        m.tryCatchBlocks.clear();
        m.instructions.add(il);
        m.maxLocals = 21;
        return true;
    }

    /** {@code dst = Mth.lfloor/Mth.ceilLong(this.box.<field>)}. */
    private static void longSlot(InsnList il, int dst, String field) {
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, AABB, field, "D"));
        String fn = field.startsWith("min") ? "lfloor" : "ceilLong";
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MTH, fn, "(D)J", false));
        il.add(new VarInsnNode(Opcodes.LSTORE, dst));
    }

    /** {@code local += 1} for a long local. */
    private static void longIncrement(InsnList il, int slot) {
        il.add(new VarInsnNode(Opcodes.LLOAD, slot));
        il.add(new InsnNode(Opcodes.LCONST_1));
        il.add(new InsnNode(Opcodes.LADD));
        il.add(new VarInsnNode(Opcodes.LSTORE, slot));
    }

    // ------------------------------------------------------------------
    // WorldGenRegion.addFreshEntity - wide section lookup
    // ------------------------------------------------------------------

    /**
     * Vanilla derives the target section from {@code entity.getBlockX()}
     * ({@code Mth.floor(x)} int), which wraps past 2^31; the wrapped section
     * is not in the generation cache and the add dies/lands in the mirror.
     * Use the real block coordinate: {@code (int)(Mth.lfloor(entity.getX()) >> 4)}.
     * At normal coordinates this equals the vanilla section.
     */
    private static boolean widenAddFreshEntity(ClassNode node) {
        MethodNode m = find(node, "addFreshEntity", "(L" + ENTITY + ";)Z");
        if (m == null) {
            throw new IllegalStateException(WORLD_GEN_REGION + "#addFreshEntity not found");
        }
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof MethodInsnNode mi && MTH.equals(mi.owner)
                    && "lfloor".equals(mi.name)) {
                return false; // already patched
            }
        }
        InsnList il = new InsnList();
        for (int dst : new int[]{2, 3}) {
            String axis = dst == 2 ? "X" : "Z";
            il.add(new VarInsnNode(Opcodes.ALOAD, 1));
            il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY, "get" + axis, "()D", false));
            il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MTH, "lfloor", "(D)J", false));
            il.add(new IntInsnNode(Opcodes.BIPUSH, 4));
            il.add(new InsnNode(Opcodes.LSHR));
            il.add(new InsnNode(Opcodes.L2I));
            il.add(new VarInsnNode(Opcodes.ISTORE, dst));
        }
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ILOAD, 2));
        il.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WORLD_GEN_REGION, "getChunk",
            "(II)Lnet/minecraft/world/level/chunk/ChunkAccess;", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/chunk/ChunkAccess",
            "addEntity", "(L" + ENTITY + ";)V", false));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));

        m.instructions.clear();
        m.localVariables = null;
        m.tryCatchBlocks.clear();
        m.instructions.add(il);
        m.maxLocals = 4;
        return true;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static MethodNode find(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) {
            if (name.equals(m.name) && desc.equals(m.desc)) {
                return m;
            }
        }
        return null;
    }
}
