package com.farlands.g2.mixin;

import com.farlands.g2.client.FarLandsI18n;
import com.farlands.g2.client.FarLandsSettingsScreen;
import com.farlands.g2.client.FarLandsTestMachineScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Title-screen entries (UI/UX master plan P0): [测试机] next to Singleplayer,
 * [全局设置] next to Multiplayer. The rest of the config lives in the reusable
 * settings screen; create-world keeps world-scoped options only.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void farlands$addEntries(CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        int x = self.width / 2 + 104;
        int y = self.height / 4 + 48;
        ScreenAccessor accessor = (ScreenAccessor) (Object) this;
        accessor.farlands$addRenderableWidget(Button.builder(FarLandsI18n.t("测试机", "Test Machine"),
            b -> Minecraft.getInstance().setScreenAndShow(new FarLandsTestMachineScreen(self)))
            .bounds(x, y, 96, 20).build());
        accessor.farlands$addRenderableWidget(Button.builder(FarLandsI18n.t("全局设置", "Global Settings"),
            b -> Minecraft.getInstance().setScreenAndShow(new FarLandsSettingsScreen(self)))
            .bounds(x, y + 24, 96, 20).build());
    }
}
