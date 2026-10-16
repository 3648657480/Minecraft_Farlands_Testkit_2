package com.farlands.g2.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global FarLands config (UI/UX master plan P0/P1).
 *
 * <p>Stored as pretty-printed {@code farlands_config.json} next to options.txt
 * (borrowed from MFSCelebrate's design). The JSON shape is a dedicated DTO with
 * a no-arg constructor and no {@code File}/final fields - serializing this
 * holder class directly made Gson fall back to Unsafe allocation and produced a
 * zero-byte file (caught and swallowed). Read/write failures are now reported.</p>
 */
public final class FarLandsGlobalConfig {

    private static final String FILE_NAME = "farlands_config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static FarLandsGlobalConfig instance;

    private final File file;
    public String schema = "g2-p0";
    public String preset = "P1";
    public Map<String, String> values = new LinkedHashMap<>();

    /** Gson-friendly DTO (public no-arg constructor, plain fields only). */
    public static final class Data {
        public String schema = "g2-p0";
        public String preset = "P1";
        public Map<String, String> values = new LinkedHashMap<>();
    }

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
                Data data = GSON.fromJson(reader, Data.class);
                if (data != null) {
                    if (data.schema != null) {
                        cfg.schema = data.schema;
                    }
                    if (data.preset != null) {
                        cfg.preset = data.preset;
                    }
                    if (data.values != null) {
                        cfg.values.putAll(data.values);
                    }
                }
            } catch (Throwable e) {
                System.out.println("[FarLands-G2] global config read failed: " + e);
            }
        }
        return cfg;
    }

    public void save() {
        Data data = new Data();
        data.schema = schema;
        data.preset = preset;
        data.values = new LinkedHashMap<>(values);
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        } catch (Throwable e) {
            System.out.println("[FarLands-G2] global config write failed: " + e);
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
