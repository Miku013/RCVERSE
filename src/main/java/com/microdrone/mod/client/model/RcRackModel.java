package com.microdrone.mod.client.model;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.block.entity.RcRackBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class RcRackModel extends GeoModel<RcRackBlockEntity> {
    private static final ResourceLocation MODEL_RESOURCE = ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/rc_rack.geo.json");
    private static final ResourceLocation TEXTURE_RESOURCE = ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/block/rc_rack.png");

    @Override
    public ResourceLocation getModelResource(RcRackBlockEntity animatable) {
        return MODEL_RESOURCE;
    }

    @Override
    public ResourceLocation getTextureResource(RcRackBlockEntity animatable) {
        return TEXTURE_RESOURCE;
    }

    @Override
    public ResourceLocation getAnimationResource(RcRackBlockEntity animatable) {
        return null;
    }
}
