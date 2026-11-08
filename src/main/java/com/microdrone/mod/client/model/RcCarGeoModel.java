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

        // 自転車 / バイク用ハンドル・フォークステアリング
        GeoBone forkHandle = this.getAnimationProcessor().getBone("fork_and_handle");
        if (forkHandle == null) forkHandle = this.getAnimationProcessor().getBone("handle");
        if (forkHandle != null) {
            float initRotZ = (forkHandle.getInitialSnapshot() != null) ? forkHandle.getInitialSnapshot().getRotZ() : forkHandle.getRotZ();
            float initRotX = (forkHandle.getInitialSnapshot() != null) ? forkHandle.getInitialSnapshot().getRotX() : forkHandle.getRotX();
            float initRotY = (forkHandle.getInitialSnapshot() != null) ? forkHandle.getInitialSnapshot().getRotY() : forkHandle.getRotY();
            if (Math.abs(initRotZ + (90.0f * Mth.DEG_TO_RAD)) < 0.25f) {
                // Z軸が-90度のモデリング: ワールドY軸旋回はローカルX軸
                forkHandle.setRotX(initRotX - steerRad);
            } else {
                forkHandle.setRotY(initRotY + steerRad);
            }
        }

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

        // 4. タイヤ回転
        // モデルのボーン設計（Z軸横倒しモデリングか、直立モデリングか）を自動判別して適用
        float wheelRad = animatable.getWheelRotation() * Mth.DEG_TO_RAD;
        GeoBone wheelFL = this.getAnimationProcessor().getBone("wheel_fl");
        GeoBone wheelFR = this.getAnimationProcessor().getBone("wheel_fr");
        GeoBone wheelF = this.getAnimationProcessor().getBone("wheel_f");
        GeoBone wheelR = this.getAnimationProcessor().getBone("wheel_r");

        applyWheelRoll(wheelFL, wheelRad, false); // 左前
        applyWheelRoll(wheelFR, wheelRad, true);  // 右前
        applyWheelRoll(wheelRL, wheelRad, false); // 左後
        applyWheelRoll(wheelRR, wheelRad, true);  // 右後
        applyWheelRoll(wheelR, wheelRad, false);  // 自転車後輪

        // 自転車前輪 wheel_f (フォークのZ-90度と子のZ+90度が相殺されて直立しているため、ローカルX軸で縦回転)
        if (wheelF != null) {
            float initX = (wheelF.getInitialSnapshot() != null) ? wheelF.getInitialSnapshot().getRotX() : wheelF.getRotX();
            float initY = (wheelF.getInitialSnapshot() != null) ? wheelF.getInitialSnapshot().getRotY() : wheelF.getRotY();
            float initZ = (wheelF.getInitialSnapshot() != null) ? wheelF.getInitialSnapshot().getRotZ() : wheelF.getRotZ();
            wheelF.setRotZ(initZ);
            wheelF.setRotY(initY);
            wheelF.setRotX(initX - wheelRad);
        }

        // 自転車用クランク回転＆村人ペダリング連動
        GeoBone crank = this.getAnimationProcessor().getBone("crank");
        if (crank != null) {
            float initCrankX = (crank.getInitialSnapshot() != null) ? crank.getInitialSnapshot().getRotX() : crank.getRotX();
            float crankRad = wheelRad * 0.75f;
            crank.setRotX(initCrankX - crankRad);

            // 村人の足（太もも・すね）の上下ペダリングアニメーション
            float pedalSwing = Mth.sin(crankRad) * (14.0f * Mth.DEG_TO_RAD);
            GeoBone legR = this.getAnimationProcessor().getBone("leg_r");
            GeoBone legL = this.getAnimationProcessor().getBone("leg_l");
            GeoBone shinR = this.getAnimationProcessor().getBone("shin_r");
            GeoBone shinL = this.getAnimationProcessor().getBone("shin_l");

            if (legR != null) {
                float init = (legR.getInitialSnapshot() != null) ? legR.getInitialSnapshot().getRotX() : legR.getRotX();
                legR.setRotX(init + pedalSwing);
            }
            if (legL != null) {
                float init = (legL.getInitialSnapshot() != null) ? legL.getInitialSnapshot().getRotX() : legL.getRotX();
                legL.setRotX(init - pedalSwing);
            }
            if (shinR != null) {
                float init = (shinR.getInitialSnapshot() != null) ? shinR.getInitialSnapshot().getRotX() : shinR.getRotX();
                shinR.setRotX(init - pedalSwing * 0.5f);
            }
            if (shinL != null) {
                float init = (shinL.getInitialSnapshot() != null) ? shinL.getInitialSnapshot().getRotX() : shinL.getRotX();
                shinL.setRotX(init + pedalSwing * 0.5f);
            }
        }

        // 5. リトラクタブルヘッドライト開閉アニメーション (0.0=閉 ~ 1.0=開)
        CarConfig carCfg = CarConfigManager.getConfig(animatable.getVariantId());
        float maxAngleDeg = (carCfg != null && carCfg.properties != null) ? carCfg.properties.retractableAngle : 22.0f;
        float openProgress = animatable.getHeadlightProgress(animationState.getPartialTick());
        float popupAngle = openProgress * (maxAngleDeg * Mth.DEG_TO_RAD);
        GeoBone retractL = this.getAnimationProcessor().getBone("retractable_l");
        GeoBone retractR = this.getAnimationProcessor().getBone("retractable_r");
        GeoBone retractBoth = this.getAnimationProcessor().getBone("retractable_lights");
        GeoBone retractSingle = this.getAnimationProcessor().getBone("retractable");

        if (retractL != null) retractL.setRotX(popupAngle);
        if (retractR != null) retractR.setRotX(popupAngle);
        if (retractBoth != null) retractBoth.setRotX(popupAngle);
        if (retractSingle != null) retractSingle.setRotX(popupAngle);
    }

    /**
     * タイヤの転がり回転を適用
     * モデルのボーン設計（Z軸90度横倒しモデリングか、直立モデリングか）を初期Z回転角から自動判別
     */
    private void applyWheelRoll(GeoBone wheelBone, float wheelRad, boolean isRightSide) {
        if (wheelBone == null) return;

        float initialRotZ = (wheelBone.getInitialSnapshot() != null)
                ? wheelBone.getInitialSnapshot().getRotZ()
                : wheelBone.getRotZ();

        boolean isLyingOnZ = Math.abs(initialRotZ) > (45.0f * Mth.DEG_TO_RAD);

        if (isLyingOnZ) {
            // Z軸に90度倒して作られている場合 (バギーモデルなど): 転がり軸はローカル Y 軸
            wheelBone.setRotX(0.0f);
            wheelBone.setRotY(isRightSide ? wheelRad : -wheelRad);
        } else {
            // 直立状態で作られている場合 (86モデルなど): 転がり軸はローカル X 軸
            // 右輪 (-X側) は +X回転で前進、左輪 (+X側) は -X回転で前進
            wheelBone.setRotY(0.0f);
            wheelBone.setRotX(isRightSide ? wheelRad : -wheelRad);
        }
    }
}

