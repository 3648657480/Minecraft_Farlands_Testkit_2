package com.farlands.g2.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Test machine entry (UI/UX master plan P0 stub, declarative widgets only).
 *
 * <p>P2 turns this into the full panel: testgen presets, fingerprint compare
 * view, /realtp shortcut and debug level toggles.</p>
 */
public class FarLandsTestMachineScreen extends Screen {

    private final Screen parent;

    public FarLandsTestMachineScreen(Screen parent) {
        super(FarLandsI18n.t("测试机", "Test Machine"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        StringWidget title = new StringWidget(FarLandsI18n.t(
            "测试机 · P2 落地中", "Test Machine · P2 pending"), font);
        title.setX(cx - 80);
        title.setY(50);
        addRenderableWidget(title);

        StringWidget hint = new StringWidget(FarLandsI18n.t(
            "现阶段请用命令行 testgen（见 docs/WORKFLOW.md）",
            "For now use the CLI testgen (see docs/WORKFLOW.md)"), font);
        hint.setX(cx - 120);
        hint.setY(70);
        addRenderableWidget(hint);

        StringWidget plan = new StringWidget(FarLandsI18n.t(
            "规划：testgen 面板 / wgHash 指纹对比 / /realtp / debug 分级",
            "Planned: testgen panel / wgHash compare / /realtp / debug levels"), font);
        plan.setX(cx - 140);
        plan.setY(90);
        addRenderableWidget(plan);

        addRenderableWidget(Button.builder(FarLandsI18n.t("完成", "Done"), b -> onClose())
            .bounds(cx - 50, height - 30, 100, 20).build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreenAndShow(parent);
        }
    }
}
