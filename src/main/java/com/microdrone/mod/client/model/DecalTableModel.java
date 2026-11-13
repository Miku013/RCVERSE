package com.microdrone.mod.client.model;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.block.entity.DecalTableBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class DecalTableModel extends GeoModel<DecalTableBlockEntity> {
    private static final ResourceLocation MODEL_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/decal_table.geo.json");
    private static final ResourceLocation TEXTURE_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/block/decal_table.png");

    @Override
    public ResourceLocation getModelResource(DecalTableBlockEntity animatable) {
        return MODEL_RESOURCE;
    }

    @Override
    public ResourceLocation getTextureResource(DecalTableBlockEntity animatable) {
        return TEXTURE_RESOURCE;
    }

    @Override
    public ResourceLocation getAnimationResource(DecalTableBlockEntity animatable) {
        return null;
    }
}

