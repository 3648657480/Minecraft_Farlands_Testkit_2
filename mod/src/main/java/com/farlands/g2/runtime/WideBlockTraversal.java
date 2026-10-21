package com.farlands.g2.runtime;

import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Long-domain DDA block traversal; replaces the int-clamped vanilla loop. */
public final class WideBlockTraversal {
    private WideBlockTraversal() {}

    public static <T, C> T traverse(
        Vec3 from, Vec3 to, C context, BiFunction<C, BlockPos, T> consumer, Function<C, T> missFactory
    ) {
        if (from.equals(to)) {
            return missFactory.apply(context);
        }
        double toX = Mth.lerp(-1.0E-7, to.x, from.x);
        double toY = Mth.lerp(-1.0E-7, to.y, from.y);
        double toZ = Mth.lerp(-1.0E-7, to.z, from.z);
        double fromX = Mth.lerp(-1.0E-7, from.x, to.x);
        double fromY = Mth.lerp(-1.0E-7, from.y, to.y);
        double fromZ = Mth.lerp(-1.0E-7, from.z, to.z);
        long currentBlockX = Mth.lfloor(fromX);
        long currentBlockY = Mth.lfloor(fromY);
        long currentBlockZ = Mth.lfloor(fromZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        pos.set(currentBlockX, currentBlockY, currentBlockZ);
        T first = consumer.apply(context, pos);
        if (first != null) {
            return first;
        }
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        int signX = Mth.sign(dx);
        int signY = Mth.sign(dy);
        int signZ = Mth.sign(dz);
        double tDeltaX = signX == 0 ? Double.MAX_VALUE : signX / dx;
        double tDeltaY = signY == 0 ? Double.MAX_VALUE : signY / dy;
        double tDeltaZ = signZ == 0 ? Double.MAX_VALUE : signZ / dz;
        double tX = tDeltaX * (signX > 0 ? 1.0 - Mth.frac(fromX) : Mth.frac(fromX));
        double tY = tDeltaY * (signY > 0 ? 1.0 - Mth.frac(fromY) : Mth.frac(fromY));
        double tZ = tDeltaZ * (signZ > 0 ? 1.0 - Mth.frac(fromZ) : Mth.frac(fromZ));
        while (tX <= 1.0 || tY <= 1.0 || tZ <= 1.0) {
            if (tX < tY) {
                if (tX < tZ) {
                    currentBlockX += signX;
                    tX += tDeltaX;
                } else {
                    currentBlockZ += signZ;
                    tZ += tDeltaZ;
                }
            } else if (tY < tZ) {
                currentBlockY += signY;
                tY += tDeltaY;
            } else {
                currentBlockZ += signZ;
                tZ += tDeltaZ;
            }
            T result = consumer.apply(context, pos.set(currentBlockX, currentBlockY, currentBlockZ));
            if (result != null) {
                return result;
            }
        }
        return missFactory.apply(context);
    }
}
