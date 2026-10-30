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
    @Unique private static boolean farlands$shot;
    @Unique private static int farlands$farFrames;

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
        int minSy = data.minSectionY();
        int drawn = 0;
        try (Gizmos.TemporaryCollection collection =
                 mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
            for (int i = 0; i < data.sections().length; i++) {
                var section = data.sections()[i];
                if (section == null || section.hasOnlyAir()) {
                    continue;
                }
                int sy = minSy + i;
                double y0 = sy * 16.0;
                AABB box = new AABB(x, y0, z, x + 16.0, y0 + 16.0, z + 16.0);
                int color = section.getBlockState(8, 8, 8).is(net.minecraft.world.level.block.Blocks.WATER)
                    ? 0x8000FFFF : 0xC0AAAAAA;
                Gizmos.cuboid(box, GizmoStyle.stroke(color, 2.0F));
                drawn++;
            }
        }
        if (!farlands$loggedGizmo) {
            farlands$loggedGizmo = true;
            System.out.println("[WIDEGIZMO] sections=" + drawn + " at " + x + "," + z
                + " minSy=" + minSy);
            System.out.flush();
        }
        if (!farlands$shot && mc.player != null
            && Math.abs(mc.player.getX()) > 1.0E12) {
            if (++farlands$farFrames >= 3000) {
                farlands$shot = true;
                net.minecraft.client.Screenshot.grab(mc, false);
                System.out.println("[WIDESHOT] grabbed at " + mc.player.position());
                System.out.flush();
            }
        }
    }
}
