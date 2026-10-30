package com.microdrone.mod.client.model;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.entity.RcCarEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class RcCarGeoModel extends GeoModel<RcCarEntity> {
    private static final ResourceLocation MODEL_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/rc_car.geo.json");
    private static final ResourceLocation TEXTURE_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/entity/rc_car.png");
    private static final ResourceLocation ANIMATION_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "animations/rc_car.animation.json");

    @Override
    public ResourceLocation getModelResource(RcCarEntity animatable) {
        return MODEL_RESOURCE;
    }

    @Override
    public ResourceLocation getTextureResource(RcCarEntity animatable) {
        return TEXTURE_RESOURCE;
    }

    @Override
    public ResourceLocation getAnimationResource(RcCarEntity animatable) {
        return ANIMATION_RESOURCE;
    }

    @Override
    public void setCustomAnimations(RcCarEntity animatable, long instanceId, AnimationState<RcCarEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        // 1. 前輪ステアリング操舵 (Y軸回転: ラジアン変換)
        float steerRad = animatable.getSteerAngle() * Mth.DEG_TO_RAD;
        GeoBone steerFL = this.getAnimationProcessor().getBone("steer_fl");
        GeoBone steerFR = this.getAnimationProcessor().getBone("steer_fr");

        if (steerFL != null) steerFL.setRotY(steerRad);
        if (steerFR != null) steerFR.setRotY(steerRad);

        // 2. 4輪タイヤ回転 (X軸回転: ラジアン変換)
        float wheelRad = animatable.getWheelRotation() * Mth.DEG_TO_RAD;
        GeoBone wheelFL = this.getAnimationProcessor().getBone("wheel_fl");
        GeoBone wheelFR = this.getAnimationProcessor().getBone("wheel_fr");
        GeoBone wheelRL = this.getAnimationProcessor().getBone("wheel_rl");
        GeoBone wheelRR = this.getAnimationProcessor().getBone("wheel_rr");

        if (wheelFL != null) wheelFL.setRotX(wheelRad);
        if (wheelFR != null) wheelFR.setRotX(wheelRad);
        if (wheelRL != null) wheelRL.setRotX(wheelRad);
        if (wheelRR != null) wheelRR.setRotX(wheelRad);
    }
}

