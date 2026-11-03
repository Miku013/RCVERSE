package com.microdrone.mod.client.model;

import com.microdrone.mod.block.miniature.MiniaturePlacementBlockEntity;
import com.microdrone.mod.block.miniature.MiniatureType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * コース小物を動的に切り替えて描画する汎用GeckoLibモデル
 * ガードレールのシームレス連結（重複ポールの非表示）にも対応
 */
public class GenericMiniatureModel extends GeoModel<MiniaturePlacementBlockEntity> {
    private ResourceLocation currentModel = MiniatureType.TRAFFIC_CONE.getModelLocation();
    private ResourceLocation currentTexture = MiniatureType.TRAFFIC_CONE.getTextureLocation();

    private boolean hidePostLeft = false;
    private boolean hidePostRight = false;

    public void setMiniatureType(MiniatureType type) {
        if (type != null) {
            this.currentModel = type.getModelLocation();
            this.currentTexture = type.getTextureLocation();
        }
    }

    public void setGuardrailConnection(boolean hideLeft, boolean hideRight) {
        this.hidePostLeft = hideLeft;
        this.hidePostRight = hideRight;
    }

    @Override
    public void setCustomAnimations(MiniaturePlacementBlockEntity animatable, long instanceId, AnimationState<MiniaturePlacementBlockEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        GeoBone postLeft = getAnimationProcessor().getBone("post_left");
        if (postLeft != null) {
            postLeft.setHidden(this.hidePostLeft);
        }

        GeoBone postRight = getAnimationProcessor().getBone("post_right");
        if (postRight != null) {
            postRight.setHidden(this.hidePostRight);
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
