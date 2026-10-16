package com.farlands.g2.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global FarLands config (UI/UX master plan P0).
 *
 * <p>Stored as pretty-printed {@code farlands_config.json} next to options.txt
 * (borrowed from MFSCelebrate's design). Values are kept as strings; the typed
 * getters parse on read so unknown keys survive round-trips.</p>
 */
public final class FarLandsGlobalConfig {

    private static final String FILE_NAME = "farlands_config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static FarLandsGlobalConfig instance;

    private final File file;
    public String schema = "g2-p0";
    public String preset = "P1";
    public Map<String, String> values = new LinkedHashMap<>();

    private FarLandsGlobalConfig(File file) {
        this.file = file;
    }

    public static FarLandsGlobalConfig get() {
        if (instance == null) {
            File dir = net.minecraft.client.Minecraft.getInstance().gameDirectory;
            instance = load(new File(dir, FILE_NAME));
        }
        return instance;
    }

    private static FarLandsGlobalConfig load(File f) {
        FarLandsGlobalConfig cfg = new FarLandsGlobalConfig(f);
        if (f.exists()) {
            try (FileReader reader = new FileReader(f)) {
                FarLandsGlobalConfig loaded = GSON.fromJson(reader, FarLandsGlobalConfig.class);
                if (loaded != null) {
                    if (loaded.schema != null) {
                        cfg.schema = loaded.schema;
                    }
                    if (loaded.preset != null) {
                        cfg.preset = loaded.preset;
                    }
                    if (loaded.values != null) {
                        cfg.values.putAll(loaded.values);
                    }
                }
            } catch (Throwable ignored) {
                // lenient read: broken file falls back to defaults (ui layer may warn)
            }
        }
        return cfg;
    }

    public void save() {
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(this, writer);
        } catch (Throwable ignored) {
        }
    }

    public String get(String key, String def) {
        String v = values.get(key);
        return v == null ? def : v;
    }

    public boolean getBool(String key, boolean def) {
        String v = values.get(key);
        return v == null ? def : Boolean.parseBoolean(v);
    }

    public void set(String key, String value) {
        values.put(key, value);
    }
}
