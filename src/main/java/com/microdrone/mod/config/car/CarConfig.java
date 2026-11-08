package com.microdrone.mod.config.car;

import com.microdrone.mod.MicroDroneMod;
import net.minecraft.resources.ResourceLocation;

/**
 * 外部JSONから読み込まれるRCカーの車体定義・モデル設定・パラメータ
 */
public class CarConfig {
    public String id = "default";
    public String displayName = "Standard RC Car";
    public String model = "microdrone:geo/rc_car.geo.json";
    public String texture = "microdrone:textures/entity/rc_car.png";
    public String animation = "microdrone:animations/rc_car.animation.json";
    public CarProperties properties = new CarProperties();

    private transient ResourceLocation cachedModelLocation;
    private transient ResourceLocation cachedTextureLocation;
    private transient ResourceLocation cachedAnimationLocation;

    public static class CarProperties {
        public float maxSpeedMultiplier = 1.0f;
        public float accelMultiplier = 1.0f;
        public float turnSpeedMultiplier = 1.0f;
        public float gripMultiplier = 1.0f;
        public float scale = 1.0f;
        public boolean invertSteer = false;
        public float rollMultiplier = 1.0f; // 旋回・遠心ロールの強度倍率 (0.0=ロール無効/水平, 0.25=リアルな浅い傾き, 1.0=標準バギー)
        public float maxRollAngle = 48.0f;  // 最大ロール角 (度)
        public float retractableAngle = 22.0f; // リトラクタブル開閉時の最大角度 (度)
        public float pedalSwingMultiplier = 1.0f; // 自転車ペダリングの足の動きの大きさ倍率
    }

    public ResourceLocation getModelResource() {
        if (cachedModelLocation == null) {
            cachedModelLocation = parseResourceLocation(model, "geo/rc_car.geo.json");
        }
        return cachedModelLocation;
    }

    public ResourceLocation getTextureResource() {
        if (cachedTextureLocation == null) {
            cachedTextureLocation = parseResourceLocation(texture, "textures/entity/rc_car.png");
        }
        return cachedTextureLocation;
    }

    public ResourceLocation getAnimationResource() {
        if (cachedAnimationLocation == null) {
            cachedAnimationLocation = parseResourceLocation(animation, "animations/rc_car.animation.json");
        }
        return cachedAnimationLocation;
    }

    private ResourceLocation parseResourceLocation(String locationStr, String fallbackPath) {
        if (locationStr == null || locationStr.isBlank()) {
            return ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, fallbackPath.toLowerCase());
        }
        String cleanStr = locationStr.trim().toLowerCase();
        try {
            if (cleanStr.contains(":")) {
                String[] parts = cleanStr.split(":", 2);
                return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
            } else {
                return ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, cleanStr);
            }
        } catch (Exception e) {
            MicroDroneMod.LOGGER.error("Invalid ResourceLocation: {}, fallback to {}", locationStr, fallbackPath, e);
            return ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, fallbackPath.toLowerCase());
        }
    }
}

