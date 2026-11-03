package com.microdrone.mod.client.model;

import com.microdrone.mod.block.miniature.MiniaturePlacementBlockEntity;
import com.microdrone.mod.block.miniature.MiniatureType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.GeoModel;

/**
 * コース小物を動的に切り替えて描画する汎用GeckoLibモデル
 */
public class GenericMiniatureModel extends GeoModel<MiniaturePlacementBlockEntity> {
    private ResourceLocation currentModel = MiniatureType.TRAFFIC_CONE.getModelLocation();
    private ResourceLocation currentTexture = MiniatureType.TRAFFIC_CONE.getTextureLocation();

    public void setMiniatureType(MiniatureType type) {
        if (type != null) {
            this.currentModel = type.getModelLocation();
            this.currentTexture = type.getTextureLocation();
        }
    }

    @Override
    public ResourceLocation getModelResource(MiniaturePlacementBlockEntity animatable) {
        return currentModel;
    }

    @Override
    public ResourceLocation getTextureResource(MiniaturePlacementBlockEntity animatable) {
        return currentTexture;
    }

    @Override
    public @Nullable ResourceLocation getAnimationResource(MiniaturePlacementBlockEntity animatable) {
        return null;
    }
}
