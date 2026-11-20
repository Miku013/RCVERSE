package com.microdrone.mod.config.spider;

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
 * config/microdrone/spider/ 配下の JSON ファイルから多脚ロボット設定を読み込み・キャッシュするマネージャー
 */
public class SpiderConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, SpiderConfig> CONFIGS = new HashMap<>();
    private static final SpiderConfig DEFAULT_CONFIG = new SpiderConfig();

    public static void init() {
        loadConfigs();
    }

    public static synchronized void loadConfigs() {
        CONFIGS.clear();
        CONFIGS.put("default", DEFAULT_CONFIG);

        try {
            Path configDir = FMLPaths.CONFIGDIR.get().resolve("microdrone").resolve("spider");
            File dir = configDir.toFile();
            if (!dir.exists()) {
                dir.mkdirs();
                createDefaultTemplate(configDir);
            }

            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                        SpiderConfig config = GSON.fromJson(reader, SpiderConfig.class);
                        if (config != null && config.id != null && !config.id.isBlank()) {
                            String key = config.id.toLowerCase();
                            CONFIGS.put(key, config);

                            String fileBase = file.getName().substring(0, file.getName().lastIndexOf('.')).toLowerCase();
                            CONFIGS.put(fileBase, config);

                            if (config.displayName != null && !config.displayName.isBlank()) {
                                CONFIGS.put(config.displayName.toLowerCase().trim(), config);
                            }

                            if ("default.json".equalsIgnoreCase(file.getName())) {
                                CONFIGS.put("default", config);
                            }
                            MicroDroneMod.LOGGER.info("Loaded RC Spider config: {} ({}) from {}", key, config.displayName, file.getName());
                        }
                    } catch (Exception e) {
                        MicroDroneMod.LOGGER.error("Failed to load spider config from {}", file.getName(), e);
                    }
                }
            }
        } catch (Exception e) {
            MicroDroneMod.LOGGER.error("Failed to load RC Spider configs directory", e);
        }
    }

    public static SpiderConfig getConfig(String id) {
        if (id == null || id.isBlank()) {
            return DEFAULT_CONFIG;
        }
        return CONFIGS.getOrDefault(id.toLowerCase().trim(), DEFAULT_CONFIG);
    }

    public static Map<String, SpiderConfig> getAllConfigs() {
        return Collections.unmodifiableMap(CONFIGS);
    }

    private static void createDefaultTemplate(Path configDir) {
        Path defaultFile = configDir.resolve("default.json");
        if (!Files.exists(defaultFile)) {
            try (Writer writer = Files.newBufferedWriter(defaultFile, StandardCharsets.UTF_8)) {
                SpiderConfig template = new SpiderConfig();
                template.id = "default";
                template.displayName = "Standard RC Hexapod Robot";
                template.model = "microdrone:geo/rc_spider.geo.json";
                template.texture = "microdrone:textures/entity/rc_spider.png";
                template.properties.scale = 0.35f;
                template.properties.stepHeight = 0.35f;
                template.properties.walkSpeedMultiplier = 1.0f;
                template.properties.turnSpeedMultiplier = 1.0f;
                template.properties.reloadTicks = 30;
                template.properties.cannonPower = 1.0f;

                GSON.toJson(template, writer);
                MicroDroneMod.LOGGER.info("Created default RC Spider config template at: {}", defaultFile);
            } catch (Exception e) {
                MicroDroneMod.LOGGER.error("Failed to create default RC Spider config template", e);
            }
        }
    }
}
