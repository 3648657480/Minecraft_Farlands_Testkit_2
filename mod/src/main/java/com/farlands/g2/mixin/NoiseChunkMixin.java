package com.farlands.g2.mixin;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.atomic.AtomicLong;

@Mixin(NoiseChunk.class)
public class NoiseChunkMixin {

    /** Rate-limited visibility for aquifer fallback; silent before W0.5. */
    @Unique
    private static final AtomicLong farlands$aquiferFailures = new AtomicLong();

    @Redirect(method = "<init>",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/Aquifer;create(Lnet/minecraft/world/level/levelgen/NoiseChunk;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/levelgen/NoiseRouter;Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;IILnet/minecraft/world/level/levelgen/Aquifer$FluidPicker;)Lnet/minecraft/world/level/levelgen/Aquifer;"),
        require = 0)
    private static Aquifer farlands$safeAquifer(NoiseChunk noiseChunk, ChunkPos pos, NoiseRouter router,
            PositionalRandomFactory positionalRandomFactory, int minBlockY, int yBlockSize,
            Aquifer.FluidPicker fluidRule) {
        Aquifer real;
        try {
            real = Aquifer.create(noiseChunk, pos, router, positionalRandomFactory, minBlockY, yBlockSize, fluidRule);
        } catch (Throwable e) {
            System.out.println("[FarLands] aquifer fallback at " + pos + ": " + e);
            return farlands$noOp(fluidRule);
        }
        return new Aquifer() {
            public BlockState computeSubstance(DensityFunction.FunctionContext ctx, double d) {
                try { return real.computeSubstance(ctx, d); }
                catch (Throwable e) {
                    long n = farlands$aquiferFailures.incrementAndGet();
                    if (n <= 5 || n % 10000 == 0) {
                        System.out.println("[FarLands] aquifer computeSubstance fallback n=" + n
                            + " at " + ctx.blockX() + "," + ctx.blockY() + "," + ctx.blockZ()
                            + ": " + e);
                        System.out.flush();
                    }
                    return fluidRule.computeFluid(ctx.blockX(), ctx.blockY(), ctx.blockZ()).at(ctx.blockY());
                }
            }
            public boolean shouldScheduleFluidUpdate() {
                try { return real.shouldScheduleFluidUpdate(); }
                catch (Throwable e) { return false; }
            }
        };
    }

    private static Aquifer farlands$noOp(Aquifer.FluidPicker fluidRule) {
        return new Aquifer() {
            public BlockState computeSubstance(DensityFunction.FunctionContext ctx, double d) {
                return fluidRule.computeFluid(ctx.blockX(), ctx.blockY(), ctx.blockZ()).at(ctx.blockY());
            }
            public boolean shouldScheduleFluidUpdate() { return false; }
        };
    }
}
