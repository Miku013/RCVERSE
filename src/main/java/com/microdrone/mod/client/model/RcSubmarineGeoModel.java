package com.microdrone.mod.client.model;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.config.submarine.SubmarineConfig;
import com.microdrone.mod.config.submarine.SubmarineConfigManager;
import com.microdrone.mod.entity.RcSubmarineEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class RcSubmarineGeoModel extends GeoModel<RcSubmarineEntity> {
    private static final ResourceLocation DEFAULT_MODEL =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/rc_submarine.geo.json");
    private static final ResourceLocation DEFAULT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/entity/rc_submarine.png");
    private static final ResourceLocation DEFAULT_ANIMATION =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "animations/rc_submarine.animation.json");

    @Override
    public ResourceLocation getModelResource(RcSubmarineEntity animatable) {
        SubmarineConfig config = SubmarineConfigManager.getConfig(animatable.getVariantId());
        return config != null ? config.getModelResource() : DEFAULT_MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(RcSubmarineEntity animatable) {
        SubmarineConfig config = SubmarineConfigManager.getConfig(animatable.getVariantId());
        return config != null ? config.getTextureResource() : DEFAULT_TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(RcSubmarineEntity animatable) {
        SubmarineConfig config = SubmarineConfigManager.getConfig(animatable.getVariantId());
        return config != null ? config.getAnimationResource() : DEFAULT_ANIMATION;
    }

    @Override
    public void setCustomAnimations(RcSubmarineEntity animatable, long instanceId, AnimationState<RcSubmarineEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        // 1. 方向舵操舵 (Y軸回転: ラジアン変換)
        float rudderAngleRad = animatable.getRudderAngle() * Mth.DEG_TO_RAD;
        GeoBone rudder = this.getAnimationProcessor().getBone("rudder");
        if (rudder != null) {
            rudder.setRotY(rudderAngleRad);
        }

        // 2. 潜舵操舵 (X軸回転: 昇降舵)
        float divePlaneAngleRad = animatable.getDivePlaneAngle() * Mth.DEG_TO_RAD;
        GeoBone divePlane = this.getAnimationProcessor().getBone("dive_plane");
        if (divePlane != null) {
            divePlane.setRotX(divePlaneAngleRad);
        }

        // 3. スクリュープロペラ回転 (Z軸回転)
        float propRotRad = animatable.getPropellerRotation() * Mth.DEG_TO_RAD;
        GeoBone propeller = this.getAnimationProcessor().getBone("propeller");
        if (propeller != null) {
            propeller.setRotZ(propRotRad);
        }
    }
}

