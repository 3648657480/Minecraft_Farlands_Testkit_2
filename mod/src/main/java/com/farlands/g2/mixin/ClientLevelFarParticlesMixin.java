package com.farlands.g2.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Break and digging particles at far X.
 *
 * <p>Vanilla builds the particle positions from {@code pos.getX()/getZ()}
 * (int). At or beyond block 2^31 that wraps, so the particles spawn at the
 * mirrored coordinates and are never visible near the player. Re-implement the
 * two effects with the wide block coordinates; normal coordinates keep the
 * vanilla path untouched.</p>
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelFarParticlesMixin {

    private static final RandomSource FARLANDS$RANDOM = RandomSource.createThreadLocalInstance();

    @Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
    private void farlands$breakParticles(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!farlands$far(pos)) {
            return;
        }
        if (state.isAir() || !state.shouldSpawnTerrainParticles()) {
            ci.cancel();
            return;
        }
        ClientLevel self = (ClientLevel) (Object) this;
        VoxelShape shape = state.getShape(self, pos);
        double bx = (double) pos.getLongX();
        double by = (double) pos.getY();
        double bz = (double) pos.getLongZ();
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            double widthX = Math.min(1.0, x2 - x1);
            double widthY = Math.min(1.0, y2 - y1);
            double widthZ = Math.min(1.0, z2 - z1);
            int countX = Math.max(2, Mth.ceil(widthX / 0.25));
            int countY = Math.max(2, Mth.ceil(widthY / 0.25));
            int countZ = Math.max(2, Mth.ceil(widthZ / 0.25));
            for (int xx = 0; xx < countX; xx++) {
                for (int yy = 0; yy < countY; yy++) {
                    for (int zz = 0; zz < countZ; zz++) {
                        double relX = (xx + 0.5) / countX;
                        double relY = (yy + 0.5) / countY;
                        double relZ = (zz + 0.5) / countZ;
                        double x = relX * widthX + x1;
                        double y = relY * widthY + y1;
                        double z = relZ * widthZ + z1;
                        Minecraft.getInstance().particleEngine.add(new TerrainParticle(self,
                            bx + x, by + y, bz + z, relX - 0.5, relY - 0.5, relZ - 0.5, state, pos));
                    }
                }
            }
        });
        ci.cancel();
    }

    @Inject(method = "addBreakingBlockEffect", at = @At("HEAD"), cancellable = true)
    private void farlands$digParticles(BlockPos pos, Direction direction, CallbackInfo ci) {
        if (!farlands$far(pos)) {
            return;
        }
        ClientLevel self = (ClientLevel) (Object) this;
        BlockState state = self.getBlockState(pos);
        if (state.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE
            || !state.shouldSpawnTerrainParticles()) {
            ci.cancel();
            return;
        }
        double x = (double) pos.getLongX();
        double y = pos.getY();
        double z = (double) pos.getLongZ();
        AABB shape = state.getShape(self, pos).bounds();
        double xp = x + FARLANDS$RANDOM.nextDouble() * (shape.maxX - shape.minX - 0.2F) + 0.1F + shape.minX;
        double yp = y + FARLANDS$RANDOM.nextDouble() * (shape.maxY - shape.minY - 0.2F) + 0.1F + shape.minY;
        double zp = z + FARLANDS$RANDOM.nextDouble() * (shape.maxZ - shape.minZ - 0.2F) + 0.1F + shape.minZ;
        if (direction == Direction.DOWN) {
            yp = y + shape.minY - 0.1F;
        }
        if (direction == Direction.UP) {
            yp = y + shape.maxY + 0.1F;
        }
        if (direction == Direction.NORTH) {
            zp = z + shape.minZ - 0.1F;
        }
        if (direction == Direction.SOUTH) {
            zp = z + shape.maxZ + 0.1F;
        }
        if (direction == Direction.WEST) {
            xp = x + shape.minX - 0.1F;
        }
        if (direction == Direction.EAST) {
            xp = x + shape.maxX + 0.1F;
        }
        Minecraft.getInstance().particleEngine
            .add(new TerrainParticle(self, xp, yp, zp, 0.0, 0.0, 0.0, state, pos).setPower(0.2F).scale(0.6F));
        ci.cancel();
    }

    private static boolean farlands$far(BlockPos pos) {
        long x = pos.getLongX();
        long z = pos.getLongZ();
        return x != (long) (int) x || z != (long) (int) z;
    }
}