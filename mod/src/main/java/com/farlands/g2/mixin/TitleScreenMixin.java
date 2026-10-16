package com.farlands.g2.mixin;

import com.farlands.g2.client.FarLandsI18n;
import com.farlands.g2.client.FarLandsSettingsScreen;
import com.farlands.g2.client.FarLandsTestMachineScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Title-screen entries (UI/UX master plan P0): [测试机] next to Singleplayer,
 * [全局设置] next to Multiplayer. Narrow, adaptive-width buttons (min 56);
 * English labels kept short so the same compact look works in both languages.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {

    private static final int MIN_WIDTH = 56;
    private static final int PADDING = 14;

    @Inject(method = "init", at = @At("TAIL"))
    private void farlands$addEntries(CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        Minecraft minecraft = Minecraft.getInstance();
        Component testLabel = FarLandsI18n.t("测试机", "Test Rig");
        Component settingsLabel = FarLandsI18n.t("全局设置", "Settings");
        int x = self.width / 2 + 104;
        int y = self.height / 4 + 48;
        ScreenAccessor accessor = (ScreenAccessor) (Object) this;
        accessor.farlands$addRenderableWidget(Button.builder(testLabel,
            b -> minecraft.setScreenAndShow(new FarLandsTestMachineScreen(self)))
            .bounds(x, y, width(minecraft, testLabel), 20).build());
        accessor.farlands$addRenderableWidget(Button.builder(settingsLabel,
            b -> minecraft.setScreenAndShow(new FarLandsSettingsScreen(self)))
            .bounds(x, y + 24, width(minecraft, settingsLabel), 20).build());
    }

    private static int width(Minecraft minecraft, Component label) {
        return Math.max(MIN_WIDTH, minecraft.font.width(label) + PADDING);
    }
}
