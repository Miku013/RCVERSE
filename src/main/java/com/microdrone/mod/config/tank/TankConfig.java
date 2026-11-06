package com.microdrone.mod.config.tank;

import com.microdrone.mod.MicroDroneMod;
import net.minecraft.resources.ResourceLocation;

/**
 * 外部JSONから読み込まれるRC戦車の機体定義・モデル設定・パラメータ
 */
public class TankConfig {
    public String id = "default";
    public String displayName = "Standard RC Tank";
    public String model = "microdrone:geo/rc_tank.geo.json";
    public String texture = "microdrone:textures/entity/rc_tank.png";
    public String animation = "microdrone:animations/rc_tank.animation.json";
    public TankProperties properties = new TankProperties();

    private transient ResourceLocation cachedModelLocation;
    private transient ResourceLocation cachedTextureLocation;
    private transient ResourceLocation cachedAnimationLocation;

    public static class TankProperties {
        public float maxSpeedMultiplier = 1.0f;
        public float accelMultiplier = 1.0f;
        public float turnSpeedMultiplier = 1.0f;
        public int reloadTicks = 20;
        public float cannonPower = 1.0f;
        public float scale = 1.0f;
    }

    public ResourceLocation getModelResource() {
        if (cachedModelLocation == null) {
            cachedModelLocation = parseResourceLocation(model, "geo/rc_tank.geo.json");
        }
        return cachedModelLocation;
    }

    public ResourceLocation getTextureResource() {
        if (cachedTextureLocation == null) {
            cachedTextureLocation = parseResourceLocation(texture, "textures/entity/rc_tank.png");
        }
        return cachedTextureLocation;
    }

    public ResourceLocation getAnimationResource() {
        if (cachedAnimationLocation == null) {
            cachedAnimationLocation = parseResourceLocation(animation, "animations/rc_tank.animation.json");
        }
        return cachedAnimationLocation;
    }

    private ResourceLocation parseResourceLocation(String locationStr, String fallbackPath) {
        if (locationStr == null || locationStr.isBlank()) {
            return ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, fallbackPath);
        }
        try {
            if (locationStr.contains(":")) {
                String[] parts = locationStr.split(":", 2);
                return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
            } else {
                return ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, locationStr);
            }
        } catch (Exception e) {
            return ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, fallbackPath);
        }
    }
}
