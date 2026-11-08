package com.microdrone.mod.client.model;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.config.car.CarConfig;
import com.microdrone.mod.config.car.CarConfigManager;
import com.microdrone.mod.entity.RcCarEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class RcCarGeoModel extends GeoModel<RcCarEntity> {
    private static final ResourceLocation DEFAULT_MODEL =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/rc_car.geo.json");
    private static final ResourceLocation DEFAULT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/entity/rc_car.png");
    private static final ResourceLocation DEFAULT_ANIMATION =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "animations/rc_car.animation.json");

    @Override
    public ResourceLocation getModelResource(RcCarEntity animatable) {
        CarConfig config = CarConfigManager.getConfig(animatable.getVariantId());
        return config != null ? config.getModelResource() : DEFAULT_MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(RcCarEntity animatable) {
        CarConfig config = CarConfigManager.getConfig(animatable.getVariantId());
        return config != null ? config.getTextureResource() : DEFAULT_TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(RcCarEntity animatable) {
        CarConfig config = CarConfigManager.getConfig(animatable.getVariantId());
        return config != null ? config.getAnimationResource() : DEFAULT_ANIMATION;
    }

    @Override
    public void setCustomAnimations(RcCarEntity animatable, long instanceId, AnimationState<RcCarEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        // 1. 前輪ステアリング操舵 (Y軸回転: ラジアン変換)
        float steerRad = animatable.getSteerAngle() * Mth.DEG_TO_RAD;
        GeoBone steerFL = this.getAnimationProcessor().getBone("steer_fl");
        GeoBone steerFR = this.getAnimationProcessor().getBone("steer_fr");
        GeoBone wheelRL = this.getAnimationProcessor().getBone("wheel_rl");
        GeoBone wheelRR = this.getAnimationProcessor().getBone("wheel_rr");

        if (steerFL != null) steerFL.setRotY(steerRad);
        if (steerFR != null) steerFR.setRotY(steerRad);

        // 2. 4輪サスペンションのストローク（タイヤ上下可動）
        // 車体全体のピッチ・ロール姿勢は RcCarGeoRenderer (poseStack) 側で一元管理。
        // ここではタイヤがフェンダー内へ出入りする微小ストロークを適用
        float suspScale = 6.0f;
        float suspFL = animatable.getSuspFL() * suspScale;
        float suspFR = animatable.getSuspFR() * suspScale;
        float suspRL = animatable.getSuspRL() * suspScale;
        float suspRR = animatable.getSuspRR() * suspScale;

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

