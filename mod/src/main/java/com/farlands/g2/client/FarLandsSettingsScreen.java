package com.farlands.g2.client;

import com.farlands.g2.client.config.FarLandsGlobalConfig;
import com.farlands.g2.client.config.FarLandsOption;
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
 * Global settings screen (UI/UX master plan P0 skeleton).
 *
 * <p>Left categories + semantic widgets (Checkbox / CycleButton / EditBox) with
 * the raw config key in each tooltip. Ten representative options across the
 * merged families; the full set arrives in P1. Declarative widgets only (26.2
 * moved the GUI drawing API, so no custom render override here).</p>
 */
public class FarLandsSettingsScreen extends Screen {

    private static final String[][] CATEGORIES = {
        {"风格", "Style"}, {"地形工具", "Terrain"}, {"内容开关", "Content"}, {"修复", "Fixes"}, {"高级", "Advanced"},
    };

    private static final FarLandsOption[] OPTIONS = {
        new FarLandsOption("farlands_style", "边境风格", "Farlands style", FarLandsOption.Type.ENUM,
            "java_1_18_2p", "java_1_18_2p", "bedrock", "beta_12550821", "vanilla_2e88", "release_2e63", "exp_snapshot_4"),
        new FarLandsOption("precision_mode", "精度模式", "Precision mode", FarLandsOption.Type.ENUM,
            "32bit", "32bit", "wide"),
        new FarLandsOption("progressive_farlands", "渐进式边境", "Progressive farlands", FarLandsOption.Type.BOOL, "false"),
        new FarLandsOption("terrain_scaler.enabled", "地形缩放假器", "Terrain scaler", FarLandsOption.Type.BOOL, "false"),
        new FarLandsOption("terrain_offset.enabled", "地形偏移", "Terrain offset", FarLandsOption.Type.BOOL, "false"),
        new FarLandsOption("x_world_scaler", "X 缩放系数", "X scaler factor", FarLandsOption.Type.TEXT, "1"),
        new FarLandsOption("skygrid.enabled", "天空网格", "SkyGrid", FarLandsOption.Type.BOOL, "false"),
        new FarLandsOption("disable_structure_spawn", "禁用结构生成", "Disable structure spawn", FarLandsOption.Type.BOOL, "false"),
        new FarLandsOption("fix_climate_float_precision", "修复气候浮点精度", "Fix climate float precision", FarLandsOption.Type.BOOL, "false"),
        new FarLandsOption("config_mode", "配置模式", "Config mode", FarLandsOption.Type.ENUM,
            "strict", "strict", "lenient"),
    };

    private final Screen parent;
    private int category;
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
                this.rebuildWidgets();
            }).bounds(10, 50 + i * 24, 92, 20).build();
            addRenderableWidget(button);
        }

        StringWidget source = new StringWidget(FarLandsI18n.t(
            "生效来源：全局 JSON（-D / 每世界覆盖见文档）",
            "Source: global JSON (-D / per-world overrides in docs)"), font);
        source.setX(118);
        source.setY(26);
        addRenderableWidget(source);

        FarLandsGlobalConfig config = FarLandsGlobalConfig.get();
        int x = 118;
        int y = 52;
        int width = 220;
        for (FarLandsOption option : OPTIONS) {
            if (family(option.key()) != category) {
                continue;
            }
            Component label = FarLandsI18n.t(option.cn(), option.en());
            Tooltip tooltip = Tooltip.create(Component.literal(option.key()));
            switch (option.type()) {
                case BOOL -> {
                    Checkbox box = Checkbox.builder(label, font)
                        .pos(x, y)
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
                        .create(x, y, width, 20, label);
                    cycle.setTooltip(tooltip);
                    cycles.put(option.key(), cycle);
                    addRenderableWidget(cycle);
                }
                case TEXT -> {
                    EditBox box = new EditBox(font, width, 20, label);
                    box.setValue(config.get(option.key(), option.def()));
                    box.setHint(Component.literal(option.key()));
                    box.setTooltip(tooltip);
                    box.setX(x);
                    box.setY(y);
                    edits.put(option.key(), box);
                    addRenderableWidget(box);
                }
            }
            y += 26;
        }

        addRenderableWidget(Button.builder(FarLandsI18n.t("保存", "Save"), b -> saveAll())
            .bounds(width / 2 - 105, height - 30, 100, 20).build());
        addRenderableWidget(Button.builder(FarLandsI18n.t("完成", "Done"), b -> onClose())
            .bounds(width / 2 + 5, height - 30, 100, 20).build());
    }

    /** P0 mapping: which family each representative option belongs to. */
    private static int family(String key) {
        if (key.startsWith("farlands_style") || key.startsWith("precision_mode") || key.startsWith("progressive")) {
            return 0;
        }
        if (key.startsWith("terrain_") || key.endsWith("_world_scaler")) {
            return 1;
        }
        if (key.startsWith("skygrid") || key.startsWith("disable_")) {
            return 2;
        }
        if (key.startsWith("fix_")) {
            return 3;
        }
        return 4;
    }

    private void saveAll() {
        FarLandsGlobalConfig config = FarLandsGlobalConfig.get();
        checks.forEach((key, box) -> config.set(key, Boolean.toString(box.selected())));
        cycles.forEach((key, cycle) -> config.set(key, cycle.getValue()));
        edits.forEach((key, box) -> config.set(key, box.getValue()));
        config.save();
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreenAndShow(parent);
        }
    }
}
