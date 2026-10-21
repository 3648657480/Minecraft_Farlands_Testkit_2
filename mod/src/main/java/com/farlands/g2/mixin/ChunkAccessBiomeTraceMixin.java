package com.farlands.g2.mixin;

import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Opt-in trace of which far chunk is currently collecting surface biomes. */
@Mixin(ChunkAccess.class)
public class ChunkAccessBiomeTraceMixin {

    private static final boolean TRACE = Boolean.getBoolean("farlands.surface.trace");

    @Inject(method = "collectBiomesInPalette", at = @At("HEAD"))
    private void farlands$chunk(Set<Holder<Biome>> out, CallbackInfo ci) {
        if (!TRACE) return;
        ChunkAccess self = (ChunkAccess) (Object) this;
        long x = self.getPos().xLong();
        if (x < 2_000_000_000L) return;
        System.out.println("[SURF] chunk " + x + "," + self.getPos().zLong()
            + " sections=" + self.getSections().length);
        System.out.flush();
    }
}
