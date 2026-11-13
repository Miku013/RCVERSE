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
        // モンスタートラックなどの大ストローク車両 (ダブルウィッシュボーン搭載車) では可動域を拡大
        // ※ Blockbench空間は 1 block = 16 units なので、実測スケール 16.0f にて厳密一致
        boolean isTruck = "truck".equalsIgnoreCase(animatable.getVariantId());
        float suspScale = isTruck ? 16.0f : 6.0f;
        float suspFL = animatable.getSuspFL() * suspScale;
        float suspFR = animatable.getSuspFR() * suspScale;
        float suspRL = animatable.getSuspRL() * suspScale;
        float suspRR = animatable.getSuspRR() * suspScale;

        // 車体（ボディ・アッパー部）の着地バウンス振動 (ドスンと沈み込んでフワッと戻る)
        // ※ タイヤ(steer/wheel)は接地しているため、ボディ側を下げることでサスペンションが深く沈み込む
        float bounceScale = 16.0f;
        float bounceY = animatable.getChassisBounce() * bounceScale;
        GeoBone bodyBone = this.getAnimationProcessor().getBone("body");
        GeoBone glassBone = this.getAnimationProcessor().getBone("Glass");
        GeoBone lightBone = this.getAnimationProcessor().getBone("light");
        if (bodyBone != null) {
            bodyBone.setPosY(bounceY);
        }
        if (glassBone != null) {
            glassBone.setPosY(bounceY);
        }
        if (lightBone != null) {
            lightBone.setPosY(bounceY);
        }

        if (steerFL != null) steerFL.setPosY(suspFL);
        if (steerFR != null) steerFR.setPosY(suspFR);
        if (wheelRL != null) wheelRL.setPosY(suspRL);
        if (wheelRR != null) wheelRR.setPosY(suspRR);

        // 3. ダブルウィッシュボーンサスペンションの視覚的連動アニメーション
        // (アッパー/ロアアームのスイング、ショックアブソーバーの伸縮・傾き、バンプキャンバー)
        applyDoubleWishbone(animatable, suspFL, suspFR, suspRL, suspRR);

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
            // クランクの回転（車輪と1:1でテンポ良く回転）
            float crankRad = wheelRad * 1.0f;
            crank.setRotX(initCrankX - crankRad);

            // 車両コンフィグからの足の動き倍率
            CarConfig currentCarCfg = CarConfigManager.getConfig(animatable.getVariantId());
            float swingMult = (currentCarCfg != null && currentCarCfg.properties != null) ? currentCarCfg.properties.pedalSwingMultiplier : 1.0f;

            // 村人の足（太もも・すね）のダイナミック上下ペダリング (14度 -> 30度へ大幅拡大)
            float pedalSwing = Mth.sin(crankRad) * (30.0f * Mth.DEG_TO_RAD) * swingMult;
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
                // 膝の曲げ伸ばしを深く連動
                shinR.setRotX(init - pedalSwing * 0.85f);
            }
            if (shinL != null) {
                float init = (shinL.getInitialSnapshot() != null) ? shinL.getInitialSnapshot().getRotX() : shinL.getRotX();
                shinL.setRotX(init + pedalSwing * 0.85f);
            }

            // 一生懸命漕ぐ村人の上半身・頭のボビング＆体重移動アクション
            GeoBone vBody = this.getAnimationProcessor().getBone("villager_body");
            GeoBone vHead = this.getAnimationProcessor().getBone("villager_head");
            if (vBody != null) {
                float initZ = (vBody.getInitialSnapshot() != null) ? vBody.getInitialSnapshot().getRotZ() : vBody.getRotZ();
                float initX = (vBody.getInitialSnapshot() != null) ? vBody.getInitialSnapshot().getRotX() : vBody.getRotX();
                // 踏み込み時の左右への体重移動ロール
                vBody.setRotZ(initZ + Mth.sin(crankRad) * (3.8f * Mth.DEG_TO_RAD) * swingMult);
                // 踏み込み時のグッと前に沈み込むピッチ運動
                vBody.setRotX(initX + Math.abs(Mth.sin(crankRad)) * (3.2f * Mth.DEG_TO_RAD) * swingMult);
            }
            if (vHead != null) {
                float initX = (vHead.getInitialSnapshot() != null) ? vHead.getInitialSnapshot().getRotX() : vHead.getRotX();
                // 体の動きに合わせた頭の上下ボビング
                vHead.setRotX(initX - Math.abs(Mth.sin(crankRad)) * (2.8f * Mth.DEG_TO_RAD) * swingMult);
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

    /**
     * ダブルウィッシュボーンサスペンションのボーン連動アニメーション
     * @param suspFL フロント左サスペンション変位 (ブロックベンチ単位)
     * @param suspFR フロント右サスペンション変位
     * @param suspRL リア左サスペンション変位
     * @param suspRR リア右サスペンション変位
     */
    private void applyDoubleWishbone(RcCarEntity animatable, float suspFL, float suspFR, float suspRL, float suspRR) {
        // FL (フロント左)
        applyCornerWishbone(animatable, "fl", suspFL, false);
        // FR (フロント右)
        applyCornerWishbone(animatable, "fr", suspFR, true);
        // RL (リア左)
        applyCornerWishbone(animatable, "rl", suspRL, false);
        // RR (リア右)
        applyCornerWishbone(animatable, "rr", suspRR, true);
    }

    /**
     * 1輪ごとのダブルウィッシュボーン連動
     * @param suffix "fl", "fr", "rl", "rr"
     * @param suspY サスペンション変位 (上方向が正、下方向が負)
     * @param isRight 右輪かどうか
     */
    private void applyCornerWishbone(RcCarEntity animatable, String suffix, float suspY, boolean isRight) {
        GeoBone armLower = this.getAnimationProcessor().getBone("arm_lower_" + suffix);
        GeoBone armUpper = this.getAnimationProcessor().getBone("arm_upper_" + suffix);
        GeoBone shockBody = this.getAnimationProcessor().getBone("shock_body_" + suffix);
        GeoBone shockRod = this.getAnimationProcessor().getBone("shock_rod_" + suffix);
        GeoBone knuckle = this.getAnimationProcessor().getBone("knuckle_" + suffix);

        // このボーン群が存在しないモデルではスキップ
        if (armLower == null && armUpper == null && shockBody == null) {
            return;
        }

        // アーム実効長 (truck.geo.json実測値: ピボット間 10.01 ブロックベンチ単位)
        float armLength = 10.0f;
        // サスペンション上下動によるアームスイング角 (ラジアン)
        // 上に沈むと (suspY > 0)、左輪(+X)は反時計回り(Z軸プラス)、右輪(-X)は時計回り(Z軸マイナス)
        // 最大変位 +4.5 unit のとき約 +26.7°、-3.5 unit のとき約 -20.5°
        float clampedRatio = Mth.clamp(suspY / armLength, -0.35f, 0.45f);
        float swingAngle = (float) Math.asin(clampedRatio);
        float armRotZ = isRight ? -swingAngle : swingAngle;

        // 1. ロアアームのスイング
        if (armLower != null) {
            float initZ = (armLower.getInitialSnapshot() != null) ? armLower.getInitialSnapshot().getRotZ() : armLower.getRotZ();
            armLower.setRotZ(initZ + armRotZ);
        }

        // 2. アッパーアームのスイング (ジオメトリ特性でロアアームより短いため 1.25倍 スイング)
        if (armUpper != null) {
            float initZ = (armUpper.getInitialSnapshot() != null) ? armUpper.getInitialSnapshot().getRotZ() : armUpper.getRotZ();
            armUpper.setRotZ(initZ + armRotZ * 1.25f);
        }

        // 3. ショックアブソーバーの傾きとピストン伸縮
        if (shockBody != null) {
            float initZ = (shockBody.getInitialSnapshot() != null) ? shockBody.getInitialSnapshot().getRotZ() : shockBody.getRotZ();
            float shockSwing = armRotZ * 0.50f;
            shockBody.setRotZ(initZ + shockSwing);
        }

        if (shockRod != null) {
            // ショックロッドのピストン伸縮 (実測幾何学比率: サス変位の約95%がシリンダー内に吸い込まれる)
            shockRod.setPosY(-suspY * 0.95f);
        }

        // 4. ナックル / キャンバー角 (バンプキャンバー: 縮むとネガティブキャンバー)
        if (knuckle != null) {
            float initZ = (knuckle.getInitialSnapshot() != null) ? knuckle.getInitialSnapshot().getRotZ() : knuckle.getRotZ();
            float bumpCamber = armRotZ * 0.25f;
            knuckle.setRotZ(initZ + bumpCamber);
        }
    }
}

