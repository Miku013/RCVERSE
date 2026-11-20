package com.microdrone.mod.config.drone;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.microdrone.mod.MicroDroneMod;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * config/microdrone/drones/ 配下の JSON ファイルから動的ドローン設定を読み込み・キャッシュするマネージャー
 */
public class DroneConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, DroneConfig> CONFIGS = new HashMap<>();
    private static final DroneConfig DEFAULT_CONFIG = new DroneConfig();

    public static void init() {
        loadConfigs();
    }

    public static synchronized void loadConfigs() {
        CONFIGS.clear();
        CONFIGS.put("default", DEFAULT_CONFIG);

        try {
            Path configDir = FMLPaths.CONFIGDIR.get().resolve("microdrone").resolve("drones");
            File dir = configDir.toFile();
            if (!dir.exists()) {
                dir.mkdirs();
                createDefaultTemplate(configDir);
            }

            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                        DroneConfig config = GSON.fromJson(reader, DroneConfig.class);
                        if (config != null && config.id != null && !config.id.isBlank()) {
                            String key = config.id.toLowerCase();
                            CONFIGS.put(key, config);
                            if ("default.json".equalsIgnoreCase(file.getName())) {
                                CONFIGS.put("default", config);
                            }
                            MicroDroneMod.LOGGER.info("Loaded Micro Drone config: {} ({}) from {}", key, config.displayName, file.getName());
                        }
                    } catch (Exception e) {
                        MicroDroneMod.LOGGER.error("Failed to parse Micro Drone config from file: " + file.getName(), e);
                    }
                }
            }
        } catch (Exception e) {
            MicroDroneMod.LOGGER.error("Failed to load Micro Drone configs", e);
        }
    }

    private static void createDefaultTemplate(Path configDir) {
        try {
            File defaultFile = configDir.resolve("default.json").toFile();
            if (!defaultFile.exists()) {
                DroneConfig defaultTemplate = new DroneConfig();
                defaultTemplate.id = "default";
                defaultTemplate.displayName = "スタンダード マイクロドローン";
                defaultTemplate.model = "microdrone:geo/drone.geo.json";
                defaultTemplate.texture = "microdrone:textures/entity/drone.png";
                defaultTemplate.animation = "microdrone:animations/drone.animation.json";
                defaultTemplate.properties = new DroneConfig.DroneProperties();

                try (Writer writer = Files.newBufferedWriter(defaultFile.toPath(), StandardCharsets.UTF_8)) {
                    GSON.toJson(defaultTemplate, writer);
                }
            }
        } catch (Exception e) {
            MicroDroneMod.LOGGER.error("Failed to create default Micro Drone config template", e);
        }
    }

    public static DroneConfig getConfig(String variantId) {
        if (variantId == null || variantId.isBlank() || "default".equalsIgnoreCase(variantId)) {
            return CONFIGS.getOrDefault("default", DEFAULT_CONFIG);
        }
        String cleanId = variantId.trim().toLowerCase();
        if (CONFIGS.containsKey(cleanId)) {
            return CONFIGS.get(cleanId);
        }
        String strippedId = cleanId.replaceAll("[._-]", "");
        for (Map.Entry<String, DroneConfig> entry : CONFIGS.entrySet()) {
            if (entry.getKey().replaceAll("[._-]", "").equalsIgnoreCase(strippedId)) {
                return entry.getValue();
            }
        }
        return CONFIGS.getOrDefault("default", DEFAULT_CONFIG);
    }

    public static Map<String, DroneConfig> getAllConfigs() {
        return Collections.unmodifiableMap(CONFIGS);
    }
}

