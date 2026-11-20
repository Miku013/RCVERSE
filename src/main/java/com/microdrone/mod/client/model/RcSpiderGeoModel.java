package com.microdrone.mod.client.model;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.entity.spider.RcSpiderEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * RC多脚ロボット（ヘキサポッド）のGeckoLib 4モデル
 * - プロシージャル歩行IK（各脚の接地・リフト・前後スイング）
 * - 車体のピッチ・ロール姿勢傾斜
 * - 360度独立旋回タレット
 */
public class RcSpiderGeoModel extends GeoModel<RcSpiderEntity> {
    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/rc_spider.geo.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/entity/rc_spider.png");
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "animations/rc_spider.animation.json");

    @Override
    public ResourceLocation getModelResource(RcSpiderEntity animatable) {
        com.microdrone.mod.config.spider.SpiderConfig config = com.microdrone.mod.config.spider.SpiderConfigManager.getConfig(animatable.getVariantId());
        return config != null ? config.getModelResource() : MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(RcSpiderEntity animatable) {
        com.microdrone.mod.config.spider.SpiderConfig config = com.microdrone.mod.config.spider.SpiderConfigManager.getConfig(animatable.getVariantId());
        return config != null ? config.getTextureResource() : TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(RcSpiderEntity animatable) {
        com.microdrone.mod.config.spider.SpiderConfig config = com.microdrone.mod.config.spider.SpiderConfigManager.getConfig(animatable.getVariantId());
        return config != null ? config.getAnimationResource() : ANIMATION;
    }

    @Override
    public void setCustomAnimations(RcSpiderEntity entity, long instanceId, AnimationState<RcSpiderEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);

        // 1. 車体（chassis）の姿勢はレンダラーのPoseStackにて一括適用
        // 2. 独立旋回砲塔（turret）の旋回角反映
        GeoBone turret = this.getAnimationProcessor().getBone("turret");
        if (turret != null) {
            float turretYawRad = -entity.clientTurretYaw * Mth.DEG_TO_RAD;
            turret.setRotY(turretYawRad);
        }

        // 3. 歩行時のボディ微振動（ボビング）
        GeoBone body = this.getAnimationProcessor().getBone("body");
        if (body != null) {
            float bobbing = (float) Math.sin(entity.walkTimer * 2.0f) * entity.currentMoveSpeed * 0.6f;
            body.setPosY(bobbing);
        }

        // 3. 6本の脚のプロシージャル歩行モーション
        // 初期オフセット角（モデル定義の初期角度）
        // 左側: leg_fl(+15 deg), leg_ml(0 deg), leg_rl(-15 deg)
        // 右側: leg_fr(-15 deg), leg_mr(0 deg), leg_rr(+15 deg)
        applyLegAnimation(entity, RcSpiderEntity.LEG_FL, "leg_fl", "leg_fl_lower", 15.0f * Mth.DEG_TO_RAD, true);
        applyLegAnimation(entity, RcSpiderEntity.LEG_ML, "leg_ml", "leg_ml_lower", 0.0f, true);
        applyLegAnimation(entity, RcSpiderEntity.LEG_RL, "leg_rl", "leg_rl_lower", -15.0f * Mth.DEG_TO_RAD, true);

        applyLegAnimation(entity, RcSpiderEntity.LEG_FR, "leg_fr", "leg_fr_lower", -15.0f * Mth.DEG_TO_RAD, false);
        applyLegAnimation(entity, RcSpiderEntity.LEG_MR, "leg_mr", "leg_mr_lower", 0.0f, false);
        applyLegAnimation(entity, RcSpiderEntity.LEG_RR, "leg_rr", "leg_rr_lower", 15.0f * Mth.DEG_TO_RAD, false);
    }

    /**
     * 各脚のIKアニメーションをボーンに適用
     */
    private void applyLegAnimation(RcSpiderEntity entity, int legIndex, String boneName, String lowerBoneName, float defaultYaw, boolean isLeft) {
        GeoBone leg = this.getAnimationProcessor().getBone(boneName);
        GeoBone lower = this.getAnimationProcessor().getBone(lowerBoneName);
        if (leg == null) return;

        RcSpiderEntity.LegState state = entity.legs[legIndex];
        if (state == null) return;

        // 1. 前後スイング（Y軸旋回）: 前進・旋回時に足を前後に大きく振る！
        float swing = state.animSwingZ;
        leg.setRotY(defaultYaw + (isLeft ? -swing : swing));

        // 2. ステップリフト（足を上げる）
        float lift = state.animLiftY; // 0.0 〜 1.0
        float liftRad = lift * 0.25f; // 最大約15度持ち上げる

        if (isLeft) {
            // 左脚: Z軸回転を負にすると外側斜め上へ持ち上がる
            leg.setRotZ(-liftRad);
            if (lower != null) {
                lower.setRotZ(liftRad * 0.5f);
            }
        } else {
            // 右脚: Z軸回転を正にすると外側斜め上へ持ち上がる
            leg.setRotZ(liftRad);
            if (lower != null) {
                lower.setRotZ(-liftRad * 0.5f);
            }
        }
    }
}
