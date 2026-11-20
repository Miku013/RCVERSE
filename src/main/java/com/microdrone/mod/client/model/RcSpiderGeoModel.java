package com.microdrone.mod.client.model;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.entity.spider.RcSpiderEntity;
import com.microdrone.mod.entity.spider.ik.SpiderLegIkSolver;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * RC多脚ロボット（ヘキサポッド）のGeckoLib 4モデル
 * - プロシージャル歩行IK（各脚の接地・リフト・前後スイング）
 * - 2リンク3D幾何学的IKソルバーによる地形凹凸追従
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

        // 4. 6本の脚のプロシージャル歩行 & 3D IK地形適応（モデル本来の初期ポーズを完全保護）
        applyLegAnimation(entity, RcSpiderEntity.LEG_FL, "leg_fl", "leg_fl_lower", "foot_fl", true);
        applyLegAnimation(entity, RcSpiderEntity.LEG_ML, "leg_ml", "leg_ml_lower", "foot_ml", true);
        applyLegAnimation(entity, RcSpiderEntity.LEG_RL, "leg_rl", "leg_rl_lower", "foot_rl", true);

        applyLegAnimation(entity, RcSpiderEntity.LEG_FR, "leg_fr", "leg_fr_lower", "foot_fr", false);
        applyLegAnimation(entity, RcSpiderEntity.LEG_MR, "leg_mr", "leg_mr_lower", "foot_mr", false);
        applyLegAnimation(entity, RcSpiderEntity.LEG_RR, "leg_rr", "leg_rr_lower", "foot_rr", false);
    }

    /**
     * 各脚のIKアニメーションをボーンに適用（Blockbenchモデル本来の初期ポーズを完全保護）
     */
    private void applyLegAnimation(RcSpiderEntity entity, int legIndex, String boneName, String lowerBoneName, String footBoneName, boolean isLeft) {
        GeoBone leg = this.getAnimationProcessor().getBone(boneName);
        GeoBone lower = this.getAnimationProcessor().getBone(lowerBoneName);
        if (leg == null) return;

        RcSpiderEntity.LegState state = entity.legs[legIndex];
        if (state == null) return;

        // Blockbenchモデル定義の初期回転角（レストポーズ）を取得
        float initRotX = (leg.getInitialSnapshot() != null) ? leg.getInitialSnapshot().getRotX() : leg.getRotX();
        float initRotY = (leg.getInitialSnapshot() != null) ? leg.getInitialSnapshot().getRotY() : leg.getRotY();
        float initRotZ = (leg.getInitialSnapshot() != null) ? leg.getInitialSnapshot().getRotZ() : leg.getRotZ();

        // 足先ボーン取得（footrl 等の表記ゆれフォールバック対応）
        GeoBone foot = this.getAnimationProcessor().getBone(footBoneName);
        if (foot == null && "foot_rl".equals(footBoneName)) {
            foot = this.getAnimationProcessor().getBone("footrl");
        }

        // ボーン長の計算（デフォルト値 10.75, 12.30）
        float lenUpper = 10.75f;
        float lenLower = 12.30f;
        if (lower != null) {
            float dx = lower.getPivotX() - leg.getPivotX();
            float dy = lower.getPivotY() - leg.getPivotY();
            float dz = lower.getPivotZ() - leg.getPivotZ();
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 0.1f) lenUpper = dist;

            if (foot != null) {
                float fdx = foot.getPivotX() - lower.getPivotX();
                float fdy = foot.getPivotY() - lower.getPivotY();
                float fdz = foot.getPivotZ() - lower.getPivotZ();
                float fdist = (float) Math.sqrt(fdx * fdx + fdy * fdy + fdz * fdz);
                if (fdist > 0.1f) lenLower = fdist;
            }
        }

        // 1. IKソルバーによる地形高低差（凹凸適応）計算
        SpiderLegIkSolver.IkResult ik = SpiderLegIkSolver.solveTerrainAdaptation(
                state.groundDeltaY, state.animSwingZ, lenUpper, lenLower, isLeft);

        // 2. 前後スイング（Y軸旋回）: 初期角度に対して前進・旋回スイングを加算！
        float swing = ik.yawRad;
        leg.setRotX(initRotX);
        leg.setRotY(initRotY + (isLeft ? -swing : swing));

        // 3. ステップリフト（足を上げる）と地形追従ピッチの合成
        float lift = state.animLiftY; // 0.0 〜 1.0
        float liftRad = lift * 0.25f; // 最大約15度持ち上げる
        float totalPitchOffset = (isLeft ? -liftRad : liftRad) + (isLeft ? ik.pitchRad : -ik.pitchRad);
        leg.setRotZ(initRotZ + totalPitchOffset);

        // 4. 下腿（膝）の連動屈曲（リフト + 地形適応IK膝曲げ）
        if (lower != null) {
            float lowerInitX = (lower.getInitialSnapshot() != null) ? lower.getInitialSnapshot().getRotX() : lower.getRotX();
            float lowerInitY = (lower.getInitialSnapshot() != null) ? lower.getInitialSnapshot().getRotY() : lower.getRotY();
            float lowerInitZ = (lower.getInitialSnapshot() != null) ? lower.getInitialSnapshot().getRotZ() : lower.getRotZ();
            lower.setRotX(lowerInitX);
            lower.setRotY(lowerInitY);

            float totalKneeOffset = (isLeft ? liftRad * 0.4f : -liftRad * 0.4f) + (isLeft ? ik.kneeRad : -ik.kneeRad);
            lower.setRotZ(lowerInitZ + totalKneeOffset);
        }
    }
}
