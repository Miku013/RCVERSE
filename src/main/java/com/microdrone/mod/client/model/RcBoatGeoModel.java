package com.microdrone.mod.client.model;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.config.boat.BoatConfig;
import com.microdrone.mod.config.boat.BoatConfigManager;
import com.microdrone.mod.entity.RcBoatEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class RcBoatGeoModel extends GeoModel<RcBoatEntity> {
    private static final ResourceLocation DEFAULT_MODEL =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/rc_boat.geo.json");
    private static final ResourceLocation DEFAULT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/entity/rc_boat.png");
    private static final ResourceLocation DEFAULT_ANIMATION =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "animations/rc_boat.animation.json");

    @Override
    public ResourceLocation getModelResource(RcBoatEntity animatable) {
        BoatConfig config = BoatConfigManager.getConfig(animatable.getVariantId());
        return config != null ? config.getModelResource() : DEFAULT_MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(RcBoatEntity animatable) {
        BoatConfig config = BoatConfigManager.getConfig(animatable.getVariantId());
        return config != null ? config.getTextureResource() : DEFAULT_TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(RcBoatEntity animatable) {
        BoatConfig config = BoatConfigManager.getConfig(animatable.getVariantId());
        return config != null ? config.getAnimationResource() : DEFAULT_ANIMATION;
    }

    @Override
    public void setCustomAnimations(RcBoatEntity animatable, long instanceId, AnimationState<RcBoatEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        // 1. ラダー操舵 (Y軸回転: ラジアン変換)
        float rudderAngleRad = animatable.getRudderAngle() * Mth.DEG_TO_RAD;
        GeoBone rudder = this.getAnimationProcessor().getBone("rudder");
        if (rudder != null) {
            rudder.setRotY(rudderAngleRad);
        }

        // 2. ツインスクリュープロペラ回転 (Z軸回転)
        float propRotRad = animatable.getPropellerRotation() * Mth.DEG_TO_RAD;
        GeoBone propLeft = this.getAnimationProcessor().getBone("propeller_left");
        GeoBone propRight = this.getAnimationProcessor().getBone("propeller_right");
        if (propLeft != null) {
            propLeft.setRotZ(propRotRad);
        }
        if (propRight != null) {
            propRight.setRotZ(-propRotRad); // ツインスクリューのカウンタローテーション
        }
    }
}

