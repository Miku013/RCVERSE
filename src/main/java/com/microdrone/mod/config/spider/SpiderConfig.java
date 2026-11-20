package com.microdrone.mod.config.spider;

import com.microdrone.mod.MicroDroneMod;
import net.minecraft.resources.ResourceLocation;

/**
 * 外部JSONから読み込まれるRC多脚ロボットの機体定義・モデル設定・パラメータ
 */
public class SpiderConfig {
    public String id = "default";
    public String displayName = "Standard RC Spider";
    public String model = "microdrone:geo/rc_spider.geo.json";
    public String texture = "microdrone:textures/entity/rc_spider.png";
    public String animation = "microdrone:animations/rc_spider.animation.json";
    public SpiderProperties properties = new SpiderProperties();

    private transient ResourceLocation cachedModelLocation;
    private transient ResourceLocation cachedTextureLocation;
    private transient ResourceLocation cachedAnimationLocation;

    public static class SpiderProperties {
        public float scale = 0.35f;          // 描画およびレイキャスト判定のスケール倍率 (デフォルト: 0.35)
        public float yOffset = 0.0f;          // 描画高さオフセット (モデル側で高さを修正いただいたためデフォルト 0.0m)
        public float maxTiltAngle = 30.0f;    // 地形適応時の最大傾斜角（度、デフォルト: 30度）
        public float stepHeight = 0.35f;     // ステップ時に足を持ち上げる高さ (m)
        public float walkSpeedMultiplier = 1.0f; // 歩行速度倍率
        public float turnSpeedMultiplier = 1.0f; // 旋回速度倍率
        public int reloadTicks = 30;         // 射撃クールダウン
        public float cannonPower = 1.0f;     // 射撃威力
    }

    public ResourceLocation getModelResource() {
        if (cachedModelLocation == null) {
            cachedModelLocation = parseResourceLocation(model, "geo/rc_spider.geo.json");
        }
        return cachedModelLocation;
    }

    public ResourceLocation getTextureResource() {
        if (cachedTextureLocation == null) {
            cachedTextureLocation = parseResourceLocation(texture, "textures/entity/rc_spider.png");
        }
        return cachedTextureLocation;
    }

    public ResourceLocation getAnimationResource() {
        if (cachedAnimationLocation == null) {
            cachedAnimationLocation = parseResourceLocation(animation, "animations/rc_spider.animation.json");
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
            MicroDroneMod.LOGGER.error("Failed to parse ResourceLocation: '{}', falling back to default.", locationStr, e);
            return ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, fallbackPath.toLowerCase());
        }
    }
}
