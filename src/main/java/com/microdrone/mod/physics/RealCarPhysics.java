package com.microdrone.mod.physics;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/**
 * RCカー REALモード専用物理演算エンジン
 *
 * 【第3段階：可変グリップ（前後輪独立グリップ限界・スリップ角・Yawモーメント制御）】
 * - 前輪・後輪それぞれのスリップ角と横力を計算し、
 *   最大横力（グリップ係数 × 荷重 × 路面摩擦）による限界クリップを導入。
 * - 前輪と後輪の横力差モーメントで YawRate（旋回角速度）を発生させ、
 *   オーバーステア（自然なドリフト開始）、アンダーステア、カウンターステア、
 *   グリップ回復による安定復帰をすべて物理演算から自然に発生させる。
 * - 低速域はスピン・数値振動を防ぐため幾何学ステアへ滑らかにブレンドし、
 *   1tickを4サブステップ分割して 20Hz 環境でも破綻しない安定性を確保。
 */
public class RealCarPhysics {

    public record StepResult(
            Vec3 worldVelocity,
            float newYaw,
            float newSteerAngle,
            float forwardSpeed,
            float lateralSpeed,
            float yawRateDeg,
            float slipAngleDeg,
            boolean drifting
    ) {}

    private final RealPhysicsConfig config = new RealPhysicsConfig();

    // 速度・姿勢状態
    private double forwardVelocity = 0.0; // 車体前方(+)/後方(-)の速度 (blocks/tick)
    private double lateralVelocity = 0.0; // 車体右(+)/左(-)の横滑り速度 (blocks/tick)
    private float yawRateDeg = 0.0f;      // 旋回速度 (deg/tick, 右が正/左が負)
    private float steerAngleDeg = 0.0f;   // 現在の前輪操舵角 (deg, 左Aが正/右Dが負)
    private float slipAngleDeg = 0.0f;    // 車体中心のスリップ角 (deg)
    private boolean drifting = false;
    private boolean counterSteering = false;
    private int continuousDriftTicks = 0; // ドリフト継続tick数 (ロングドリフト時の減速・グリップ回復用)
    private int recentSlideTicks = 0; // 直近まで横滑り/ドリフトしていた余韻カウント (立ち上がりおつり抑止用)
    private double smoothedPowerSpin = 0.0; // アクセル瞬間抜き時の急激なリアグリップ復帰を防ぐ滑らか保持値
    private float prevInputForward = 0.0f; // 直前tickのアクセル入力（踏み直し・チョッピング検出用）

    // 第3段階：前後グリップ・スリップ・デバッグ監視用パラメーター
    private float frontGrip = 1.12f;
    private float rearGrip = 1.02f;
    private double frontGripLimit = 0.0;
    private double rearGripLimit = 0.0;
    private double lateralForceFront = 0.0;
    private double lateralForceRear = 0.0;
    private float frontSlipAngleDeg = 0.0f;
    private float rearSlipAngleDeg = 0.0f;
    private float frontGripUsage = 0.0f;  // 0.0〜1.0+ (1.0以上で限界到達)
    private float rearGripUsage = 0.0f;
    private boolean frontSlipping = false;
    private boolean rearSlipping = false;

    // 第4・第5段階：荷重移動システム ＆ 路面摩擦係数
    private final WeightTransferSystem weightTransfer = new WeightTransferSystem();
    private float frontLoadRatio = 0.50f;
    private float rearLoadRatio = 0.50f;
    private float surfaceFriction = 1.0f;

    // 装着タイヤ特性倍率
    private float tireGripMultiplier = 1.0f;
    private float tireScrubMultiplier = 1.0f;
    private boolean tireIceImmunity = false;
    private boolean tireOffroadImmunity = false;

    public void setTireProperties(float gripMult, float scrubMult, boolean iceImmunity, boolean offroadImmunity) {
        setTireProperties(gripMult, gripMult, scrubMult, iceImmunity, offroadImmunity);
    }

    public void setTireProperties(float frontGripMult, float rearGripMult, float scrubMult, boolean iceImmunity, boolean offroadImmunity) {
        this.tireGripMultiplier = (frontGripMult + rearGripMult) * 0.5f;
        this.tireScrubMultiplier = scrubMult;
        this.tireIceImmunity = iceImmunity;
        this.tireOffroadImmunity = offroadImmunity;
        this.frontGrip = 1.12f * frontGripMult;
        this.rearGrip = 1.02f * rearGripMult;
    }

    public void setSurfaceFriction(float friction) {
        if (this.tireIceImmunity && friction < 0.65f) {
            this.surfaceFriction = 1.0f; // 氷上でもスパイクピンが食い付く
        } else if (this.tireOffroadImmunity && friction < 0.85f && friction >= 0.65f) {
            this.surfaceFriction = 1.0f; // ダートでもブロックパターンがグリップ
        } else {
            this.surfaceFriction = friction;
        }
    }

    // 一時テスト用テレメトリー記録状態
    private double lastRearDynMult = 1.0;
    private double lastGyroSteerDeg = 0.0;
    private float lastStepYawDeg = 0.0f;
    private int telemetryTickCounter = 0;
    private static final Object TELEMETRY_LOCK = new Object();
    private static BufferedWriter telemetryWriter = null;
    private static boolean telemetryFileInitialized = false;

    public WeightTransferSystem getWeightTransfer() {
        return this.weightTransfer;
    }

    public RealPhysicsConfig getConfig() {
        return this.config;
    }

    public double getForwardVelocity() {
        return this.forwardVelocity;
    }

    public double getLateralVelocity() {
        return this.lateralVelocity;
    }

    public float getYawRateDeg() {
        return this.yawRateDeg;
    }

    public float getSteerAngleDeg() {
        return this.steerAngleDeg;
    }

    public float getSlipAngleDeg() {
        return this.slipAngleDeg;
    }

    public float getFrontGrip() {
        return this.frontGrip;
    }

    public float getRearGrip() {
        return this.rearGrip;
    }

    public double getFrontGripLimit() {
        return this.frontGripLimit;
    }

    public double getRearGripLimit() {
        return this.rearGripLimit;
    }

    public double getLateralForceFront() {
        return this.lateralForceFront;
    }

    public double getLateralForceRear() {
        return this.lateralForceRear;
    }

    public float getFrontSlipAngleDeg() {
        return this.frontSlipAngleDeg;
    }

    public float getRearSlipAngleDeg() {
        return this.rearSlipAngleDeg;
    }

    public float getFrontGripUsage() {
        return this.frontGripUsage;
    }

    public float getRearGripUsage() {
        return this.rearGripUsage;
    }

    public boolean isFrontSlipping() {
        return this.frontSlipping;
    }

    public boolean isRearSlipping() {
        return this.rearSlipping;
    }

    public float getSurfaceFriction() {
        return this.surfaceFriction;
    }

    public boolean isDrifting() {
        return this.drifting;
    }

    public boolean isCounterSteering() {
        return this.counterSteering;
    }

    public float getCounterMaxAngleDeg() {
        return this.config.maxCounterSteerAngleDeg;
    }

    public void setCounterMaxAngleDeg(float angleDeg) {
        this.config.maxCounterSteerAngleDeg = Mth.clamp(angleDeg, 35.0f, 65.0f);
    }

    public float getDriftStartAngleDeg() {
        return this.config.driftStartAngleDeg;
    }

    public void setDriftStartAngleDeg(float angleDeg) {
        this.config.driftStartAngleDeg = Mth.clamp(angleDeg, 10.0f, 35.0f);
    }

    public float getFrontLoadRatio() {
        return this.frontLoadRatio;
    }

    public float getRearLoadRatio() {
        return this.rearLoadRatio;
    }

    /**
     * ワールド速度と車体Yawから、車体ローカル速度（前後/左右）へ同期する。
     */
    public void syncFromWorldVelocity(Vec3 worldVel, float yawDeg, float currentSteerDeg) {
        float yawRad = yawDeg * Mth.DEG_TO_RAD;
        double fwdX = -Mth.sin(yawRad);
        double fwdZ = Mth.cos(yawRad);
        double rightX = -fwdZ;
        double rightZ = fwdX;

        this.forwardVelocity = worldVel.x * fwdX + worldVel.z * fwdZ;
        this.lateralVelocity = worldVel.x * rightX + worldVel.z * rightZ;
        this.steerAngleDeg = currentSteerDeg;
        this.yawRateDeg = 0.0f;
        this.recentSlideTicks = 0;
        this.continuousDriftTicks = 0;
        this.smoothedPowerSpin = 0.0;
        updateSlipAndDriftFlags();
    }

    public void reset() {
        this.forwardVelocity = 0.0;
        this.lateralVelocity = 0.0;
        this.yawRateDeg = 0.0f;
        this.steerAngleDeg = 0.0f;
        this.slipAngleDeg = 0.0f;
        this.drifting = false;
        this.counterSteering = false;
        this.continuousDriftTicks = 0;
        this.recentSlideTicks = 0;
        this.smoothedPowerSpin = 0.0;
        this.prevInputForward = 0.0f;
        this.frontSlipping = false;
        this.rearSlipping = false;
        this.frontGripUsage = 0.0f;
        this.rearGripUsage = 0.0f;
        this.weightTransfer.reset();
        this.frontLoadRatio = 0.50f;
        this.rearLoadRatio = 0.50f;
        this.lastRearDynMult = 1.0;
        this.lastGyroSteerDeg = 0.0;
        flushTelemetry();
    }

    /**
     * REALモードの1tick分の水平物理演算を実行する（第4段階：可変グリップ＋荷重移動方式）。
     */
    public StepResult stepHorizontal(
            Vec3 prevWorldVel,
            float currentYawDeg,
            float inputForward,
            float inputSteer,
            boolean inputHandbrake,
            boolean grounded,
            boolean wallAhead,
            double maxSpeed,
            double accel,
            float turnMult,
            boolean invertSteer
    ) {
        // 1. 前tickの実ワールド速度（壁衝突やジャンプ射出を反映済み）を現在Yawのローカル速度へ射影
        float yawRad = currentYawDeg * Mth.DEG_TO_RAD;
        double prevFwdX = -Mth.sin(yawRad);
        double prevFwdZ = Mth.cos(yawRad);
        double prevRightX = -prevFwdZ;
        double prevRightZ = prevFwdX;

        this.forwardVelocity = prevWorldVel.x * prevFwdX + prevWorldVel.z * prevFwdZ;
        this.lateralVelocity = prevWorldVel.x * prevRightX + prevWorldVel.z * prevRightZ;

        // 2. 前輪操舵角の更新（カウンター方向入力時は設定された最大カウンター角まで切れ角を拡大し、応答を調整）
        float steerDir = invertSteer ? -1.0f : 1.0f;
        float effectiveInput = inputSteer * steerDir;
        boolean hasLateralSlip = Math.abs(this.lateralVelocity) > 0.012 && this.forwardVelocity > 0.02;
        boolean steeringIntoCounter = hasLateralSlip && (effectiveInput * this.lateralVelocity < -0.001);
        boolean currentlyCountered = hasLateralSlip && (this.steerAngleDeg * this.lateralVelocity < -0.5f);
        this.counterSteering = steeringIntoCounter || currentlyCountered;

        float maxAngle = steeringIntoCounter ? config.maxCounterSteerAngleDeg : config.maxSteeringAngleDeg;
        float lerpRate;
        if (Math.abs(inputSteer) < 0.05f) {
            boolean overshotSteerExit = (this.recentSlideTicks > 0 || currentlyCountered)
                    && Math.abs(this.lateralVelocity) < 0.09
                    && Math.abs(this.steerAngleDeg) > Math.abs(this.slipAngleDeg) + 8.0f;
            if (overshotSteerExit) {
                lerpRate = config.exitFastReturnRate;
            } else {
                lerpRate = currentlyCountered ? config.counterReturnRate : config.steeringReturnRate;
            }
        } else if (steeringIntoCounter) {
            lerpRate = config.counterSteerLerpRate;
        } else {
            lerpRate = config.steeringLerpRate;
        }
        float targetSteerDeg = inputSteer * maxAngle * steerDir;
        this.steerAngleDeg = Mth.lerp(lerpRate, this.steerAngleDeg, targetSteerDeg);

        // 3. 縦方向（アクセル・ブレーキ・転がり抵抗）と縦加速度の計測
        double preForwardVel = this.forwardVelocity;
        double effectiveAccel = (wallAhead && inputForward > 0.05f) ? accel * 0.08 : accel;
        if (wallAhead && inputForward > 0.05f) {
            this.forwardVelocity = Math.min(this.forwardVelocity, 0.015);
        }

        double powerSpinSeverity = 0.0;
        if (grounded) {
            if (inputHandbrake) {
                // スペースキー: REALモード専用フットブレーキ（強い減速・0で停止し後退に入らない）
                double brakeForce = effectiveAccel * config.spaceBrakeDecelMultiplier;
                if (this.forwardVelocity > 0.001) {
                    this.forwardVelocity = Math.max(0.0, this.forwardVelocity - brakeForce);
                } else if (this.forwardVelocity < -0.001) {
                    this.forwardVelocity = Math.min(0.0, this.forwardVelocity + brakeForce);
                } else {
                    this.forwardVelocity = 0.0;
                    this.lateralVelocity *= 0.65; // 完全停止間際は横ズレも収束
                }
            } else if (inputForward > 0.05f) {
                if (this.forwardVelocity < -0.01) {
                    this.forwardVelocity += effectiveAccel * config.brakeDecelMultiplier;
                    if (this.forwardVelocity > 0.0) {
                        this.forwardVelocity = 0.0;
                    }
                } else {
                    // 高回転アクセル入力ほど駆動輪（後輪）の縦力消費が増え、パワードリフトのきっかけになる
                    double fwdGain = effectiveAccel * inputForward;
                    this.forwardVelocity += fwdGain;
                    powerSpinSeverity = Mth.clamp(inputForward, 0.0f, 1.0f);

                    // ★ ドリフト中のアクセル踏み直し（チョッピング / パワースライド再点火）
                    boolean throttleKicked = (inputForward > 0.15f && this.prevInputForward <= 0.05f);
                    if (throttleKicked && (this.drifting || this.continuousDriftTicks > 5) && this.forwardVelocity > 0.04) {
                        this.forwardVelocity += effectiveAccel * 1.5; // 前方へグッと蹴り出すトラクション
                        this.smoothedPowerSpin = 1.0; // 後輪を再スピンさせてテールを押し出す
                        if (this.continuousDriftTicks > 12) {
                            this.continuousDriftTicks = Math.max(8, this.continuousDriftTicks - 14); // 垂れを巻き戻し
                        }
                    }
                }
            } else if (inputForward < -0.05f) {
                if (this.forwardVelocity > 0.01) {
                    this.forwardVelocity -= effectiveAccel * config.brakeDecelMultiplier;
                    if (this.forwardVelocity < 0.0) {
                        this.forwardVelocity = 0.0;
                    }
                } else {
                    this.forwardVelocity += effectiveAccel * inputForward * config.reversePowerMultiplier;
                }
            } else {
                this.forwardVelocity *= config.rollingFriction;
            }
        } else {
            this.forwardVelocity *= 0.995;
            this.lateralVelocity *= 0.995;
        }

        if (this.forwardVelocity > maxSpeed) {
            this.forwardVelocity = Mth.lerp(0.28, this.forwardVelocity, maxSpeed);
        } else if (this.forwardVelocity < -maxSpeed * 0.45) {
            this.forwardVelocity = -maxSpeed * 0.45;
        }

        // 瞬間的なアクセル抜きでリアグリップが1tickで1.0へ跳ね戻らないよう滑らかに維持・減衰
        double spinTarget = powerSpinSeverity;
        double spinRate = (spinTarget >= this.smoothedPowerSpin)
                ? 0.55
                : (inputHandbrake || inputForward < -0.05f ? 0.45 : 0.20);
        this.smoothedPowerSpin = Mth.lerp(spinRate, this.smoothedPowerSpin, spinTarget);

        // 第4段階：縦加減速Gと横Gに基づく荷重移動の更新（前後・左右・4輪個別データ）
        // ※ maxSpeed超過リミットの減衰がフルアクセル中に「急ブレーキ荷重」として前輪へドカ乗りするのを防止
        // ※ また単なるアクセルオフ減速時は過剰な前傾荷重(ノーズダイブ→逆振りおつり)を抑え、意図的ブレーキ時のみ強めに前へ乗せる
        boolean isActivelyBraking = inputHandbrake || (inputForward < -0.05f && preForwardVel > 0.01);
        double longitudinalAccel = (grounded && !wallAhead) ? (this.forwardVelocity - preForwardVel) : 0.0;
        if (inputForward > 0.05f && !inputHandbrake && longitudinalAccel < 0.0) {
            longitudinalAccel = 0.0;
        } else if (!isActivelyBraking && longitudinalAccel < 0.0) {
            longitudinalAccel *= 0.35;
        }
        double prevTotalLatForce = this.lateralForceFront + this.lateralForceRear;
        this.weightTransfer.update(longitudinalAccel, prevTotalLatForce, grounded, this.config);
        this.frontLoadRatio = this.weightTransfer.getFrontEffectiveLoad();
        this.rearLoadRatio = this.weightTransfer.getRearEffectiveLoad();

        // 4. 可変グリップ・バイシクルモデル（4サブステップ積分で20Hz環境でも安定）
        float newYawDeg = currentYawDeg;
        if (grounded) {
            newYawDeg = stepBicycleTirePhysics(
                    currentYawDeg,
                    inputForward,
                    inputSteer,
                    inputHandbrake,
                    maxSpeed,
                    turnMult,
                    this.smoothedPowerSpin
            );
        } else {
            // 空中は横力・タイヤ旋回が発生せず、慣性を保つ
            this.yawRateDeg = Mth.lerp(0.15f, this.yawRateDeg, 0.0f);
            this.frontGripUsage = 0.0f;
            this.rearGripUsage = 0.0f;
            this.frontSlipping = false;
            this.rearSlipping = false;
            this.lateralForceFront = 0.0;
            this.lateralForceRear = 0.0;
        }

        // 低速時の微小ドリフト残存による不安定を防止
        if (grounded) {
            if (Math.abs(this.forwardVelocity) < config.lowSpeedSnapThreshold && Math.abs(inputForward) < 0.05f) {
                this.forwardVelocity = 0.0;
            }
            if (Math.abs(this.lateralVelocity) < config.lowSpeedSnapThreshold * 0.5 && Math.abs(this.steerAngleDeg) < 1.0f) {
                this.lateralVelocity = 0.0;
            }
        }

        // 発散防止の安全クランプ
        this.forwardVelocity = Mth.clamp(this.forwardVelocity, -maxSpeed * 0.6, maxSpeed * 1.15);
        this.lateralVelocity = Mth.clamp(this.lateralVelocity, -maxSpeed * 1.15, maxSpeed * 1.15);

        updateSlipAndDriftFlags();
        if (this.drifting) {
            this.continuousDriftTicks++;
        } else {
            this.continuousDriftTicks = 0;
        }
        this.lastStepYawDeg = newYawDeg;
        if (this.drifting || this.counterSteering || Math.abs(this.lateralVelocity) > config.exitDampingLatThreshold) {
            this.recentSlideTicks = 8;
        } else if (this.recentSlideTicks > 0) {
            this.recentSlideTicks--;
        }

        // 5. 最終Yawのローカル速度からワールド速度ベクトルへ再合成
        float outYawRad = newYawDeg * Mth.DEG_TO_RAD;
        double outFwdX = -Mth.sin(outYawRad);
        double outFwdZ = Mth.cos(outYawRad);
        double outRightX = -outFwdZ;
        double outRightZ = outFwdX;

        Vec3 outWorldVel = new Vec3(
                outFwdX * this.forwardVelocity + outRightX * this.lateralVelocity,
                0.0,
                outFwdZ * this.forwardVelocity + outRightZ * this.lateralVelocity
        );

        this.prevInputForward = inputForward;

        return new StepResult(
                outWorldVel,
                newYawDeg,
                this.steerAngleDeg,
                (float) this.forwardVelocity,
                (float) this.lateralVelocity,
                this.yawRateDeg,
                this.slipAngleDeg,
                this.drifting
        );
    }

    /**
     * 前後2軸（バイシクルモデル）に基づく可変グリップ＆YawRate旋回計算
     */
    private float stepBicycleTirePhysics(
            float startYawDeg,
            float inputForward,
            float inputSteer,
            boolean inputHandbrake,
            double maxSpeed,
            float turnMult,
            double powerSpinSeverity
    ) {
        this.frontGrip = config.baseFrontGrip;
        this.rearGrip = config.baseRearGrip;

        double lf = config.wheelBase * (1.0 - config.frontAxleRatio);
        double lr = config.wheelBase * config.frontAxleRatio;

        // 速度が中高速になるほど大舵角時の最大実効切れ角をわずかに抑える（ただしカウンターステア時はフル切れ角を解放！）
        double totalSpeed = Math.hypot(this.forwardVelocity, this.lateralVelocity);
        double speedRatio = Mth.clamp(totalSpeed / Math.max(0.15, maxSpeed), 0.0, 1.0);
        double highSpeedSteerScale = this.counterSteering ? 1.0 : (1.0 - speedRatio * 0.26);
        double steerRad = Math.toRadians(this.steerAngleDeg) * config.steeringSensitivity * turnMult * highSpeedSteerScale;

        // カウンターステアの角度に応じたフロント食いつき（バイト）補正
        double counterAngleRatio = this.counterSteering
                ? Mth.clamp(Math.abs(this.steerAngleDeg) / 30.0f, 0.0f, 1.0f)
                : 0.0;
        double frontBiteGrip = Mth.lerp((float) counterAngleRatio, 1.0f, (float) config.counterFrontBiteGripMult);
        double frontBiteStiff = Mth.lerp((float) counterAngleRatio, 1.0f, (float) config.counterFrontBiteStiffMult);

        // グリップ限界計算：最大横力 = グリップ係数 × 荷重配分(×2) × 路面摩擦 × 基準横G
        double baseLimitScale = this.surfaceFriction * config.maxLateralAccelPerTick;
        this.frontGripLimit = Math.max(0.005, this.frontGrip * (this.frontLoadRatio * 2.0) * baseLimitScale * frontBiteGrip);

        double rearDynMult = 1.0;
        if (inputHandbrake && totalSpeed > 0.03) {
            // 専用ブレーキ時は強すぎない制動分(0.88)のみ反映し、直進安定と荷重移動によるドリフト進入を両立
            rearDynMult *= config.brakeRearGripRetention;
        }
        // 滑り始め角度 (driftStartAngleDeg) を超えた分だけ段階的に後輪の横グリップ低下を発動
        if (powerSpinSeverity > 0.1 && totalSpeed > 0.04) {
            float absSteer = Math.abs(this.steerAngleDeg);
            float startDeg = this.config.driftStartAngleDeg;
            float slideTriggerRatio = 0.0f;
            if (!this.counterSteering && absSteer > startDeg) {
                // イン側への切り込み超過分から +6° かけてリアのパワースライドを発動（大舵角時は素直にテールを出す）
                slideTriggerRatio = Mth.clamp((absSteer - startDeg) / 6.0f, 0.0f, 1.0f);
            } else {
                // カウンター中およびステア戻し中は、現在のスリップ角に応じて段階的に後輪グリップを回復させる
                float absSlip = Math.abs(this.slipAngleDeg);
                if (absSlip > 3.5f && Math.abs(this.lateralVelocity) > 0.012) {
                    float maxRatio = this.counterSteering ? 0.78f : 0.75f;
                    slideTriggerRatio = Mth.clamp((absSlip - 3.5f) / 16.5f, 0.0f, 1.0f) * maxRatio;
                }
            }
            if (slideTriggerRatio > 0.0f) {
                double effectiveSpin = powerSpinSeverity * slideTriggerRatio;
                rearDynMult *= Mth.lerp((float) effectiveSpin, 1.0f, (float) config.powerOversteerGripMult);
            }
        }

        // ドリフト継続時間・距離に応じた後輪グリップ回復 (Grip Recovery - マイルド調整版)
        // ドリフトが長引くとタイヤの動摩擦低下が適度に収まり、マイルドにグリップを取り戻して横滑りを収束
        if (this.continuousDriftTicks > 22) {
            float gripRecovery = Mth.clamp((this.continuousDriftTicks - 22) / 38.0f, 0.0f, 0.85f);
            rearDynMult = Mth.lerp(gripRecovery, rearDynMult, 1.0);
        }

        this.lastRearDynMult = rearDynMult;
        this.rearGripLimit = Math.max(0.004, this.rearGrip * (this.rearLoadRatio * 2.0) * baseLimitScale * rearDynMult);

        int subSteps = Math.max(1, config.physicsSubSteps);
        double subDt = 1.0 / subSteps;
        boolean steeringNeutral = Math.abs(inputSteer) < 0.05f;
        boolean hadRecentSlide = this.drifting || this.counterSteering || this.recentSlideTicks > 0;

        double yawRateRad = Math.toRadians(this.yawRateDeg); // rad/tick
        float currentYaw = startYawDeg;

        double lastFyF = 0.0;
        double lastFyR = 0.0;
        double lastAlphaF = 0.0;
        double lastAlphaR = 0.0;
        double lastGyroDeg = 0.0;
        boolean anyFrontSlip = false;
        boolean anyRearSlip = false;

        for (int i = 0; i < subSteps; i++) {
            double subStartLat = this.lateralVelocity;
            double vAbs = Math.hypot(this.forwardVelocity, this.lateralVelocity);
            if (vAbs < 0.003) {
                this.lateralVelocity *= Math.pow(0.5, subDt);
                yawRateRad *= Math.pow(0.5, subDt);
                continue;
            }

            // 低速領域ではタイヤモデルの発散を防ぎつつ、幾何学旋回からタイヤ物理へ滑らかにフェード
            double tireBlend = Mth.clamp((vAbs - 0.008) / Math.max(0.01, config.lowSpeedGeomThreshold), 0.0, 1.0);

            double denom = Math.max(Math.abs(this.forwardVelocity), 0.065);
            double fwdSign = this.forwardVelocity >= -0.001 ? 1.0 : -1.0;

            // ② RCドリフト用ステアジャイロ／立ち上がりダンピング（横速度がゼロ付近へ収束した瞬間の過剰Yawをおつり抑止）
            double latThresh = Math.max(0.01, config.exitDampingLatThreshold);
            double exitProximity = 1.0 - Mth.clamp(Math.abs(this.lateralVelocity) / latThresh, 0.0, 1.0);
            double gyroSteerRad = 0.0;
            if (config.enableExitGyro && steeringNeutral && tireBlend > 0.1) {
                double maxGyroRad = Math.toRadians(14.0);
                gyroSteerRad = Mth.clamp(
                        fwdSign * yawRateRad * config.gyroCounterAssistGain * tireBlend * (0.45 + 0.55 * exitProximity),
                        -maxGyroRad,
                        maxGyroRad
                );
            }
            double effectiveSteerRad = steerRad + gyroSteerRad;

            // 前軸・後軸の横方向接地速度 (右方向が正)
            double vyFront = this.lateralVelocity + lf * yawRateRad;
            double vyRear = this.lateralVelocity - lr * yawRateRad;

            // スリップ角 (rad)
            double alphaF = Math.atan2(vyFront, denom) + (fwdSign * effectiveSteerRad);
            double alphaR = Math.atan2(vyRear, denom);

            // グリップ限界前の生横力
            double rawFyF = -config.corneringStiffnessFront * frontBiteStiff * (this.frontLoadRatio * 2.0) * this.surfaceFriction * alphaF;
            double rawFyR = -config.corneringStiffnessRear * (this.rearLoadRatio * 2.0) * this.surfaceFriction * alphaR;

            // 限界超過判定＆グリップ限界によるクランプ（前輪はカウンター時粘り強く、後輪は動摩擦へ落とす）
            boolean fSlip = Math.abs(rawFyF) > this.frontGripLimit;
            boolean rSlip = Math.abs(rawFyR) > this.rearGripLimit;
            anyFrontSlip |= fSlip;
            anyRearSlip |= rSlip;

            double fyF = clampWithGripLimit(rawFyF, this.frontGripLimit, config.frontSlipPeakDropRatio);
            double fyR = clampWithGripLimit(rawFyR, this.rearGripLimit, config.slipLimitPeakDropRatio);

            // 低速域用の幾何学的ターゲット角速度 (微速旋回のみ補助、中〜高速は完全タイヤ物理)
            double geomOmegaRad = -(this.forwardVelocity / Math.max(0.20, config.wheelBase))
                    * Math.tan(steerRad) * 0.42;

            // 全体横力とヨーモーメントの適用
            double totalFy = fyF + fyR;
            double yawTorque = (lf * fyF) - (lr * fyR);

            this.lateralVelocity += totalFy * tireBlend * subDt;

            double dynYawRateRad = yawRateRad + (yawTorque * config.yawTorqueGain) * tireBlend * subDt;
            double effectiveYawDamping = config.yawAngularDamping;
            if (config.enableExitGyro && vAbs > 0.04) {
                if (steeringNeutral) {
                    double dampingWeight = Math.max(exitProximity, hadRecentSlide ? 0.55 : 0.15);
                    effectiveYawDamping = Mth.lerp(dampingWeight, config.yawAngularDamping, config.exitGyroYawDamping);
                } else if (hadRecentSlide && exitProximity > 0.0 && Math.abs(yawRateRad) > 0.07) {
                    // カウンター維持のまま横滑りがゼロへ収束した直後の急激な逆振り（おつり）のみ緩和
                    effectiveYawDamping = Mth.lerp(0.35 * exitProximity, config.yawAngularDamping, config.exitGyroYawDamping);
                }
            }
            dynYawRateRad *= Math.pow(effectiveYawDamping, subDt);

            // 低速時は幾何学旋回、中〜高速は完全なタイヤ物理YawRateへブレンド
            yawRateRad = Mth.lerp(tireBlend, geomOmegaRad, dynYawRateRad);

            double maxYawRad = Math.toRadians(config.maxYawRateDegPerTick);
            yawRateRad = Mth.clamp(yawRateRad, -maxYawRad, maxYawRad);

            // 低速時は不要な横速度を軽く減衰してピタッと直進・停止収束
            if (tireBlend < 0.99) {
                this.lateralVelocity *= Math.pow(0.85, subDt * (1.0 - tireBlend));
            }

            // コーナリングドラッグ（横滑り角度が大きいほどタイヤ摩擦により自然に減速し、グリップ回復へ繋げる）
            double bodySlipRad = Math.atan2(Math.abs(this.lateralVelocity), Math.max(Math.abs(this.forwardVelocity), 0.05));
            double dragRetention = 1.0 - Math.min(0.06, bodySlipRad * config.corneringDragFactor * subDt);

            // ドリフト継続時間・距離に応じた追加スクラブ抵抗（減速緩和微調整版：ロングドリフト時もスピードを維持）
            if (this.continuousDriftTicks > 22) {
                double scrubDrag = Math.min(0.0085 * this.tireScrubMultiplier, (this.continuousDriftTicks - 22) * 0.00022);
                dragRetention -= scrubDrag * subDt;
            }

            // 車体の微小回転 δYaw に対して、速度ベクトル自体を新しい車体ローカル軸へ正確に再射影する
            double dYawRad = yawRateRad * subDt;
            double cosD = Math.cos(dYawRad);
            double sinD = Math.sin(dYawRad);

            double nextFwd = (this.forwardVelocity * cosD + this.lateralVelocity * sinD) * dragRetention;
            double nextLat = this.lateralVelocity * cosD - this.forwardVelocity * sinD;
            this.forwardVelocity = nextFwd;
            this.lateralVelocity = nextLat;

            // 中立操舵中にドリフト立ち上がりで lateralVelocity が 0 を跨いだ瞬間、過剰な逆滑り＆Yawを吸収
            if (config.enableExitGyro && steeringNeutral && hadRecentSlide
                    && Math.abs(subStartLat) > 1e-4 && (subStartLat * this.lateralVelocity <= 0.0)) {
                this.lateralVelocity *= config.exitLatZeroCrossDamping;
                yawRateRad *= 0.55;
            }

            currentYaw += (float) Math.toDegrees(dYawRad);

            lastFyF = fyF;
            lastFyR = fyR;
            lastAlphaF = alphaF;
            lastAlphaR = alphaR;
            lastGyroDeg = Math.toDegrees(gyroSteerRad);
        }

        this.yawRateDeg = (float) Math.toDegrees(yawRateRad);
        this.lateralForceFront = lastFyF;
        this.lateralForceRear = lastFyR;
        this.frontSlipAngleDeg = (float) Math.toDegrees(lastAlphaF);
        this.rearSlipAngleDeg = (float) Math.toDegrees(lastAlphaR);
        this.frontSlipping = anyFrontSlip;
        this.rearSlipping = anyRearSlip;
        this.frontGripUsage = (float) (Math.abs(lastFyF) / Math.max(0.001, this.frontGripLimit));
        this.rearGripUsage = (float) (Math.abs(lastFyR) / Math.max(0.001, this.rearGripLimit));
        this.lastGyroSteerDeg = lastGyroDeg;

        return currentYaw;
    }

    /**
     * 最大横力を超えないように制限する（限界超過後は動摩擦残存率で自然にサチュレート）
     */
    private static double clampWithGripLimit(double rawForce, double limit, double peakDropRatio) {
        double abs = Math.abs(rawForce);
        if (abs <= limit) {
            return rawForce;
        }
        // 限界超過後、少しだけグリップが落ちて滑らかなブレイク感を出す（急激なゼロ落ちはさせない）
        double excess = Math.min(1.0, (abs - limit) / Math.max(0.001, limit));
        double dropFactor = 1.0 - excess * (1.0 - peakDropRatio);
        return Math.signum(rawForce) * limit * dropFactor;
    }

    private void updateSlipAndDriftFlags() {
        double totalHoriz = Math.hypot(this.forwardVelocity, this.lateralVelocity);
        if (totalHoriz > 0.015) {
            this.slipAngleDeg = (float) Math.toDegrees(Math.atan2(this.lateralVelocity, Math.abs(this.forwardVelocity)));
        } else {
            this.slipAngleDeg = 0.0f;
        }
        // スリップ角 8度以上 または 後輪が限界滑りを起こしているときドリフト状態と判定
        this.drifting = (totalHoriz > 0.04) && (Math.abs(this.slipAngleDeg) >= 8.0f || this.rearSlipping);
    }

    /**
     * 一時テスト用：REALモードの走行挙動を logs/rc_real_telemetry.csv へ記録する
     */
    public void recordTelemetryTick(
            int entityId,
            double posX,
            double posZ,
            float inputForward,
            float inputSteer,
            boolean inputHandbrake
    ) {
        if (!this.config.enableTelemetryLog) {
            return;
        }
        double speed = Math.hypot(this.forwardVelocity, this.lateralVelocity);
        boolean active = speed > 0.015 || Math.abs(inputForward) > 0.05f || Math.abs(inputSteer) > 0.05f || inputHandbrake;
        if (!active) {
            return;
        }
        this.telemetryTickCounter++;
        double yawRadPerTick = Math.abs(Math.toRadians(this.yawRateDeg));
        double estRadius = yawRadPerTick > 0.002 ? Math.min(999.9, speed / yawRadPerTick) : 999.9;

        String line = String.format(
                Locale.ROOT,
                "%d,%d,%.4f,%.4f,%.2f,%.2f,%d,%.2f,%d,%d,%.5f,%.5f,%.5f,%.2f,%.2f,%.3f,%.2f,%.3f,%.3f,%.5f,%.5f,%.5f,%.5f,%.2f,%.2f,%.3f,%.2f",
                this.telemetryTickCounter,
                entityId,
                posX,
                posZ,
                inputForward,
                inputSteer,
                inputHandbrake ? 1 : 0,
                this.steerAngleDeg,
                this.counterSteering ? 1 : 0,
                this.drifting ? 1 : 0,
                this.forwardVelocity,
                this.lateralVelocity,
                speed,
                this.slipAngleDeg,
                this.lastStepYawDeg,
                this.yawRateDeg,
                estRadius,
                this.frontLoadRatio,
                this.rearLoadRatio,
                this.frontGripLimit,
                this.rearGripLimit,
                this.lateralForceFront,
                this.lateralForceRear,
                this.frontGripUsage,
                this.rearGripUsage,
                this.lastRearDynMult,
                this.lastGyroSteerDeg
        );

        synchronized (TELEMETRY_LOCK) {
            try {
                if (telemetryWriter == null) {
                    Path logPath = Path.of("logs", "rc_real_telemetry.csv");
                    if (logPath.getParent() != null) {
                        Files.createDirectories(logPath.getParent());
                    }
                    boolean truncate = !telemetryFileInitialized;
                    telemetryWriter = Files.newBufferedWriter(
                            logPath,
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            truncate ? StandardOpenOption.TRUNCATE_EXISTING : StandardOpenOption.APPEND,
                            StandardOpenOption.WRITE
                    );
                    if (truncate) {
                        telemetryWriter.write("tick,entityId,posX,posZ,inFwd,inSteer,brake,steerDeg,counter,drift,fwdVel,latVel,speed,slipDeg,yawDeg,yawRateDeg,estRadius,fLoad,rLoad,fLimit,rLimit,fyF,fyR,fUsage,rUsage,rearDynMult,gyroDeg");
                        telemetryWriter.newLine();
                        telemetryFileInitialized = true;
                    }
                }
                telemetryWriter.write(line);
                telemetryWriter.newLine();
                if (this.telemetryTickCounter % 10 == 0) {
                    telemetryWriter.flush();
                }
            } catch (Exception ignored) {
            }
        }
    }

    public void flushTelemetry() {
        synchronized (TELEMETRY_LOCK) {
            if (telemetryWriter != null) {
                try {
                    telemetryWriter.flush();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
