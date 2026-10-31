package com.farlands.g2.mixin;

import com.farlands.g2.client.WideClientChunkStore;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * W1-4b2a: visible HUD top view of the decoded far chunk. This is a debug view,
 * not the 3D world renderer; it proves the wide sections are usable on the
 * client before the wide render pass exists.
 */
@Mixin(Gui.class)
public class WideChunkHudMixin {

    @Shadow private GuiRenderState guiRenderState;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void farlands$wideHud(DeltaTracker deltaTracker, boolean a, boolean b,
        CallbackInfo ci) {
        if (!Boolean.getBoolean("farlands.diag.clientWideChunk")) {
            return;
        }
        if (!Boolean.parseBoolean(System.getProperty("farlands.diag.hud", "true"))) {
            return;
        }
        WideClientChunkStore.Data data = WideClientChunkStore.current();
        if (data == null || data.sections().length == 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        String[] lines = new String[18];
        lines[0] = "WIDE " + data.pos() + " minSy=" + data.minSectionY();
        for (int z = 0; z < 16; z++) {
            StringBuilder row = new StringBuilder(16);
            for (int x = 0; x < 16; x++) {
                row.append(farlands$topChar(data, x, z));
            }
            lines[z + 1] = row.toString();
        }
        lines[17] = "HUD map: not 3D render";
        Matrix3x2f pose = new Matrix3x2f();
        for (int i = 0; i < lines.length; i++) {
            guiRenderState.addText(new GuiTextRenderState(font,
                Component.literal(lines[i]).getVisualOrderText(), pose,
                8, 8 + i * 9, 0xFFFFFFFF, 0, true, false, null));
        }
        System.out.println("[WIDEHUD] " + lines[0] + " rows=16");
        System.out.flush();
    }

    private static char farlands$topChar(WideClientChunkStore.Data data, int x, int z) {
        for (int idx = data.sections().length - 1; idx >= 0; idx--) {
            var section = data.sections()[idx];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }
            for (int ly = 15; ly >= 0; ly--) {
                var state = section.getBlockState(x, ly, z);
                if (state.isAir()) {
                    continue;
                }
                if (state.is(Blocks.WATER)) {
                    return '~';
                }
                if (state.is(Blocks.STONE)) {
                    return '#';
                }
                if (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)
                    || state.is(Blocks.SAND) || state.is(Blocks.GRAVEL)) {
                    return 'g';
                }
                return '?';
            }
        }
        return '.';
    }
}
