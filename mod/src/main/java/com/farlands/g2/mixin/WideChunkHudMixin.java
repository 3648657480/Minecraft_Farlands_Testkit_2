package com.farlands.g2.mixin;

import com.farlands.g2.runtime.FarLightMode;
import com.farlands.g2.client.RenderKeys;
import com.farlands.g2.client.WideClientChunkStore;
import com.farlands.g2.client.WideRenderBase;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Far-domain diagnostic table (opt-in, {@code -Dfarlands.diag.hud=true}).
 *
 * <p>Replaces the old 16x16 character "top view". That view existed to prove the
 * client decoded far chunks before the 3D far renderer existed; now that the
 * renderer works it only added a coarse map with five glyphs ({@code ~} water,
 * {@code #} stone, {@code g} soil, {@code ?} other, {@code .} air) which read as
 * "the HUD is full of g" on any grassy area.</p>
 *
 * <p>The table reports the numbers that actually decide whether the far domain
 * works: the render frame (base/local), the decoded data window, the render set
 * and the far-light guard.</p>
 */
@Mixin(Gui.class)
public class WideChunkHudMixin {

    @Shadow private GuiRenderState guiRenderState;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void farlands$diagTable(DeltaTracker deltaTracker, boolean a, boolean b,
        CallbackInfo ci) {
        if (!Boolean.parseBoolean(System.getProperty("farlands.diag.hud", "false"))) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.font == null) {
            return;
        }
        String[] lines = farlands$lines(mc);
        Matrix3x2f pose = new Matrix3x2f();
        for (int i = 0; i < lines.length; i++) {
            guiRenderState.addText(new GuiTextRenderState(mc.font,
                Component.literal(lines[i]).getVisualOrderText(), pose,
                8, 8 + i * 9, 0xFFFFFFFF, 0, true, false, null));
        }
    }

    private static String[] farlands$lines(Minecraft mc) {
        double camX = mc.player.getX();
        double camZ = mc.player.getZ();
        String frame = WideRenderBase.established()
            ? WideRenderBase.describe(camX, camZ)
            : "not established";
        int storeChunks = WideClientChunkStore.all().size();
        WideClientChunkStore.Data cur = WideClientChunkStore.current();
        String curDesc = cur == null ? "-" : cur.pos() + " minSy=" + cur.minSectionY();
        int nodes = RenderKeys.nodeCount();
        int mesh = 0;
        int uncompiled = 0;
        try {
            for (var s : RenderKeys.all()) {
                var m = s.getSectionMesh();
                if (m == net.minecraft.client.renderer.chunk.CompiledSectionMesh.UNCOMPILED) {
                    uncompiled++;
                } else if (m.hasRenderableLayers()) {
                    mesh++;
                }
            }
        } catch (Throwable ignored) {
            // render types can be mid-rebuild; the table must never throw
        }
        int visible = mc.levelRenderer == null || mc.levelRenderer.visibleSections() == null
            ? -1 : mc.levelRenderer.visibleSections().size();
        // The decisive number: of the sections the frustum actually wants, how many are
        // still uncompiled. "uncompiled=1312" over ALL sections is mostly out-of-view
        // sections that legitimately stay uncompiled and hides the real backlog.
        int visUnc = 0;
        int visEmpty = 0;
        try {
            for (var s : mc.levelRenderer.visibleSections()) {
                var m = s.getSectionMesh();
                if (m == net.minecraft.client.renderer.chunk.CompiledSectionMesh.UNCOMPILED) {
                    visUnc++;
                } else if (!m.hasRenderableLayers()) {
                    visEmpty++;
                }
            }
        } catch (Throwable ignored) {
            visUnc = -1;
        }
        long x = (long) Math.floor(camX);
        long z = (long) Math.floor(camZ);
        return new String[] {
            "[G2] far diagnostics",
            "pos    " + x + ", " + z,
            "frame  " + frame,
            "data   chunks=" + storeChunks + "  cur=" + curDesc,
            "render visible=" + visible + "  sections=" + nodes
                + "  mesh=" + mesh + "  uncompiled=" + uncompiled,
            "vis    uncompiled=" + visUnc + "  empty=" + visEmpty
                + "  missing=" + Math.max(0, visible - visUnc - visEmpty - mesh),
            "light  far=" + FarLightMode.active(),
        };
    }
}
