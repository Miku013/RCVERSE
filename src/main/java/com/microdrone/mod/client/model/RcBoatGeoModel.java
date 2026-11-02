package com.microdrone.mod.client.model;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.entity.RcBoatEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class RcBoatGeoModel extends GeoModel<RcBoatEntity> {
    private static final ResourceLocation MODEL_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/rc_boat.geo.json");
    private static final ResourceLocation TEXTURE_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/entity/rc_boat.png");
    private static final ResourceLocation ANIMATION_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "animations/rc_boat.animation.json");

    @Override
    public ResourceLocation getModelResource(RcBoatEntity animatable) {
        return MODEL_RESOURCE;
    }

    @Override
    public ResourceLocation getTextureResource(RcBoatEntity animatable) {
        return TEXTURE_RESOURCE;
    }

    @Override
    public ResourceLocation getAnimationResource(RcBoatEntity animatable) {
        return ANIMATION_RESOURCE;
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
