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
 * Global settings screen (UI/UX master plan P1: full merged option set).
 *
 * <p>Five categories, pagination (9 rows per page), semantic widgets with the
 * raw config key in each tooltip, [世界级] / [-D] badges, and a lenient-mode
 * warning line (never silent). WORLD-scoped entries are marked here and will be
 * routed to the create-world draft once that hookup lands.</p>
 */
public class FarLandsSettingsScreen extends Screen {

    private static final String[][] CATEGORIES = {
        {"风格", "Style"}, {"地形工具", "Terrain"}, {"内容开关", "Content"}, {"修复", "Fixes"}, {"高级", "Advanced"},
    };

    private static final java.util.List<FarLandsOption> OPTIONS = java.util.List.of(
        // 风格 / Style
        new FarLandsOption("farlands_style", "边境风格", "Farlands style", FarLandsOption.Type.ENUM,
            FarLandsOption.Scope.WORLD, "java_1_18_2p", "java_1_18_2p", "bedrock", "beta_12550821", "vanilla_2e88", "release_2e63", "exp_snapshot_4"),
        new FarLandsOption("precision_mode", "精度模式", "Precision mode", FarLandsOption.Type.ENUM,
            FarLandsOption.Scope.WORLD, "32bit", "32bit", "wide"),
        new FarLandsOption("progressive_farlands", "渐进式边境", "Progressive farlands", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.WORLD, "false"),
        new FarLandsOption("exact_terrain_rewrite", "精确地形重写", "Exact terrain rewrite", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.WORLD, "false"),
        new FarLandsOption("simulated_wraparound_overflow", "模拟回绕溢出", "Simulated wraparound overflow", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.WORLD, "false"),
        new FarLandsOption("expand_bedrock_single_precision", "扩展基岩版单精度", "Expand Bedrock single precision", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.WORLD, "false"),

        // 地形工具 / Terrain tools
        new FarLandsOption("terrain_scaler.enabled", "地形缩放假器", "Terrain scaler", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.WORLD, "false"),
        new FarLandsOption("x_world_scaler", "X 缩放系数", "X scaler factor", FarLandsOption.Type.TEXT,
            FarLandsOption.Scope.WORLD, "1"),
        new FarLandsOption("y_world_scaler", "Y 缩放系数", "Y scaler factor", FarLandsOption.Type.TEXT,
            FarLandsOption.Scope.WORLD, "1"),
        new FarLandsOption("z_world_scaler", "Z 缩放系数", "Z scaler factor", FarLandsOption.Type.TEXT,
            FarLandsOption.Scope.WORLD, "1"),
        new FarLandsOption("terrain_offset.enabled", "地形偏移", "Terrain offset", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.WORLD, "false"),
        new FarLandsOption("x_world_offset", "X 偏移", "X offset", FarLandsOption.Type.TEXT,
            FarLandsOption.Scope.WORLD, "0"),
        new FarLandsOption("y_world_offset", "Y 偏移", "Y offset", FarLandsOption.Type.TEXT,
            FarLandsOption.Scope.WORLD, "0"),
        new FarLandsOption("z_world_offset", "Z 偏移", "Z offset", FarLandsOption.Type.TEXT,
            FarLandsOption.Scope.WORLD, "0"),
        new FarLandsOption("y_clamped_gradient_offset", "Y 梯度钳制偏移", "Y clamped gradient offset", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.WORLD, "false"),
        new FarLandsOption("disable_offset_noise", "禁用偏移噪声", "Disable offset noise", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.WORLD, "false"),
        new FarLandsOption("surface_noise_offset", "地表噪声偏移", "Surface noise offset", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.GLOBAL, "true"),

        // 内容开关 / Content switches
        new FarLandsOption("skygrid.enabled", "天空网格", "SkyGrid", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.WORLD, "false"),
        new FarLandsOption("skygrid.force", "强制天空网格", "Force SkyGrid", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.WORLD, "false"),
        new FarLandsOption("replace_default_fluid", "替换默认流体", "Replace default fluid", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.WORLD, "false"),
        new FarLandsOption("replace_default_fluid_block", "替换流体为", "Fluid replacement block", FarLandsOption.Type.TEXT,
            FarLandsOption.Scope.WORLD, "minecraft:air"),
        new FarLandsOption("replace_underground_lava", "替换地下岩浆", "Replace underground lava", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.WORLD, "false"),
        new FarLandsOption("replace_underground_lava_block", "岩浆替换为", "Lava replacement block", FarLandsOption.Type.TEXT,
            FarLandsOption.Scope.WORLD, "minecraft:air"),
        new FarLandsOption("disable_structure_spawn", "禁用结构生成", "Disable structure spawn", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.WORLD, "false"),
        new FarLandsOption("disable_entity_spawn", "禁用实体生成", "Disable entity spawn", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.WORLD, "false"),
        new FarLandsOption("prevent_generation_fails", "防生成失败", "Prevent generation fails", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.GLOBAL, "false"),
        new FarLandsOption("allow_illegal_value_player_position", "允许非法值玩家位置", "Allow illegal-value player position", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.GLOBAL, "true"),

        // 修复 / Fixes
        new FarLandsOption("fix_chunk_out_of_bounds", "修复区块越界", "Fix chunk out of bounds", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.GLOBAL, "true"),
        new FarLandsOption("fix_average_function_overflow", "修复平均值溢出", "Fix average function overflow", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.GLOBAL, "true"),
        new FarLandsOption("fix_climate_float_precision", "修复气候浮点精度", "Fix climate float precision", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.GLOBAL, "false"),
        new FarLandsOption("fix_end_rings", "修复末地环", "Fix end rings", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.GLOBAL, "false"),
        new FarLandsOption("fix_float_overflow_crash", "修复浮点溢出崩溃", "Fix float overflow crash", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.GLOBAL, "true"),
        new FarLandsOption("expand_datapack_value_range", "扩展数据包值域", "Expand datapack value range", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.GLOBAL, "true"),
        new FarLandsOption("expand_noise_value_retrieval_limit", "扩展噪声取值上限", "Expand noise value limit", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.GLOBAL, "true"),
        new FarLandsOption("limit_return_value", "限制返回值", "Limit return value", FarLandsOption.Type.BOOL,
            FarLandsOption.Scope.GLOBAL, "false"),
        new FarLandsOption("limit_return_value_value", "返回值上限值", "Return value cap", FarLandsOption.Type.TEXT,
            FarLandsOption.Scope.GLOBAL, "9.0"),

        // 高级 / Advanced（含我方独有）
        new FarLandsOption("config_mode", "配置模式", "Config mode", FarLandsOption.Type.ENUM,
            FarLandsOption.Scope.GLOBAL, "strict", "strict", "lenient"),
        new FarLandsOption("worldgen_sample_mode", "采样模式", "Sample mode", FarLandsOption.Type.ENUM,
            FarLandsOption.Scope.WORLD, "raw", "raw", "clamp", "quantize"),
        new FarLandsOption("worldgen_sample_clamp", "采样钳制值", "Sample clamp", FarLandsOption.Type.TEXT,
            FarLandsOption.Scope.WORLD, "1e300"),
        new FarLandsOption("worldgen_far_threshold", "远域阈值", "Far threshold", FarLandsOption.Type.TEXT,
            FarLandsOption.Scope.WORLD, "9007199254740992"),
        new FarLandsOption("fluid_tick_limit", "流体 tick 上限", "Fluid tick limit", FarLandsOption.Type.TEXT,
            FarLandsOption.Scope.GLOBAL, "2000"),
        new FarLandsOption("debug", "调试级别", "Debug level", FarLandsOption.Type.ENUM,
            FarLandsOption.Scope.GLOBAL, "0", "0", "1", "2", "3"),
        new FarLandsOption("epoch_x", "epoch X（创建前）", "Epoch X (pre-creation)", FarLandsOption.Type.TEXT,
            FarLandsOption.Scope.WORLD, "0"),
        new FarLandsOption("epoch_z", "epoch Z（创建前）", "Epoch Z (pre-creation)", FarLandsOption.Type.TEXT,
            FarLandsOption.Scope.WORLD, "0")
    );

    private static final String[] PRESETS = {"P1", "P2", "P3", "P4", "P5", "P6", "P7"};

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
        presetCycle.setTooltip(Tooltip.create(Component.literal(
            FarLandsPresets.describe(config.preset))));
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
        // rows per page follow the real screen height (GUI scale changes height)
        int rowsPerPage = Math.max(3, (height - 64 - 52) / 26);
        int pages = Math.max(1, (rows.size() + rowsPerPage - 1) / rowsPerPage);
        if (page >= pages) {
            page = pages - 1;
        }
        int from = page * rowsPerPage;
        int to = Math.min(rows.size(), from + rowsPerPage);

        int x = 118;
        int y = 52;
        int width = Math.min(220, this.width - x - 16);
        for (int i = from; i < to; i++) {
            FarLandsOption option = rows.get(i);
            Component label = labelFor(option);
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
                    StringWidget rowLabel = new StringWidget(label, font);
                    rowLabel.setX(x);
                    rowLabel.setY(y + 6);
                    addRenderableWidget(rowLabel);
                    EditBox box = new EditBox(font, Math.max(80, width - 106), 20, Component.empty());
                    box.setValue(config.get(option.key(), option.def()));
                    box.setHint(Component.literal(option.key()));
                    box.setTooltip(tooltip);
                    box.setX(x + 106);
                    box.setY(y);
                    edits.put(option.key(), box);
                    addRenderableWidget(box);
                }
            }
            y += 26;
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
            StringWidget pageInfo = new StringWidget(
                Component.literal((page + 1) + "/" + pages), font);
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
        if (System.getProperty("farlands." + option.key().replace('.', '_')) != null
            || System.getProperty("farlands." + option.key()) != null) {
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
        if (key.startsWith("farlands_style") || key.startsWith("precision_mode")
            || key.startsWith("progressive") || key.startsWith("exact_")
            || key.startsWith("simulated_") || key.startsWith("expand_bedrock")) {
            return 0;
        }
        if (key.startsWith("terrain_") || key.contains("_world_scaler") || key.contains("_world_offset")
            || key.startsWith("y_clamped") || key.startsWith("disable_offset_noise")
            || key.startsWith("surface_noise")) {
            return 1;
        }
        if (key.startsWith("skygrid") || key.startsWith("replace_") || key.startsWith("disable_")
            || key.startsWith("prevent_") || key.startsWith("allow_illegal")) {
            return 2;
        }
        if (key.startsWith("fix_") || key.startsWith("expand_") || key.startsWith("limit_return")) {
            return 3;
        }
        return 4;
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
