package com.microdrone.mod.config.tank;

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
 * config/microdrone/tanks/ 配下の JSON ファイルから動的戦車設定を読み込み・キャッシュするマネージャー
 */
public class TankConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, TankConfig> CONFIGS = new HashMap<>();
    private static final TankConfig DEFAULT_CONFIG = new TankConfig();

    public static void init() {
        loadConfigs();
    }

    public static synchronized void loadConfigs() {
        CONFIGS.clear();
        CONFIGS.put("default", DEFAULT_CONFIG);

        try {
            Path configDir = FMLPaths.CONFIGDIR.get().resolve("microdrone").resolve("tanks");
            File dir = configDir.toFile();
            if (!dir.exists()) {
                dir.mkdirs();
                createDefaultTemplate(configDir);
            }

            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    try (FileReader reader = new FileReader(file)) {
                        TankConfig config = GSON.fromJson(reader, TankConfig.class);
                        if (config != null && config.id != null && !config.id.isBlank()) {
                            String key = config.id.toLowerCase();
                            CONFIGS.put(key, config);

                            // ファイル名（拡張子なし）もエイリアス登録（例: "pz_h_tank"）
                            String fileBase = file.getName().substring(0, file.getName().lastIndexOf('.')).toLowerCase();
                            CONFIGS.put(fileBase, config);

                            // "_tank" や "tank" 抜きの短縮名もエイリアス登録（例: "pz.h", "pzh"）
                            String shortKey = key.replaceAll("(_tank|tank|戦車)", "");
                            if (!shortKey.isBlank()) {
                                CONFIGS.put(shortKey, config);
                                CONFIGS.put(shortKey.replaceAll("[._\\-\\s]", ""), config);
                            }

                            // 表示名（displayName）も小文字で登録
                            if (config.displayName != null && !config.displayName.isBlank()) {
                                CONFIGS.put(config.displayName.toLowerCase().trim(), config);
                            }

                            // default.json の場合は "default" のキーとしても登録（通常設置時に即座に反映されるようにする）
                            if ("default.json".equalsIgnoreCase(file.getName())) {
                                CONFIGS.put("default", config);
                            }
                            MicroDroneMod.LOGGER.info("Loaded RC Tank config: {} ({}) from {}", key, config.displayName, file.getName());
                        }
                    } catch (Exception e) {
                        MicroDroneMod.LOGGER.error("Failed to parse RC Tank config from file: " + file.getName(), e);
                    }
                }
            }
        } catch (Exception e) {
            MicroDroneMod.LOGGER.error("Failed to load RC Tank configs", e);
        }
    }

    private static void createDefaultTemplate(Path configDir) {
        try {
            File defaultFile = configDir.resolve("default.json").toFile();
            if (!defaultFile.exists()) {
                TankConfig defaultTemplate = new TankConfig();
                defaultTemplate.id = "default";
                defaultTemplate.displayName = "スタンダード RC戦車";
                defaultTemplate.model = "microdrone:geo/rc_tank.geo.json";
                defaultTemplate.texture = "microdrone:textures/entity/rc_tank.png";
                defaultTemplate.animation = "microdrone:animations/rc_tank.animation.json";
                defaultTemplate.properties = new TankConfig.TankProperties();

                try (FileWriter writer = new FileWriter(defaultFile)) {
                    GSON.toJson(defaultTemplate, writer);
                }
            }
        } catch (Exception e) {
            MicroDroneMod.LOGGER.error("Failed to create default RC Tank config template", e);
        }
    }

    public static TankConfig getConfig(String variantId) {
        if (variantId == null || variantId.isBlank() || "default".equalsIgnoreCase(variantId)) {
            return CONFIGS.getOrDefault("default", DEFAULT_CONFIG);
        }
        String cleanId = variantId.trim().toLowerCase();
        if (CONFIGS.containsKey(cleanId)) {
            return CONFIGS.get(cleanId);
        }

        // ドットやアンダースコア、スペースを除去した比較（例: "pzh_tank" と "pz.h_tank"）
        String strippedId = cleanId.replaceAll("[._\\-\\s]", "");
        for (Map.Entry<String, TankConfig> entry : CONFIGS.entrySet()) {
            if (entry.getKey().replaceAll("[._\\-\\s]", "").equalsIgnoreCase(strippedId)) {
                return entry.getValue();
            }
        }

        // "tank", "rc", "戦車" などの単語を除去したコア名での比較（例: "pzh" と "pz.h_tank"）
        String coreId = strippedId.replaceAll("(tank|rc|戦車)", "");
        if (!coreId.isEmpty()) {
            for (Map.Entry<String, TankConfig> entry : CONFIGS.entrySet()) {
                String entryCore = entry.getKey().replaceAll("[._\\-\\s]", "").replaceAll("(tank|rc|戦車)", "");
                if (!entryCore.isEmpty() && entryCore.equalsIgnoreCase(coreId)) {
                    return entry.getValue();
                }
            }
        }

        return CONFIGS.getOrDefault("default", DEFAULT_CONFIG);
    }

    public static Map<String, TankConfig> getAllConfigs() {
        return Collections.unmodifiableMap(CONFIGS);
    }
}

