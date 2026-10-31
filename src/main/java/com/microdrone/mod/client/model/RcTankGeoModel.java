package com.microdrone.mod.client.model;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.entity.RcTankEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class RcTankGeoModel extends GeoModel<RcTankEntity> {
    private static final ResourceLocation MODEL_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/rc_tank.geo.json");
    private static final ResourceLocation TEXTURE_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/entity/rc_tank.png");
    private static final ResourceLocation ANIMATION_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "animations/rc_tank.animation.json");

    @Override
    public ResourceLocation getModelResource(RcTankEntity animatable) {
        return MODEL_RESOURCE;
    }

    @Override
    public ResourceLocation getTextureResource(RcTankEntity animatable) {
        return TEXTURE_RESOURCE;
    }

    @Override
    public ResourceLocation getAnimationResource(RcTankEntity animatable) {
        return ANIMATION_RESOURCE;
    }

    @Override
    public void setCustomAnimations(RcTankEntity animatable, long instanceId, AnimationState<RcTankEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        // 1. 履帯大ギア・転輪回転（初期回転 rotZ=±90度 のためローカルY軸で回転）
        float leftRad = animatable.getLeftTrackRotation() * Mth.DEG_TO_RAD;
        float rightRad = animatable.getRightTrackRotation() * Mth.DEG_TO_RAD;

        GeoBone spFL = this.getAnimationProcessor().getBone("sprocket_fl");
        GeoBone spRL = this.getAnimationProcessor().getBone("sprocket_rl");
        GeoBone idlerL = this.getAnimationProcessor().getBone("idler_l");
        GeoBone wheelsL = this.getAnimationProcessor().getBone("wheels_l");

        if (spFL != null) { spFL.setRotX(0.0f); spFL.setRotY(-leftRad); }
        if (spRL != null) { spRL.setRotX(0.0f); spRL.setRotY(-leftRad); }
        if (idlerL != null) { idlerL.setRotX(0.0f); idlerL.setRotY(-leftRad); }
        if (wheelsL != null) { wheelsL.setRotX(0.0f); wheelsL.setRotY(-leftRad); }

        GeoBone spFR = this.getAnimationProcessor().getBone("sprocket_fr");
        GeoBone spRR = this.getAnimationProcessor().getBone("sprocket_rr");
        GeoBone idlerR = this.getAnimationProcessor().getBone("idler_r");
        GeoBone wheelsR = this.getAnimationProcessor().getBone("wheels_r");

        if (spFR != null) { spFR.setRotX(0.0f); spFR.setRotY(rightRad); }
        if (spRR != null) { spRR.setRotX(0.0f); spRR.setRotY(rightRad); }
        if (idlerR != null) { idlerR.setRotX(0.0f); idlerR.setRotY(rightRad); }
        if (wheelsR != null) { wheelsR.setRotX(0.0f); wheelsR.setRotY(rightRad); }

        // 2. 砲塔旋回（ローカル Y 軸）
        float turretYawRad = animatable.getTurretYaw() * Mth.DEG_TO_RAD;
        GeoBone turret = this.getAnimationProcessor().getBone("turret");
        if (turret != null) {
            turret.setRotY(-turretYawRad);
        }

        // 3. 砲身仰俯角（初期回転 [-90, 0, 0] に対してピッチ角を加算）
        float pitchDeg = animatable.getCannonPitch();
        GeoBone cannon = this.getAnimationProcessor().getBone("cannon");
        if (cannon != null) {
            cannon.setRotX((-90.0f - pitchDeg) * Mth.DEG_TO_RAD);
        }

        // 4. 発砲リコイル（後座反動）
        float recoil = animatable.getRecoil();
        GeoBone recoilBone = this.getAnimationProcessor().getBone("recoil");
        if (recoilBone != null) {
            // cannonのローカル座標系で後ろ(+Yまたは+Z)に後座
            recoilBone.setPosZ(recoil * -3.0f);
        }
    }
}
