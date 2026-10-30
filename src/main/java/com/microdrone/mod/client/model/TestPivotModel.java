package com.microdrone.mod.client.model;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.block.entity.TestPivotBlockEntity;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.GeoModel;

public class TestPivotModel extends GeoModel<TestPivotBlockEntity> {
    private static final ResourceLocation MODEL_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/testpivot.geo.json");
    private static final ResourceLocation TEXTURE_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/entity/testpivot.png");

    @Override
    public ResourceLocation getModelResource(TestPivotBlockEntity animatable) {
        return MODEL_RESOURCE;
    }

    @Override
    public ResourceLocation getTextureResource(TestPivotBlockEntity animatable) {
        return TEXTURE_RESOURCE;
    }

    @Override
    public @Nullable ResourceLocation getAnimationResource(TestPivotBlockEntity animatable) {
        return null;
    }
}

