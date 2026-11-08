package com.microdrone.mod.client.model;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.config.drone.DroneConfig;
import com.microdrone.mod.config.drone.DroneConfigManager;
import com.microdrone.mod.entity.DroneEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class DroneGeoModel extends GeoModel<DroneEntity> {
    private static final ResourceLocation DEFAULT_MODEL =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/drone.geo.json");
    private static final ResourceLocation DEFAULT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/entity/drone.png");
    private static final ResourceLocation DEFAULT_ANIMATION =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "animations/drone.animation.json");

    @Override
    public ResourceLocation getModelResource(DroneEntity animatable) {
        DroneConfig config = DroneConfigManager.getConfig(animatable.getVariantId());
        return config != null ? config.getModelResource() : DEFAULT_MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(DroneEntity animatable) {
        DroneConfig config = DroneConfigManager.getConfig(animatable.getVariantId());
        return config != null ? config.getTextureResource() : DEFAULT_TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(DroneEntity animatable) {
        DroneConfig config = DroneConfigManager.getConfig(animatable.getVariantId());
        return config != null ? config.getAnimationResource() : DEFAULT_ANIMATION;
    }

    @Override
    public void setCustomAnimations(DroneEntity animatable, long instanceId, AnimationState<DroneEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        // 飛行中または操縦中の場合、4基のプロペラを高速回転
        if (animatable.isPiloted() || !animatable.onGround()) {
            float rot = (animatable.tickCount + animationState.getPartialTick()) * 1.5F; // 高速スピン

            GeoBone propFR = this.getAnimationProcessor().getBone("propeller_fr");
            GeoBone propFL = this.getAnimationProcessor().getBone("propeller_fl");
            GeoBone propRR = this.getAnimationProcessor().getBone("propeller_rr");
            GeoBone propRL = this.getAnimationProcessor().getBone("propeller_rl");

            if (propFR != null) propFR.setRotY(rot);
            if (propFL != null) propFL.setRotY(-rot);
            if (propRR != null) propRR.setRotY(-rot);
            if (propRL != null) propRL.setRotY(rot);
        }
    }
}
