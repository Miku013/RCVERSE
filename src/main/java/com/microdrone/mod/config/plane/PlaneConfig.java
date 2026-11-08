package com.microdrone.mod.config.plane;

import com.microdrone.mod.MicroDroneMod;
import net.minecraft.resources.ResourceLocation;

/**
 * 外部JSONから読み込まれるRC飛行機の機体定義・モデル設定・パラメータ
 */
public class PlaneConfig {
    public String id = "default";
    public String displayName = "Standard RC Plane";
    public String model = "microdrone:geo/rc_plane.geo.json";
    public String texture = "microdrone:textures/entity/rc_plane.png";
    public String animation = "microdrone:animations/rc_plane.animation.json";
    public PlaneProperties properties = new PlaneProperties();

    private transient ResourceLocation cachedModelLocation;
    private transient ResourceLocation cachedTextureLocation;
    private transient ResourceLocation cachedAnimationLocation;

    public static class PlaneProperties {
        public float maxSpeedMultiplier = 1.0f;
        public float accelMultiplier = 1.0f;
        public float turnSpeedMultiplier = 1.0f;
        public float liftMultiplier = 1.0f;
        public float scale = 1.0f;
    }

    public ResourceLocation getModelResource() {
        if (cachedModelLocation == null) {
            cachedModelLocation = parseResourceLocation(model, "geo/rc_plane.geo.json");
        }
        return cachedModelLocation;
    }

    public ResourceLocation getTextureResource() {
        if (cachedTextureLocation == null) {
            cachedTextureLocation = parseResourceLocation(texture, "textures/entity/rc_plane.png");
        }
        return cachedTextureLocation;
    }

    public ResourceLocation getAnimationResource() {
        if (cachedAnimationLocation == null) {
            cachedAnimationLocation = parseResourceLocation(animation, "animations/rc_plane.animation.json");
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

