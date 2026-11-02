package com.microdrone.mod.client.model;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.block.miniature.MiniaturePlacementBlockEntity;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.GeoModel;

/**
 * カラーコーンのGeckoLibモデル
 */
public class TrafficConeModel extends GeoModel<MiniaturePlacementBlockEntity> {
    private static final ResourceLocation MODEL_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/traffic_cone.geo.json");
    private static final ResourceLocation TEXTURE_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/entity/traffic_cone.png");

    @Override
    public ResourceLocation getModelResource(MiniaturePlacementBlockEntity animatable) {
        return MODEL_RESOURCE;
    }

    @Override
    public ResourceLocation getTextureResource(MiniaturePlacementBlockEntity animatable) {
        return TEXTURE_RESOURCE;
    }

    @Override
    public @Nullable ResourceLocation getAnimationResource(MiniaturePlacementBlockEntity animatable) {
        return null;
    }
}

