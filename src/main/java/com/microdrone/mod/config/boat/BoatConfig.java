package com.microdrone.mod.config.boat;

import com.microdrone.mod.MicroDroneMod;
import net.minecraft.resources.ResourceLocation;

/**
 * 外部JSONから読み込まれるRCボートの船体定義・モデル設定・パラメータ
 */
public class BoatConfig {
    public String id = "default";
    public String displayName = "Standard RC Boat";
    public String model = "microdrone:geo/rc_boat.geo.json";
    public String texture = "microdrone:textures/entity/rc_boat.png";
    public String animation = "microdrone:animations/rc_boat.animation.json";
    public BoatProperties properties = new BoatProperties();

    private transient ResourceLocation cachedModelLocation;
    private transient ResourceLocation cachedTextureLocation;
    private transient ResourceLocation cachedAnimationLocation;

    public static class BoatProperties {
        public float maxSpeedMultiplier = 1.0f;
        public float accelMultiplier = 1.0f;
        public float turnSpeedMultiplier = 1.0f;
        public float scale = 1.0f;
    }

    public ResourceLocation getModelResource() {
        if (cachedModelLocation == null) {
            cachedModelLocation = parseResourceLocation(model, "geo/rc_boat.geo.json");
        }
        return cachedModelLocation;
    }

    public ResourceLocation getTextureResource() {
        if (cachedTextureLocation == null) {
            cachedTextureLocation = parseResourceLocation(texture, "textures/entity/rc_boat.png");
        }
        return cachedTextureLocation;
    }

    public ResourceLocation getAnimationResource() {
        if (cachedAnimationLocation == null) {
            cachedAnimationLocation = parseResourceLocation(animation, "animations/rc_boat.animation.json");
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

