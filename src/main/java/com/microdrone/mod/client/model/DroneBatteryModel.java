package com.microdrone.mod.client.model;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.item.DroneBatteryItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class DroneBatteryModel extends GeoModel<DroneBatteryItem> {
    private static final ResourceLocation MODEL_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/drone_battery.geo.json");
    private static final ResourceLocation TEXTURE_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/entity/drone_battery.png");
    private static final ResourceLocation ANIMATION_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "animations/drone_battery.animation.json");

    @Override
    public ResourceLocation getModelResource(DroneBatteryItem animatable) {
        return MODEL_RESOURCE;
    }

    @Override
    public ResourceLocation getTextureResource(DroneBatteryItem animatable) {
        return TEXTURE_RESOURCE;
    }

    @Override
    public ResourceLocation getAnimationResource(DroneBatteryItem animatable) {
        return ANIMATION_RESOURCE;
    }
}
