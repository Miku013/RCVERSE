package com.microdrone.mod.config.car;

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
 * config/microdrone/cars/ 配下の JSON ファイルから動的RCカー設定を読み込み・キャッシュするマネージャー
 */
public class CarConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, CarConfig> CONFIGS = new HashMap<>();
    private static final CarConfig DEFAULT_CONFIG = new CarConfig();

    public static void init() {
        loadConfigs();
    }

    public static synchronized void loadConfigs() {
        CONFIGS.clear();
        CONFIGS.put("default", DEFAULT_CONFIG);

        try {
            Path configDir = FMLPaths.CONFIGDIR.get().resolve("microdrone").resolve("cars");
            File dir = configDir.toFile();
            if (!dir.exists()) {
                dir.mkdirs();
                createDefaultTemplate(configDir);
            }

            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    try (FileReader reader = new FileReader(file)) {
                        CarConfig config = GSON.fromJson(reader, CarConfig.class);
                        if (config != null && config.id != null && !config.id.isBlank()) {
                            String key = config.id.toLowerCase();
                            CONFIGS.put(key, config);
                            if ("default.json".equalsIgnoreCase(file.getName())) {
                                CONFIGS.put("default", config);
                            }
                            MicroDroneMod.LOGGER.info("Loaded RC Car config: {} ({}) from {}", key, config.displayName, file.getName());
                        }
                    } catch (Exception e) {
                        MicroDroneMod.LOGGER.error("Failed to parse RC Car config from file: " + file.getName(), e);
                    }
                }
            }
        } catch (Exception e) {
            MicroDroneMod.LOGGER.error("Failed to load RC Car configs", e);
        }
    }

    private static void createDefaultTemplate(Path configDir) {
        try {
            File defaultFile = configDir.resolve("default.json").toFile();
            if (!defaultFile.exists()) {
                CarConfig defaultTemplate = new CarConfig();
                defaultTemplate.id = "default";
                defaultTemplate.displayName = "スタンダード RCカー";
                defaultTemplate.model = "microdrone:geo/rc_car.geo.json";
                defaultTemplate.texture = "microdrone:textures/entity/rc_car.png";
                defaultTemplate.animation = "microdrone:animations/rc_car.animation.json";
                defaultTemplate.properties = new CarConfig.CarProperties();

                try (FileWriter writer = new FileWriter(defaultFile)) {
                    GSON.toJson(defaultTemplate, writer);
                }
            }
        } catch (Exception e) {
            MicroDroneMod.LOGGER.error("Failed to create default RC Car config template", e);
        }
    }

    public static CarConfig getConfig(String variantId) {
        if (variantId == null || variantId.isBlank() || "default".equalsIgnoreCase(variantId)) {
            return CONFIGS.getOrDefault("default", DEFAULT_CONFIG);
        }
        String cleanId = variantId.trim().toLowerCase();
        if (CONFIGS.containsKey(cleanId)) {
            return CONFIGS.get(cleanId);
        }
        String strippedId = cleanId.replaceAll("[._-]", "");
        for (Map.Entry<String, CarConfig> entry : CONFIGS.entrySet()) {
            if (entry.getKey().replaceAll("[._-]", "").equalsIgnoreCase(strippedId)) {
                return entry.getValue();
            }
        }
        return CONFIGS.getOrDefault("default", DEFAULT_CONFIG);
    }

    public static Map<String, CarConfig> getAllConfigs() {
        return Collections.unmodifiableMap(CONFIGS);
    }
}

