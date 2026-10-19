package com.farlands.g2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Diagnostic: the first time a tick is scheduled at a block position near the
 * int boundary (|x| >= 2e9), dump the caller stack. Vanilla logs "Trying to
 * schedule tick in not loaded position" for these without a source; this is
 * how the wrapping consumer is located. One-shot; no behaviour change.
 */
@Mixin(LevelTicks.class)
public class ScheduleTickDiagMixin {

    private static final AtomicBoolean farlands$logged = new AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicInteger farlands$count =
        new java.util.concurrent.atomic.AtomicInteger();

    @Inject(method = "schedule", at = @At("HEAD"))
    private void farlands$diagSchedule(ScheduledTick<?> tick, CallbackInfo ci) {
        BlockPos pos = tick.pos();
        int x = pos.getX();
        if (Math.abs((long) pos.getLongX()) < 2_147_483_600L
            && Math.abs((long) pos.getLongZ()) < 2_147_483_600L) {
            return;
        }
        int n = farlands$count.incrementAndGet();
        if (n > 20) {
            return;
        }
        System.out.println("[FarLands-G2][diag] scheduleTick n=" + n
            + " pos=" + pos + " longX=" + pos.getLongX() + " intX=" + pos.getX()
            + " longZ=" + pos.getLongZ() + " intZ=" + pos.getZ()
            + " chunk=" + new net.minecraft.world.level.ChunkPos(
                net.minecraft.core.SectionPos.blockToSectionCoord(pos.getX()),
                net.minecraft.core.SectionPos.blockToSectionCoord(pos.getZ())));
        if (farlands$logged.compareAndSet(false, true)) {
            new Throwable("schedule-tick-caller").printStackTrace(System.out);
        }
        System.out.flush();
    }
}
