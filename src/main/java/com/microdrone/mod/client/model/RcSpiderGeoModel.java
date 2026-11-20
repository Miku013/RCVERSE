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

        // 4. 6本の脚のプロシージャル歩行モーション（モデルの初期姿勢に差分加算）
        applyLegAnimation(entity, RcSpiderEntity.LEG_FL, "leg_fl", "leg_fl_lower", true);
        applyLegAnimation(entity, RcSpiderEntity.LEG_ML, "leg_ml", "leg_ml_lower", true);
        applyLegAnimation(entity, RcSpiderEntity.LEG_RL, "leg_rl", "leg_rl_lower", true);

        applyLegAnimation(entity, RcSpiderEntity.LEG_FR, "leg_fr", "leg_fr_lower", false);
        applyLegAnimation(entity, RcSpiderEntity.LEG_MR, "leg_mr", "leg_mr_lower", false);
        applyLegAnimation(entity, RcSpiderEntity.LEG_RR, "leg_rr", "leg_rr_lower", false);
    }

    /**
     * 各脚のIKアニメーションをボーンに適用（Blockbenchモデル本来の初期ポーズを完全保護）
     */
    private void applyLegAnimation(RcSpiderEntity entity, int legIndex, String boneName, String lowerBoneName, boolean isLeft) {
        GeoBone leg = this.getAnimationProcessor().getBone(boneName);
        GeoBone lower = this.getAnimationProcessor().getBone(lowerBoneName);
        if (leg == null) return;

        RcSpiderEntity.LegState state = entity.legs[legIndex];
        if (state == null) return;

        // Blockbenchモデル定義の初期回転角（レストポーズ）を取得
        float initRotX = (leg.getInitialSnapshot() != null) ? leg.getInitialSnapshot().getRotX() : leg.getRotX();
        float initRotY = (leg.getInitialSnapshot() != null) ? leg.getInitialSnapshot().getRotY() : leg.getRotY();
        float initRotZ = (leg.getInitialSnapshot() != null) ? leg.getInitialSnapshot().getRotZ() : leg.getRotZ();

        // 1. 前後スイング（Y軸旋回）: 初期角度に対して前進・旋回スイングを加算！
        float swing = state.animSwingZ;
        leg.setRotX(initRotX);
        leg.setRotY(initRotY + (isLeft ? -swing : swing));

        // 2. ステップリフト（足を上げる）: 初期角度に対して持ち上げ角を加算！
        float lift = state.animLiftY; // 0.0 〜 1.0
        float liftRad = lift * 0.25f; // 最大約15度持ち上げる
        leg.setRotZ(initRotZ + (isLeft ? -liftRad : liftRad));

        // 3. 下腿（膝）の連動屈曲
        if (lower != null) {
            float lowerInitX = (lower.getInitialSnapshot() != null) ? lower.getInitialSnapshot().getRotX() : lower.getRotX();
            float lowerInitY = (lower.getInitialSnapshot() != null) ? lower.getInitialSnapshot().getRotY() : lower.getRotY();
            float lowerInitZ = (lower.getInitialSnapshot() != null) ? lower.getInitialSnapshot().getRotZ() : lower.getRotZ();
            lower.setRotX(lowerInitX);
            lower.setRotY(lowerInitY);
            lower.setRotZ(lowerInitZ + (isLeft ? liftRad * 0.4f : -liftRad * 0.4f));
        }
    }
}
