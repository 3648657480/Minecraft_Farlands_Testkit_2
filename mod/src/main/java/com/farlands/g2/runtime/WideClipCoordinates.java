package com.farlands.g2.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public interface WideClipCoordinates {
    double SEAM_X = 2_147_483_648.0;
    double FOLD_X = 4_294_967_296.0;
    ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> Boolean.FALSE);
    ThreadLocal<Boolean> SPLITTING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    Vec3 farlands$getRawFrom();

    ClipContext.Block farlands$getBlockType();

    ClipContext.Fluid farlands$getFluidType();

    CollisionContext farlands$getCollisionContext();

    static BlockHitResult restore(ClipContext context, BlockHitResult hit) {
        if (!(context instanceof WideClipCoordinates coordinates)) {
            return hit;
        }
        BlockPos hitPos = hit.getBlockPos();
        if (hitPos.getLongX() != (long) hitPos.getX()
            || hitPos.getLongZ() != (long) hitPos.getZ()) {
            return hit; // already restored (e.g. by BlockGetter.clip)
        }
        Vec3 raw = coordinates.farlands$getRawFrom();
        Vec3 folded = context.getFrom();
        long offsetX = (long) (raw.x - folded.x);
        long offsetZ = (long) (raw.z - folded.z);
        if (offsetX == 0L && offsetZ == 0L) {
            return hit;
        }
        Vec3 location = hit.getLocation().add((double) offsetX, 0.0, (double) offsetZ);
        BlockPos pos = hit.getBlockPos();
        BlockPos real = new BlockPos(pos.getLongX() + offsetX, pos.getLongY(),
            pos.getLongZ() + offsetZ);
        return hit.getType() == HitResult.Type.MISS
            ? BlockHitResult.miss(location, hit.getDirection(), real)
            : new BlockHitResult(location, hit.getDirection(), real, hit.isInside(),
                hit.isWorldBorderHit());
    }

    static BlockHitResult clipCrossSeam(BlockGetter getter, ClipContext context) {
        if (SPLITTING.get()) {
            return null;
        }
        Vec3 from = context.getFrom();
        Vec3 to = context.getTo();
        boolean fromWest = from.x < SEAM_X;
        boolean toWest = to.x < SEAM_X;
        if (fromWest == toWest) {
            return null;
        }

        double t = (SEAM_X - from.x) / (to.x - from.x);
        double my = from.y + (to.y - from.y) * t;
        double mz = from.z + (to.z - from.z) * t;
        Vec3 westSeam = new Vec3(SEAM_X - 1.0E-7, my, mz);
        Vec3 foldedSeam = new Vec3(-SEAM_X, my, mz);

        Vec3 westFrom = fromWest ? from : westSeam;
        Vec3 westTo = toWest ? to : westSeam;
        Vec3 eastFrom = fromWest ? foldedSeam : new Vec3(from.x - FOLD_X, from.y, from.z);
        Vec3 eastTo = toWest ? foldedSeam : new Vec3(to.x - FOLD_X, to.y, to.z);

        BlockHitResult west = clipSegment(getter, context, westFrom, westTo, false);
        BlockHitResult east = clipSegment(getter, context, eastFrom, eastTo, true);
        if (east != null && east.getType() != HitResult.Type.MISS) {
            east = shiftX(east, FOLD_X);
        }
        boolean westHit = west != null && west.getType() != HitResult.Type.MISS;
        boolean eastHit = east != null && east.getType() != HitResult.Type.MISS;
        if (westHit && eastHit) {
            double wd = west.getLocation().distanceToSqr(from);
            double ed = east.getLocation().distanceToSqr(from);
            return ed < wd ? east : west;
        }
        if (eastHit) {
            return east;
        }
        if (westHit) {
            return west;
        }
        return BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to));
    }

    private static BlockHitResult clipSegment(BlockGetter getter, ClipContext original,
        Vec3 from, Vec3 to, boolean folded) {
        ClipContext segment = new ClipContext(from, to,
            ((WideClipCoordinates) original).farlands$getBlockType(),
            ((WideClipCoordinates) original).farlands$getFluidType(),
            ((WideClipCoordinates) original).farlands$getCollisionContext());
        boolean oldBypass = BYPASS.get();
        boolean oldSplitting = SPLITTING.get();
        BYPASS.set(folded);
        SPLITTING.set(true);
        try {
            return getter.clip(segment);
        } finally {
            BYPASS.set(oldBypass);
            SPLITTING.set(oldSplitting);
        }
    }

    private static BlockHitResult shiftX(BlockHitResult hit, double offset) {
        Vec3 location = hit.getLocation().add(offset, 0.0, 0.0);
        BlockPos pos = hit.getBlockPos();
        BlockPos real = new BlockPos(pos.getLongX() + (long) offset, pos.getLongY(),
            pos.getLongZ());
        return new BlockHitResult(location, hit.getDirection(), real, hit.isInside(),
            hit.isWorldBorderHit());
    }
}
