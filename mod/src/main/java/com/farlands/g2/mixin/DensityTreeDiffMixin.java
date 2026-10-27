package com.farlands.g2.mixin;

import com.farlands.g2.runtime.RealContext;
import com.farlands.g2.util.FarProjection;
import java.lang.reflect.Field;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Diagnostic: diff the wrapped density tree against the raw router tree. */
@Mixin(NoiseChunk.class)
public class DensityTreeDiffMixin {

    @Shadow private DensityFunction fullNoiseDensity;
    @Unique private static boolean farlands$done;
    @Unique private static int farlands$budget;

    @Inject(method = "getInterpolatedState", at = @At("HEAD"))
    private void farlands$diff(CallbackInfoReturnable<?> cir) {
        if (farlands$done || !Boolean.getBoolean("farlands.diag.treediff") || !FarProjection.isFarOrigin()) {
            return;
        }
        DensityFunction raw = FarProjection.rawFinal();
        if (raw == null) {
            return;
        }
        farlands$done = true;
        try {
            Object cache = this.fullNoiseDensity;
            java.lang.reflect.Field fillerField = cache.getClass().getDeclaredField("noiseFiller");
            fillerField.setAccessible(true);
            Object add = fillerField.get(cache);
            java.lang.reflect.Field a1 = add.getClass().getDeclaredField("argument1");
            a1.setAccessible(true);
            DensityFunction wrappedFinal = (DensityFunction) a1.get(add);
            System.out.println("[TREEDIFF] wrappedFinal=" + wrappedFinal.getClass().getName()
                + " value=" + wrappedFinal.compute(new RealContext(2147483664L, 120L, 292L))
                + " rawValue=" + raw.compute(new RealContext(2147483664L, 120L, 292L)));
        } catch (Throwable t) {
            System.out.println("[TREEDIFF] reflect failed: " + t);
        }
        farlands$budget = 400;
        RealContext ctx = new RealContext(2147483664L, 120L, 292L);
        System.out.println("[TREEDIFF] wrapped=" + this.fullNoiseDensity.getClass().getName()
            + " raw=" + raw.getClass().getName());
        farlands$walk(this.fullNoiseDensity, raw, "root", ctx, 0);
        System.out.flush();
    }

    @Unique
    private static DensityFunction farlands$unwrap(DensityFunction fn) {
        int guard = 0;
        while (fn instanceof DensityFunctions.MarkerOrMarked marked && guard++ < 8) {
            DensityFunction next = marked.wrapped();
            if (next == fn) {
                break;
            }
            fn = next;
        }
        return fn;
    }

    @Unique
    private static void farlands$walk(DensityFunction w, DensityFunction r, String path,
        RealContext ctx, int depth) {
        if (depth > 10 || farlands$budget-- <= 0) {
            return;
        }
        w = farlands$unwrap(w);
        r = farlands$unwrap(r);
        if (w.getClass() != r.getClass()) {
            System.out.println("[TREEDIFF] class mismatch at " + path + ": wrapped="
                + w.getClass().getName() + " raw=" + r.getClass().getName());
            return;
        }
        double vw = w.compute(ctx);
        double vr = r.compute(ctx);
        if (Double.isFinite(vw) && Double.isFinite(vr) && Math.abs(vw - vr) > 1.0e-6) {
            System.out.println("[TREEDIFF] value mismatch at " + path + " " + w.getClass().getSimpleName()
                + " wrapped=" + vw + " raw=" + vr);
            return;
        }
        for (Field f : w.getClass().getDeclaredFields()) {
            if (!DensityFunction.class.isAssignableFrom(f.getType())) {
                continue;
            }
            try {
                f.setAccessible(true);
                DensityFunction cw = (DensityFunction) f.get(w);
                DensityFunction cr = (DensityFunction) f.get(r);
                if (cw != null && cr != null) {
                    farlands$walk(cw, cr, path + "." + f.getName(), ctx, depth + 1);
                }
            } catch (Throwable ignored) {
            }
        }
    }
}