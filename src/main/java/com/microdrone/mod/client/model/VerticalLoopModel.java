package com.microdrone.mod.client.model;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.block.entity.VerticalLoopBlockEntity;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.GeoModel;

public class VerticalLoopModel extends GeoModel<VerticalLoopBlockEntity> {
    private static final ResourceLocation MODEL_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/vertical_loop.geo.json");
    private static final ResourceLocation TEXTURE_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/block/vertical_loop.png");

    @Override
    public ResourceLocation getModelResource(VerticalLoopBlockEntity animatable) {
        return MODEL_RESOURCE;
    }

    @Override
    public ResourceLocation getTextureResource(VerticalLoopBlockEntity animatable) {
        return TEXTURE_RESOURCE;
    }

    @Override
    public @Nullable ResourceLocation getAnimationResource(VerticalLoopBlockEntity animatable) {
        return null;
    }
}

