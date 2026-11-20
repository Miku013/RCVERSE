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
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(RcSpiderEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(RcSpiderEntity animatable) {
        return ANIMATION;
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

        // 水平スイング（前後・左右への振り出し角）
        // animSwingZ: 前後方向の変位, animSwingX: 左右方向の変位
        float swingAngle = (float) Math.atan2(state.animSwingZ, 0.6f);
        // 旋回入力や移動方向に応じたYaw回転
        leg.setRotY(defaultYaw + (isLeft ? -swingAngle : swingAngle));

        // 垂直リフト（足を上げる放物線軌道）
        float liftAmount = state.animLiftY; // 0.0m 〜 0.35m
        float liftRad = Mth.clamp(liftAmount * 1.5f, 0.0f, 0.7f); // 最大約40度

        if (isLeft) {
            // 左脚の持ち上げ（ロール軸／ピッチ軸連動）
            leg.setRotZ(-liftRad * 0.7f);
            if (lower != null) {
                lower.setRotZ(liftRad * 1.2f);
            }
        } else {
            // 右脚の持ち上げ
            leg.setRotZ(liftRad * 0.7f);
            if (lower != null) {
                lower.setRotZ(-liftRad * 1.2f);
            }
        }
    }
}
