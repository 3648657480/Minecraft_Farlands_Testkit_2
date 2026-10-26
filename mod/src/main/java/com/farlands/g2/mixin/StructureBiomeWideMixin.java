package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * W-2: structure biome validation read {@code position().getX()/getY()/getZ()}
 * (int views). At far X the int view wraps, so the structure sampled the biome
 * of a different place. Derive the quart coordinates from the long position.
 */
@Mixin(Structure.class)
public class StructureBiomeWideMixin {

    @Inject(method = "isValidBiome", at = @At("HEAD"), cancellable = true)
    private static void farlands$wideBiome(Structure.GenerationStub stub,
        Structure.GenerationContext context, CallbackInfoReturnable<Boolean> cir) {
        BlockPos pos = stub.position();
        int quartX = (int) (pos.getLongX() >> 2);
        int quartY = (int) (pos.getLongY() >> 2);
        int quartZ = (int) (pos.getLongZ() >> 2);
        cir.setReturnValue(context.validBiome().test(
            context.chunkGenerator().getBiomeSource()
                .getNoiseBiome(quartX, quartY, quartZ, context.randomState().sampler())));
    }
}
