package com.microdrone.mod.config.submarine;

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
 * config/microdrone/submarines/ 配下の JSON ファイルから動的RC潜水艦設定を読み込み・キャッシュするマネージャー
 */
public class SubmarineConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, SubmarineConfig> CONFIGS = new HashMap<>();
    private static final SubmarineConfig DEFAULT_CONFIG = new SubmarineConfig();

    public static void init() {
        loadConfigs();
    }

    public static synchronized void loadConfigs() {
        CONFIGS.clear();
        CONFIGS.put("default", DEFAULT_CONFIG);

        try {
            Path configDir = FMLPaths.CONFIGDIR.get().resolve("microdrone").resolve("submarines");
            File dir = configDir.toFile();
            if (!dir.exists()) {
                dir.mkdirs();
                createDefaultTemplate(configDir);
            }

            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    try (FileReader reader = new FileReader(file)) {
                        SubmarineConfig config = GSON.fromJson(reader, SubmarineConfig.class);
                        if (config != null && config.id != null && !config.id.isBlank()) {
                            String key = config.id.toLowerCase();
                            CONFIGS.put(key, config);
                            if ("default.json".equalsIgnoreCase(file.getName())) {
                                CONFIGS.put("default", config);
                            }
                            MicroDroneMod.LOGGER.info("Loaded RC Submarine config: {} ({}) from {}", key, config.displayName, file.getName());
                        }
                    } catch (Exception e) {
                        MicroDroneMod.LOGGER.error("Failed to parse RC Submarine config from file: " + file.getName(), e);
                    }
                }
            }
        } catch (Exception e) {
            MicroDroneMod.LOGGER.error("Failed to load RC Submarine configs", e);
        }
    }

    private static void createDefaultTemplate(Path configDir) {
        try {
            File defaultFile = configDir.resolve("default.json").toFile();
            if (!defaultFile.exists()) {
                SubmarineConfig defaultTemplate = new SubmarineConfig();
                defaultTemplate.id = "default";
                defaultTemplate.displayName = "スタンダード RC潜水艦";
                defaultTemplate.model = "microdrone:geo/rc_submarine.geo.json";
                defaultTemplate.texture = "microdrone:textures/entity/rc_submarine.png";
                defaultTemplate.animation = "microdrone:animations/rc_submarine.animation.json";
                defaultTemplate.properties = new SubmarineConfig.SubmarineProperties();

                try (FileWriter writer = new FileWriter(defaultFile)) {
                    GSON.toJson(defaultTemplate, writer);
                }
            }
        } catch (Exception e) {
            MicroDroneMod.LOGGER.error("Failed to create default RC Submarine config template", e);
        }
    }

    public static SubmarineConfig getConfig(String variantId) {
        if (variantId == null || variantId.isBlank() || "default".equalsIgnoreCase(variantId)) {
            return CONFIGS.getOrDefault("default", DEFAULT_CONFIG);
        }
        String cleanId = variantId.trim().toLowerCase();
        if (CONFIGS.containsKey(cleanId)) {
            return CONFIGS.get(cleanId);
        }
        String strippedId = cleanId.replaceAll("[._-]", "");
        for (Map.Entry<String, SubmarineConfig> entry : CONFIGS.entrySet()) {
            if (entry.getKey().replaceAll("[._-]", "").equalsIgnoreCase(strippedId)) {
                return entry.getValue();
            }
        }
        return CONFIGS.getOrDefault("default", DEFAULT_CONFIG);
    }

    public static Map<String, SubmarineConfig> getAllConfigs() {
        return Collections.unmodifiableMap(CONFIGS);
    }
}

