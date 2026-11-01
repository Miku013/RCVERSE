package com.microdrone.mod.client.model;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.entity.RcPlaneEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class RcPlaneGeoModel extends GeoModel<RcPlaneEntity> {
    private static final ResourceLocation MODEL_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/rc_plane.geo.json");
    private static final ResourceLocation TEXTURE_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/entity/rc_plane.png");
    private static final ResourceLocation ANIMATION_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "animations/rc_plane.animation.json");

    @Override
    public ResourceLocation getModelResource(RcPlaneEntity animatable) {
        return MODEL_RESOURCE;
    }

    @Override
    public ResourceLocation getTextureResource(RcPlaneEntity animatable) {
        return TEXTURE_RESOURCE;
    }

    @Override
    public ResourceLocation getAnimationResource(RcPlaneEntity animatable) {
        return ANIMATION_RESOURCE;
    }

    @Override
    public void setCustomAnimations(RcPlaneEntity animatable, long instanceId, AnimationState<RcPlaneEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        // 1. プロペラ回転 (Z軸回転)
        float propRotRad = animatable.getPropRotation() * Mth.DEG_TO_RAD;
        GeoBone propBone = this.getAnimationProcessor().getBone("propeller");
        if (propBone != null) {
            propBone.setRotZ(propRotRad);
        }

        // 2. エルロン舵面可動 (左右逆位相、X軸回転)
        // 旋回・ロール入力による舵面角 (最大約25度)
        float aileronInput = animatable.getAileronInput();
        float aileronAngleRad = aileronInput * 25.0f * Mth.DEG_TO_RAD;

        GeoBone aileronL = this.getAnimationProcessor().getBone("aileron_left");
        GeoBone aileronR = this.getAnimationProcessor().getBone("aileron_right");
        if (aileronL != null) {
            aileronL.setRotX(aileronAngleRad);
        }
        if (aileronR != null) {
            aileronR.setRotX(-aileronAngleRad);
        }

        // 3. エレベーター舵面可動 (昇降舵、X軸回転)
        // ピッチ入力による舵面角 (最大約25度)
        float elevatorInput = animatable.getElevatorInput();
        float elevatorAngleRad = elevatorInput * 25.0f * Mth.DEG_TO_RAD;
        GeoBone elevator = this.getAnimationProcessor().getBone("elevator");
        if (elevator != null) {
            elevator.setRotX(elevatorAngleRad);
        }

        // 4. ラダー舵面可動 (方向舵、Y軸回転)
        // ヨー入力による舵面角 (最大約25度)
        // tail_gear (尾輪) は rudder の子ボーンのため、地上タキシング時に自動的にステア連動！
        float rudderInput = animatable.getRudderInput();
        float rudderAngleRad = rudderInput * 25.0f * Mth.DEG_TO_RAD;
        GeoBone rudder = this.getAnimationProcessor().getBone("rudder");
        if (rudder != null) {
            rudder.setRotY(rudderAngleRad);
        }

        // 5. 車輪回転 (地上走行時、X軸回転)
        float wheelRad = animatable.wheelRotation * Mth.DEG_TO_RAD;
        GeoBone wheelL = this.getAnimationProcessor().getBone("wheel_left");
        GeoBone wheelR = this.getAnimationProcessor().getBone("wheel_right");
        GeoBone tailWheel = this.getAnimationProcessor().getBone("tail_wheel");
        if (wheelL != null) {
            wheelL.setRotX(wheelRad);
        }
        if (wheelR != null) {
            wheelR.setRotX(wheelRad);
        }
        if (tailWheel != null) {
            tailWheel.setRotX(wheelRad);
        }
    }
}

