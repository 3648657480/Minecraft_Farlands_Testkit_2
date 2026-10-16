package com.farlands.g2.client.config;

/**
 * Option descriptor for the settings screen (P0 skeleton).
 *
 * <p>{@code key} is the raw config key (also shown in the tooltip, per the
 * master plan: UI and file/CLI stay one-to-one).</p>
 */
public record FarLandsOption(String key, String cn, String en, Type type, String def, String... choices) {

    public enum Type {
        BOOL, ENUM, TEXT
    }
}
