package com.farlands.g2.client;

import com.farlands.g2.client.config.FarLandsGlobalConfig;
import com.farlands.g2.client.config.FarLandsOption;
import com.farlands.g2.client.config.FarLandsPresets;
import com.farlands.g2.util.FarConfig;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Global settings screen (UI/UX master plan P1).
 *
 * <p>Only options that G2 actually implements are listed (placeholder entries
 * from the MFS inventory stay out until their features land). Every row is 34px
 * tall: single-line widgets for BOOL/ENUM, label-above-box for TEXT so no text
 * is ever clipped. Tooltips carry the raw config key plus a one-line
 * description.</p>
 */
public class FarLandsSettingsScreen extends Screen {

    private static final String[][] CATEGORIES = {
        {"世界与迁移", "World"}, {"地形采样", "Sampling"}, {"行为", "Behavior"}, {"高级", "Advanced"},
    };

    private static final java.util.List<FarLandsOption> OPTIONS = java.util.List.of(
        new FarLandsOption("auto_relocate", "自动重定位", "Auto relocate",
            "接近窗口边缘时自动重定位（无缝继续）",
            "Auto-relocate near the window edge",
            FarLandsOption.Type.BOOL, FarLandsOption.Scope.WORLD, "true"),
        new FarLandsOption("relocate_margin", "重定位余量（格）", "Relocate margin",
            "距边缘多少格触发重定位；不建议低于 10000",
            "Trigger distance to the edge in blocks; keep >= 10000",
            FarLandsOption.Type.TEXT, FarLandsOption.Scope.WORLD, "100000"),

        new FarLandsOption("worldgen_sample_mode", "采样模式", "Sample mode",
            "远域采样策略：raw 原样 / clamp 钳制 / quantize 量化",
            "Far-domain sampling: raw / clamp / quantize",
            FarLandsOption.Type.ENUM, FarLandsOption.Scope.WORLD, "raw", "raw", "clamp", "quantize"),
        new FarLandsOption("worldgen_sample_clamp", "采样钳制值", "Sample clamp",
            "clamp 模式的钳制值（正有限数）",
            "Clamp value for clamp mode (positive finite)",
            FarLandsOption.Type.TEXT, FarLandsOption.Scope.WORLD, "1e300"),
        new FarLandsOption("worldgen_far_threshold", "远域阈值（格）", "Far threshold",
            "该距离之外才应用采样策略（0 = 处处应用）",
            "Sampling policy applies beyond this block distance (0 = everywhere)",
            FarLandsOption.Type.TEXT, FarLandsOption.Scope.WORLD, "9007199254740992"),

        new FarLandsOption("fluid_tick_limit", "流体 tick 上限", "Fluid tick limit",
            "每游戏 tick 流体更新上限（0 = 无限）",
            "Fluid updates per tick (0 = unlimited)",
            FarLandsOption.Type.TEXT, FarLandsOption.Scope.GLOBAL, "2000"),
        new FarLandsOption("debug", "调试级别", "Debug level",
            "日志级别 0-3；3 = 采样变换日志（硬顶 2000 行）",
            "Log level 0-3; 3 = sampling-transform log (2000-line cap)",
            FarLandsOption.Type.ENUM, FarLandsOption.Scope.GLOBAL, "0", "0", "1", "2", "3"),

        new FarLandsOption("config_mode", "配置模式", "Config mode",
            "strict：非法值中止加载（默认）；lenient：警告并回退默认",
            "strict aborts on invalid values (default); lenient warns and reverts",
            FarLandsOption.Type.ENUM, FarLandsOption.Scope.GLOBAL, "strict", "strict", "lenient"),
        new FarLandsOption("pro_sample_offset_x", "采样偏移 X（解锁）", "Sample offset X (unlock)",
            "实验性：采样域的 X 偏移（需解锁）",
            "Experimental: sampling offset X (requires unlock)",
            FarLandsOption.Type.TEXT, FarLandsOption.Scope.GLOBAL, "0"),
        new FarLandsOption("pro_sample_offset_z", "采样偏移 Z（解锁）", "Sample offset Z (unlock)",
            "实验性：采样域的 Z 偏移（需解锁）",
            "Experimental: sampling offset Z (requires unlock)",
            FarLandsOption.Type.TEXT, FarLandsOption.Scope.GLOBAL, "0"),
        new FarLandsOption("pro_sample_scale", "采样缩放（解锁）", "Sample scale (unlock)",
            "实验性：采样缩放（> 0；>1 放大，<1 缩小）",
            "Experimental: sampling scale (> 0)",
            FarLandsOption.Type.TEXT, FarLandsOption.Scope.GLOBAL, "1")
    );

    private static final String[] PRESETS = {"P1", "P2", "P3", "P4", "P5", "P6", "P7"};
    private static final int ROW_HEIGHT = 34;

    private final Screen parent;
    private int category;
    private int page;
    private CycleButton<String> presetCycle;
    private final Map<String, Checkbox> checks = new HashMap<>();
    private final Map<String, CycleButton<String>> cycles = new HashMap<>();
    private final Map<String, EditBox> edits = new HashMap<>();

    public FarLandsSettingsScreen(Screen parent) {
        super(FarLandsI18n.t("全局设置", "Global Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        checks.clear();
        cycles.clear();
        edits.clear();

        for (int i = 0; i < CATEGORIES.length; i++) {
            final int index = i;
            Button button = Button.builder(FarLandsI18n.t(CATEGORIES[i][0], CATEGORIES[i][1]), b -> {
                this.category = index;
                this.page = 0;
                this.rebuildWidgets();
            }).bounds(10, 50 + i * 24, 92, 20).build();
            addRenderableWidget(button);
        }

        FarLandsGlobalConfig config = FarLandsGlobalConfig.get();
        presetCycle = CycleButton.builder(s -> Component.literal(s), config.preset)
            .withValues(java.util.List.of(PRESETS))
            .create(118, 22, 96, 20, FarLandsI18n.t("预设", "Preset"));
        presetCycle.setTooltip(Tooltip.create(Component.literal(FarLandsPresets.describe(config.preset))));
        addRenderableWidget(presetCycle);
        addRenderableWidget(Button.builder(FarLandsI18n.t("应用", "Apply"), b -> {
            int applied = FarLandsPresets.apply(presetCycle.getValue(), config);
            this.rebuildWidgets();
            System.out.println("[FarLands-G2] preset " + presetCycle.getValue()
                + " applied (" + applied + " value(s); press Save to persist)");
        }).bounds(218, 22, 56, 20).build());

        StringWidget source = new StringWidget(FarLandsI18n.t(
            "来源：全局 JSON（-D/世界覆盖见角标与文档）",
            "Source: global JSON (see badges & docs for -D/world)"), font);
        source.setX(284);
        source.setY(26);
        addRenderableWidget(source);

        java.util.List<FarLandsOption> rows = optionsFor(category);
        int rowsPerPage = Math.max(2, (height - 64 - 52) / ROW_HEIGHT);
        int pages = Math.max(1, (rows.size() + rowsPerPage - 1) / rowsPerPage);
        if (page >= pages) {
            page = pages - 1;
        }
        int from = page * rowsPerPage;
        int to = Math.min(rows.size(), from + rowsPerPage);

        int x = 118;
        int y = 52;
        int width = Math.min(260, this.width - x - 16);
        for (int i = from; i < to; i++) {
            FarLandsOption option = rows.get(i);
            Component label = labelFor(option);
            Tooltip tooltip = Tooltip.create(Component.literal(option.key() + "\n"
                + FarLandsI18n.t(option.descCn(), option.descEn()).getString()));
            switch (option.type()) {
                case BOOL -> {
                    Checkbox box = Checkbox.builder(label, font)
                        .pos(x, y + 7)
                        .selected(config.getBool(option.key(), Boolean.parseBoolean(option.def())))
                        .tooltip(tooltip)
                        .build();
                    checks.put(option.key(), box);
                    addRenderableWidget(box);
                }
                case ENUM -> {
                    CycleButton<String> cycle = CycleButton.builder(s -> Component.literal(s),
                            config.get(option.key(), option.def()))
                        .withValues(java.util.List.of(option.choices()))
                        .create(x, y + 7, width, 20, label);
                    cycle.setTooltip(tooltip);
                    cycles.put(option.key(), cycle);
                    addRenderableWidget(cycle);
                }
                case TEXT -> {
                    StringWidget rowLabel = new StringWidget(label, font);
                    rowLabel.setX(x);
                    rowLabel.setY(y);
                    addRenderableWidget(rowLabel);
                    EditBox box = new EditBox(font, width, 18, Component.empty());
                    box.setValue(config.get(option.key(), option.def()));
                    box.setTooltip(tooltip);
                    box.setX(x);
                    box.setY(y + 14);
                    edits.put(option.key(), box);
                    addRenderableWidget(box);
                }
            }
            y += ROW_HEIGHT;
        }

        if (pages > 1) {
            final int current = page;
            Button prev = Button.builder(Component.literal("<"), b -> {
                this.page = Math.max(0, current - 1);
                this.rebuildWidgets();
            }).bounds(118, height - 56, 24, 20).build();
            Button next = Button.builder(Component.literal(">"), b -> {
                this.page = Math.min(pages - 1, current + 1);
                this.rebuildWidgets();
            }).bounds(146, height - 56, 24, 20).build();
            addRenderableWidget(prev);
            addRenderableWidget(next);
            StringWidget pageInfo = new StringWidget(Component.literal((page + 1) + "/" + pages), font);
            pageInfo.setX(176);
            pageInfo.setY(height - 50);
            addRenderableWidget(pageInfo);
        }

        if (FarConfig.lenientMode() && !FarConfig.warnings().isEmpty()) {
            StringWidget warn = new StringWidget(FarLandsI18n.t(
                "宽松模式：" + FarConfig.warnings().size() + " 个非法值已回退默认",
                "Lenient: " + FarConfig.warnings().size() + " invalid value(s) reverted"), font);
            warn.setX(224);
            warn.setY(height - 50);
            addRenderableWidget(warn);
        }

        addRenderableWidget(Button.builder(FarLandsI18n.t("保存", "Save"), b -> saveAll())
            .bounds(width / 2 - 105, height - 30, 100, 20).build());
        addRenderableWidget(Button.builder(FarLandsI18n.t("完成", "Done"), b -> onClose())
            .bounds(width / 2 + 5, height - 30, 100, 20).build());
    }

    private static Component labelFor(FarLandsOption option) {
        StringBuilder suffix = new StringBuilder();
        if (option.scope() == FarLandsOption.Scope.WORLD) {
            suffix.append(FarLandsI18n.zh() ? " [世界级]" : " [world]");
        }
        if (System.getProperty("farlands." + option.key()) != null
            || System.getProperty("farlands." + option.key().replace('.', '_')) != null) {
            suffix.append(" [-D]");
        }
        return FarLandsI18n.t(option.cn() + suffix, option.en() + suffix);
    }

    private static java.util.List<FarLandsOption> optionsFor(int category) {
        java.util.List<FarLandsOption> out = new java.util.ArrayList<>();
        for (FarLandsOption option : OPTIONS) {
            if (family(option.key()) == category) {
                out.add(option);
            }
        }
        return out;
    }

    private static int family(String key) {
        if (key.startsWith("epoch_") || key.startsWith("auto_relocate") || key.startsWith("relocate_")) {
            return 0;
        }
        if (key.startsWith("worldgen_")) {
            return 1;
        }
        if (key.startsWith("fluid_tick") || key.startsWith("debug")) {
            return 2;
        }
        return 3;
    }

    private void saveAll() {
        FarLandsGlobalConfig config = FarLandsGlobalConfig.get();
        checks.forEach((key, box) -> config.set(key, Boolean.toString(box.selected())));
        cycles.forEach((key, cycle) -> config.set(key, cycle.getValue()));
        edits.forEach((key, box) -> config.set(key, box.getValue()));
        if (presetCycle != null) {
            config.preset = presetCycle.getValue();
        }
        config.save();
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreenAndShow(parent);
        }
    }
}
