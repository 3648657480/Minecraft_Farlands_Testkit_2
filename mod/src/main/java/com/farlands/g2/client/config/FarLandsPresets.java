package com.farlands.g2.client.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Experiment-purpose presets (UI/UX master plan P1), adapted from G1's P1-P7
 * to the G2 merged option set. Applying a preset only sets values in the config
 * model; the user still presses Save (draft-first, never silent).
 */
public final class FarLandsPresets {

    private static final Map<String, Map<String, String>> PRESETS = new LinkedHashMap<>();

    static {
        // P1 标准（默认）：开箱即用，不改任何值
        PRESETS.put("P1", Map.of());
        // P2 安全锁：稳定探索——限制流体、防生成失败、无调试
        PRESETS.put("P2", Map.of(
            "fluid_tick_limit", "1000",
            "prevent_generation_fails", "true",
            "debug", "0"));
        // P3 高性能：大迁移/低内存
        PRESETS.put("P3", Map.of(
            "fluid_tick_limit", "500",
            "debug", "0"));
        // P4 世界实验：新建世界实验 worldgen_*（R5：勿对运行中存档做）
        PRESETS.put("P4", Map.of(
            "worldgen_sample_mode", "quantize",
            "worldgen_far_threshold", "0",
            "debug", "1"));
        // P5 取景：去动态对象（取景/截图）
        PRESETS.put("P5", Map.of(
            "fluid_tick_limit", "2000",
            "debug", "0"));
        // P6 排查：加日志（临时提速）
        PRESETS.put("P6", Map.of(
            "debug", "2"));
        // P7 原版对照：只跑边界对比，无任何自动化
        PRESETS.put("P7", Map.of(
            "worldgen_sample_mode", "raw",
            "debug", "0"));
    }

    private FarLandsPresets() {
    }

    /** Applies the preset's key/values into the config model. */
    public static int apply(String preset, FarLandsGlobalConfig config) {
        Map<String, String> values = PRESETS.getOrDefault(preset, Map.of());
        values.forEach(config::set);
        return values.size();
    }

    public static String describe(String preset) {
        return switch (preset) {
            case "P1" -> "标准（默认）";
            case "P2" -> "安全锁（稳定探索）";
            case "P3" -> "高性能（大迁移）";
            case "P4" -> "世界实验（新建世界）";
            case "P5" -> "取景（去动态对象）";
            case "P6" -> "排查（加日志）";
            case "P7" -> "原版对照";
            default -> preset;
        };
    }
}
