package com.microdrone.mod.physics;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

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
        this.frontSlipping = false;
        this.rearSlipping = false;
        this.frontGripUsage = 0.0f;
        this.rearGripUsage = 0.0f;
        this.weightTransfer.reset();
        this.frontLoadRatio = 0.50f;
        this.rearLoadRatio = 0.50f;
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
            lerpRate = config.steeringReturnRate;
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
            if (inputForward > 0.05f) {
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

            if (inputHandbrake) {
                this.forwardVelocity *= 0.965;
            }
        } else {
            this.forwardVelocity *= 0.995;
            this.lateralVelocity *= 0.995;
        }

        this.forwardVelocity = Mth.clamp(this.forwardVelocity, -maxSpeed * 0.45, maxSpeed);

        // 第4段階：縦加減速Gと横Gに基づく荷重移動の更新（前後・左右・4輪個別データ）
        double longitudinalAccel = (grounded && !wallAhead) ? (this.forwardVelocity - preForwardVel) : 0.0;
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
                    inputHandbrake,
                    maxSpeed,
                    turnMult,
                    powerSpinSeverity
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
        double highSpeedSteerScale = this.counterSteering ? 1.0 : (1.0 - speedRatio * 0.22);
        double steerRad = Math.toRadians(this.steerAngleDeg) * config.steeringSensitivity * turnMult * highSpeedSteerScale;

        // グリップ限界計算：最大横力 = グリップ係数 × 荷重配分(×2) × 路面摩擦 × 基準横G
        double baseLimitScale = this.surfaceFriction * config.maxLateralAccelPerTick;
        this.frontGripLimit = Math.max(0.005, this.frontGrip * (this.frontLoadRatio * 2.0) * baseLimitScale);

        double rearDynMult = 1.0;
        if (inputHandbrake && totalSpeed > 0.03) {
            rearDynMult *= config.handbrakeRearGripMult;
        }
        // コーナーでアクセルを強く踏むと駆動力により後輪の横グリップがわずかに低下（パワースライド誘発）
        if (powerSpinSeverity > 0.1 && Math.abs(this.steerAngleDeg) > 4.0f && totalSpeed > 0.05) {
            rearDynMult *= Mth.lerp((float) powerSpinSeverity, 1.0f, (float) config.powerOversteerGripMult);
        }
        this.rearGripLimit = Math.max(0.004, this.rearGrip * (this.rearLoadRatio * 2.0) * baseLimitScale * rearDynMult);

        int subSteps = Math.max(1, config.physicsSubSteps);
        double subDt = 1.0 / subSteps;

        double yawRateRad = Math.toRadians(this.yawRateDeg); // rad/tick
        float currentYaw = startYawDeg;

        double lastFyF = 0.0;
        double lastFyR = 0.0;
        double lastAlphaF = 0.0;
        double lastAlphaR = 0.0;
        boolean anyFrontSlip = false;
        boolean anyRearSlip = false;

        for (int i = 0; i < subSteps; i++) {
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

            // 前軸・後軸の横方向接地速度 (右方向が正)
            double vyFront = this.lateralVelocity + lf * yawRateRad;
            double vyRear = this.lateralVelocity - lr * yawRateRad;

            // スリップ角 (rad)
            double alphaF = Math.atan2(vyFront, denom) + (fwdSign * steerRad);
            double alphaR = Math.atan2(vyRear, denom);

            // グリップ限界前の生横力
            double rawFyF = -config.corneringStiffnessFront * (this.frontLoadRatio * 2.0) * this.surfaceFriction * alphaF;
            double rawFyR = -config.corneringStiffnessRear * (this.rearLoadRatio * 2.0) * this.surfaceFriction * alphaR;

            // 限界超過判定＆グリップ限界によるクランプ（超過時はわずかな動摩擦低下を伴う）
            boolean fSlip = Math.abs(rawFyF) > this.frontGripLimit;
            boolean rSlip = Math.abs(rawFyR) > this.rearGripLimit;
            anyFrontSlip |= fSlip;
            anyRearSlip |= rSlip;

            double fyF = clampWithGripLimit(rawFyF, this.frontGripLimit, config.slipLimitPeakDropRatio);
            double fyR = clampWithGripLimit(rawFyR, this.rearGripLimit, config.slipLimitPeakDropRatio);

            // 低速域用の幾何学的ターゲット角速度 (微速旋回のみ補助、中〜高速は完全タイヤ物理)
            double geomOmegaRad = -(this.forwardVelocity / Math.max(0.20, config.wheelBase))
                    * Math.tan(steerRad) * 0.42;

            // 全体横力とヨーモーメントの適用
            double totalFy = fyF + fyR;
            double yawTorque = (lf * fyF) - (lr * fyR);

            this.lateralVelocity += totalFy * tireBlend * subDt;

            double dynYawRateRad = yawRateRad + (yawTorque * config.yawTorqueGain) * tireBlend * subDt;
            dynYawRateRad *= Math.pow(config.yawAngularDamping, subDt);

            // 低速時は幾何学旋回、中〜高速は完全なタイヤ物理YawRateへブレンド
            yawRateRad = Mth.lerp(tireBlend, geomOmegaRad, dynYawRateRad);

            double maxYawRad = Math.toRadians(config.maxYawRateDegPerTick);
            yawRateRad = Mth.clamp(yawRateRad, -maxYawRad, maxYawRad);

            // 低速かつサイドブレーキ非使用時は不要な横速度を軽く減衰してピタッと直進収束
            if (tireBlend < 0.99 && !inputHandbrake) {
                this.lateralVelocity *= Math.pow(0.85, subDt * (1.0 - tireBlend));
            }

            // コーナリングドラッグ（横滑り角度が大きいほどタイヤ摩擦により自然に減速し、グリップ回復へ繋げる）
            double bodySlipRad = Math.atan2(Math.abs(this.lateralVelocity), Math.max(Math.abs(this.forwardVelocity), 0.05));
            double dragRetention = 1.0 - Math.min(0.06, bodySlipRad * config.corneringDragFactor * subDt);

            // 車体の微小回転 δYaw に対して、速度ベクトル自体を新しい車体ローカル軸へ正確に再射影する
            double dYawRad = yawRateRad * subDt;
            double cosD = Math.cos(dYawRad);
            double sinD = Math.sin(dYawRad);

            double nextFwd = (this.forwardVelocity * cosD + this.lateralVelocity * sinD) * dragRetention;
            double nextLat = this.lateralVelocity * cosD - this.forwardVelocity * sinD;
            this.forwardVelocity = nextFwd;
            this.lateralVelocity = nextLat;

            currentYaw += (float) Math.toDegrees(dYawRad);

            lastFyF = fyF;
            lastFyR = fyR;
            lastAlphaF = alphaF;
            lastAlphaR = alphaR;
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
}
