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
                            CONFIGS.put(config.id.toLowerCase(), config);
                            MicroDroneMod.LOGGER.info("Loaded RC Tank config: {} ({})", config.id, config.displayName);
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
        if (variantId == null || variantId.isBlank()) {
            return DEFAULT_CONFIG;
        }
        return CONFIGS.getOrDefault(variantId.toLowerCase(), DEFAULT_CONFIG);
    }

    public static Map<String, TankConfig> getAllConfigs() {
        return Collections.unmodifiableMap(CONFIGS);
    }
}
