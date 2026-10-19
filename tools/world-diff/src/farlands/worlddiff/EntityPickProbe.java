package farlands.worlddiff;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class EntityPickProbe {
    private EntityPickProbe() {
    }

    public static void run(ServerLevel level) {
        for (long[] column : new long[][]{
            {8L, 8L}, {2_147_483_656L, 8L}, {-2_147_483_656L, 8L}, {8L, 2_147_483_656L}
        }) {
            long x = column[0];
            long z = column[1];
            putStone(level, x, z);
            putStone(level, fold(x), fold(z));
            ItemEntity actor = new ItemEntity(level, x + 0.5, 83.0, z + 0.5,
                new ItemStack(Items.STONE));
            actor.setXRot(90.0f);
            actor.setYRot(0.0f);
            Vec3 eye = actor.getEyePosition(1.0f);
            HitResult result = actor.pick(5.0, 1.0f, false);
            BlockPos target = result instanceof BlockHitResult block ? block.getBlockPos() : null;
            boolean inReach = result.getLocation().closerThan(eye, 5.0);
            boolean realPos = target != null && target.getLongX() == x
                && target.getLongZ() == z && target.getY() == 80;
            boolean passed = result.getType() == HitResult.Type.BLOCK && inReach && realPos;
            System.out.println("[PICKFRAME] marker=entity-pick-02 column=" + x + "," + z
                + " type=" + result.getType() + " eye=" + eye + " hit=" + result.getLocation()
                + " target=" + (target == null ? "null" : target.getLongX() + ","
                    + target.getY() + "," + target.getLongZ())
                + " distance=" + eye.distanceTo(result.getLocation())
                + " inReach=" + inReach + " realPos=" + realPos + " passed=" + passed);
            System.out.flush();
            if (Boolean.getBoolean("farlands.testgen.pick_assert") && !passed) {
                throw new IllegalStateException("Entity pick frame failed for " + x + "," + z);
            }
        }
    }

    private static long fold(long coordinate) {
        return coordinate >= 2_147_483_648L ? coordinate - 4_294_967_296L
            : coordinate < -2_147_483_648L ? coordinate + 4_294_967_296L : coordinate;
    }

    private static void putStone(ServerLevel level, long x, long z) {
        BlockPos pos = new BlockPos(x, 80L, z);
        level.getChunk((int) (x >> 4), (int) (z >> 4))
            .setBlockState(pos, Blocks.STONE.defaultBlockState(), 0);
    }
}
