package com.microdrone.mod.client.model;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.block.entity.RcShowcaseBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class RcShowcaseModel extends GeoModel<RcShowcaseBlockEntity> {
    private static final ResourceLocation MODEL_RESOURCE = ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/rc_showcase.geo.json");
    private static final ResourceLocation TEXTURE_RESOURCE = ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/block/rc_showcase.png");

    @Override
    public ResourceLocation getModelResource(RcShowcaseBlockEntity animatable) {
        return MODEL_RESOURCE;
    }

    @Override
    public ResourceLocation getTextureResource(RcShowcaseBlockEntity animatable) {
        return TEXTURE_RESOURCE;
    }

    @Override
    public ResourceLocation getAnimationResource(RcShowcaseBlockEntity animatable) {
        return null;
    }
}
