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

        // 1. 車体（chassis）のピッチ角・ロール角傾斜 (4点サスペンション連動)
        GeoBone chassis = this.getAnimationProcessor().getBone("chassis");
        if (chassis != null) {
            float pitchRad = animatable.getPitchAngle() * Mth.DEG_TO_RAD;
            float rollRad = animatable.getRollAngle() * Mth.DEG_TO_RAD;
            chassis.setRotX(pitchRad);
            chassis.setRotZ(rollRad);
        }

        // 2. 前輪ステアリング操舵 (Y軸回転: ラジアン変換)
        float steerRad = animatable.getSteerAngle() * Mth.DEG_TO_RAD;
        GeoBone steerFL = this.getAnimationProcessor().getBone("steer_fl");
        GeoBone steerFR = this.getAnimationProcessor().getBone("steer_fr");
        GeoBone wheelRL = this.getAnimationProcessor().getBone("wheel_rl");
        GeoBone wheelRR = this.getAnimationProcessor().getBone("wheel_rr");

        if (steerFL != null) steerFL.setRotY(steerRad);
        if (steerFR != null) steerFR.setRotY(steerRad);

        // 3. 4輪サスペンションのストローク（タイヤ上下可動）
        // GeckoLibピクセル単位 (1ブロック = 16px) でサス縮み量をオフセット
        // サスが縮む（正）とタイヤは車体に対して上（+Y）に持ち上がる
        float suspFL = animatable.getSuspFL() * 16.0f;
        float suspFR = animatable.getSuspFR() * 16.0f;
        float suspRL = animatable.getSuspRL() * 16.0f;
        float suspRR = animatable.getSuspRR() * 16.0f;

        if (steerFL != null) steerFL.setPosY(suspFL);
        if (steerFR != null) steerFR.setPosY(suspFR);
        if (wheelRL != null) wheelRL.setPosY(suspRL);
        if (wheelRR != null) wheelRR.setPosY(suspRR);

        // 4. 4輪タイヤ回転 (車輪モデルの転がり軸 = ローカルY軸!)
        // 左輪(FL, RL)は rotZ=90度、右輪(FR, RR)は rotZ=-90度 で横に倒れているため、
        // 転がり回転は Y 軸回転で行う。X軸回転は0にリセットして横ブレを完全防止。
        float wheelRad = animatable.getWheelRotation() * Mth.DEG_TO_RAD;
        GeoBone wheelFL = this.getAnimationProcessor().getBone("wheel_fl");
        GeoBone wheelFR = this.getAnimationProcessor().getBone("wheel_fr");

        if (wheelFL != null) {
            wheelFL.setRotX(0.0f);
            wheelFL.setRotY(-wheelRad);
        }
        if (wheelFR != null) {
            wheelFR.setRotX(0.0f);
            wheelFR.setRotY(wheelRad);
        }
        if (wheelRL != null) {
            wheelRL.setRotX(0.0f);
            wheelRL.setRotY(-wheelRad);
        }
        if (wheelRR != null) {
            wheelRR.setRotX(0.0f);
            wheelRR.setRotY(wheelRad);
        }
    }
}

