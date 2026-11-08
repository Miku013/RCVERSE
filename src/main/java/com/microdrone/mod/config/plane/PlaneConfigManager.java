package com.microdrone.mod.config.plane;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.microdrone.mod.MicroDroneMod;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * config/microdrone/planes/ 配下の JSON ファイルから動的RC飛行機設定を読み込み・キャッシュするマネージャー
 */
public class PlaneConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, PlaneConfig> CONFIGS = new HashMap<>();
    private static final PlaneConfig DEFAULT_CONFIG = new PlaneConfig();

    public static void init() {
        loadConfigs();
    }

    public static synchronized void loadConfigs() {
        CONFIGS.clear();
        CONFIGS.put("default", DEFAULT_CONFIG);

        try {
            Path configDir = FMLPaths.CONFIGDIR.get().resolve("microdrone").resolve("planes");
            File dir = configDir.toFile();
            if (!dir.exists()) {
                dir.mkdirs();
                createDefaultTemplate(configDir);
            }

            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    try (FileReader reader = new FileReader(file)) {
                        PlaneConfig config = GSON.fromJson(reader, PlaneConfig.class);
                        if (config != null && config.id != null && !config.id.isBlank()) {
                            String key = config.id.toLowerCase();
                            CONFIGS.put(key, config);
                            if ("default.json".equalsIgnoreCase(file.getName())) {
                                CONFIGS.put("default", config);
                            }
                            MicroDroneMod.LOGGER.info("Loaded RC Plane config: {} ({}) from {}", key, config.displayName, file.getName());
                        }
                    } catch (Exception e) {
                        MicroDroneMod.LOGGER.error("Failed to parse RC Plane config from file: " + file.getName(), e);
                    }
                }
            }
        } catch (Exception e) {
            MicroDroneMod.LOGGER.error("Failed to load RC Plane configs", e);
        }
    }

    private static void createDefaultTemplate(Path configDir) {
        try {
            File defaultFile = configDir.resolve("default.json").toFile();
            if (!defaultFile.exists()) {
                PlaneConfig defaultTemplate = new PlaneConfig();
                defaultTemplate.id = "default";
                defaultTemplate.displayName = "スタンダード RC飛行機";
                defaultTemplate.model = "microdrone:geo/rc_plane.geo.json";
                defaultTemplate.texture = "microdrone:textures/entity/rc_plane.png";
                defaultTemplate.animation = "microdrone:animations/rc_plane.animation.json";
                defaultTemplate.properties = new PlaneConfig.PlaneProperties();

                try (FileWriter writer = new FileWriter(defaultFile)) {
                    GSON.toJson(defaultTemplate, writer);
                }
            }
        } catch (Exception e) {
            MicroDroneMod.LOGGER.error("Failed to create default RC Plane config template", e);
        }
    }

    public static PlaneConfig getConfig(String variantId) {
        if (variantId == null || variantId.isBlank() || "default".equalsIgnoreCase(variantId)) {
            return CONFIGS.getOrDefault("default", DEFAULT_CONFIG);
        }
        String cleanId = variantId.trim().toLowerCase();
        if (CONFIGS.containsKey(cleanId)) {
            return CONFIGS.get(cleanId);
        }
        String strippedId = cleanId.replaceAll("[._-]", "");
        for (Map.Entry<String, PlaneConfig> entry : CONFIGS.entrySet()) {
            if (entry.getKey().replaceAll("[._-]", "").equalsIgnoreCase(strippedId)) {
                return entry.getValue();
            }
        }
        return CONFIGS.getOrDefault("default", DEFAULT_CONFIG);
    }

    public static Map<String, PlaneConfig> getAllConfigs() {
        return Collections.unmodifiableMap(CONFIGS);
    }
}

