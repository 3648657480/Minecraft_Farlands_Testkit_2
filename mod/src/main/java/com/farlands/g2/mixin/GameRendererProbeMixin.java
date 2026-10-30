package com.farlands.g2.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minimal check that the per-frame GameRenderer entry is mixin-reachable. */
@Mixin(GameRenderer.class)
public class GameRendererProbeMixin {

    @Unique private static boolean farlands$logged;
    @Unique private static boolean farlands$loggedGizmo;

    @Inject(method = "render", at = @At("HEAD"))
    private void farlands$probe(CallbackInfo ci) {
        if (!farlands$logged) {
            farlands$logged = true;
            System.out.println("[GRENDER] head reached");
            System.out.flush();
        }
        if (!Boolean.getBoolean("farlands.diag.clientWideChunk")) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer == null) {
            return;
        }
        var data = com.farlands.g2.client.WideClientChunkStore.current();
        if (data == null) {
            return;
        }
        double x = data.pos().x().toBigInteger().doubleValue() * 16.0;
        double z = data.pos().z().toBigInteger().doubleValue() * 16.0;
        double y = data.minSectionY() * 16.0;
        double height = data.sections().length * 16.0;
        AABB box = new AABB(x, y, z, x + 16.0, y + height, z + 16.0);
        try (Gizmos.TemporaryCollection collection =
                 mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
            Gizmos.cuboid(box, GizmoStyle.stroke(0xFF00FF00, 4.0F));
        }
        if (!farlands$loggedGizmo) {
            farlands$loggedGizmo = true;
            System.out.println("[WIDEGIZMO] box=" + box);
            System.out.flush();
        }
    }
}
