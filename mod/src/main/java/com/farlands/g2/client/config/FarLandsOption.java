package com.farlands.g2.client.config;

/**
 * Option descriptor for the settings screen.
 *
 * <p>{@code key} is the raw config key (shown in the tooltip together with the
 * localized description, per the master plan: UI and file/CLI stay
 * one-to-one). {@code scope} decides where the value lands: GLOBAL writes the
 * shared farlands_config.json, WORLD writes the per-world draft /
 * farlands.properties.</p>
 */
public record FarLandsOption(String key, String cn, String en, String descCn, String descEn,
                             Type type, Scope scope, String def, String... choices) {

    public enum Type {
        BOOL, ENUM, TEXT
    }

    public enum Scope {
        GLOBAL, WORLD
    }
}
