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

    @Inject(method = "schedule", at = @At("HEAD"))
    private void farlands$diagSchedule(ScheduledTick<?> tick, CallbackInfo ci) {
        BlockPos pos = tick.pos();
        int x = pos.getX();
        if (x < 2_000_000_000 && x > -2_000_000_000) {
            return;
        }
        if (!farlands$logged.compareAndSet(false, true)) {
            return;
        }
        System.out.println("[FarLands-G2][diag] scheduleTick pos=(" + x + ","
            + pos.getY() + "," + pos.getZ() + ")");
        new Throwable("schedule-tick-caller").printStackTrace(System.out);
        System.out.flush();
    }
}
