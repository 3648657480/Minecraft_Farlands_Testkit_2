package com.farlands.g2.mixin;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Diagnostic: logs who re-centres the view grid and with which section. The far
 * grid must always be re-centred with the <b>local</b> camera section; a caller
 * that passes a world or stale section makes the grid re-centre every frame and
 * re-key the whole set (observed: thousands of KEYREKEY, no rendering).
 */
@Mixin(ViewArea.class)
public class ViewAreaRepositionDiagMixin {

    @Unique private static int farlands$calls;

    @Inject(method = "repositionCamera", at = @At("HEAD"))
    private void farlands$logReposition(SectionPos cameraSectionPos,
        CallbackInfoReturnable<Boolean> cir) {
        if (farlands$calls++ >= 24) {
            return;
        }
        StringBuilder sb = new StringBuilder("[VAREPOS] center=")
            .append(cameraSectionPos.x()).append(",").append(cameraSectionPos.y())
            .append(",").append(cameraSectionPos.z());
        int shown = 0;
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            String cls = e.getClassName();
            if (cls.equals(ViewArea.class.getName())
                || cls.equals("java.lang.Thread")) {
                continue;
            }
            if (cls.startsWith("net.minecraft.client.") || cls.startsWith("com.farlands")) {
                sb.append(" <- ").append(cls).append("#").append(e.getMethodName());
                if (++shown >= 3) {
                    break;
                }
            }
        }
        System.out.println(sb);
        System.out.flush();
    }
}
