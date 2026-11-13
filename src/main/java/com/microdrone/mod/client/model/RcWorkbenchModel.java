package com.microdrone.mod.client.model;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.block.entity.RcWorkbenchBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * RC工作台のGeckoLib 3Dモデル定義
 */
public class RcWorkbenchModel extends GeoModel<RcWorkbenchBlockEntity> {
    private static final ResourceLocation MODEL_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/rc_workbench.geo.json");
    private static final ResourceLocation TEXTURE_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/block/rc_workbench.png");

    @Override
    public ResourceLocation getModelResource(RcWorkbenchBlockEntity animatable) {
        return MODEL_RESOURCE;
    }

    @Override
    public ResourceLocation getTextureResource(RcWorkbenchBlockEntity animatable) {
        return TEXTURE_RESOURCE;
    }

    @Override
    public ResourceLocation getAnimationResource(RcWorkbenchBlockEntity animatable) {
        return null;
    }
}
