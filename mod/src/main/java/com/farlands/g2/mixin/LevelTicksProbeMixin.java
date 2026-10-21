package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Temporary probe: find who schedules ticks with int-view negative block positions. */
@Mixin(LevelTicks.class)
public class LevelTicksProbeMixin {

    @Unique private static long farlands$last;

    @Inject(method = "schedule", at = @At("HEAD"))
    private void farlands$probe(ScheduledTick<?> tick, CallbackInfo ci) {
        BlockPos p = tick.pos();
        long x = p.getLongX();
        if (x < -2_000_000_000L && x > -2_147_483_648L) {
            long now = System.currentTimeMillis();
            if (now - farlands$last < 2000L) {
                return;
            }
            farlands$last = now;
            System.out.println("[TICKPROBE] pos=" + p + " longX=" + x + " type=" + tick.type());
            new Throwable("tick schedule caller").printStackTrace(System.out);
            System.out.flush();
        }
    }
}
