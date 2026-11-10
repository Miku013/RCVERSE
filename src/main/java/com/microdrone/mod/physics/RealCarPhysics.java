package com.microdrone.mod.physics;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * RCカー REALモード専用物理演算エンジン
 *
 * 【第2段階：速度分離・座標変換・基礎走行】
 * - ワールド速度を車体ローカルの「前後方向速度 (forwardVelocity)」と
 *   「左右横方向速度 (lateralVelocity)」へ分離して管理する。
 * - 車体の向き (Yaw) と実際の進行方向を独立させ、旋回時に横方向慣性が生まれる。
 * - 既存の RACE モードやモデルアニメーションとは完全に分離されている。
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

    private double forwardVelocity = 0.0; // 車体前方(+)/後方(-)の速度 (blocks/tick)
    private double lateralVelocity = 0.0; // 車体右(+)/左(-)の横滑り速度 (blocks/tick)
    private float yawRateDeg = 0.0f;      // 旋回速度 (deg/tick)
    private float steerAngleDeg = 0.0f;   // 現在の前輪操舵角 (deg, 左Aが正/右Dが負)
    private float slipAngleDeg = 0.0f;    // 車体進行方向とのスリップ角 (deg)
    private float surfaceFriction = 1.0f; // 現在の路面摩擦係数 (第5段階で路面判定連動)
    private boolean drifting = false;

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

    public float getSurfaceFriction() {
        return this.surfaceFriction;
    }

    public boolean isDrifting() {
        return this.drifting;
    }

    /**
     * ワールド速度と車体Yawから、車体ローカル速度（前後/左右）へ同期する。
     * RACE -> REAL モード切替時や外部衝突後の引き継ぎに使用し、速度を不自然にリセットしない。
     */
    public void syncFromWorldVelocity(Vec3 worldVel, float yawDeg, float currentSteerDeg) {
        float yawRad = yawDeg * Mth.DEG_TO_RAD;
        double fwdX = -Mth.sin(yawRad);
        double fwdZ = Mth.cos(yawRad);
        double rightX = -fwdZ; // (-cos, -sin)
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
    }

    /**
     * REALモードの1tick分の水平物理演算を実行する。
     *
     * @param prevWorldVel    前tickのワールド水平速度
     * @param currentYawDeg   現在の車体向き (deg)
     * @param inputForward    アクセル(+)/ブレーキ・後退(-)入力 (-1.0〜+1.0)
     * @param inputSteer      ステアリング入力 (左A:+1.0 / 右D:-1.0)
     * @param inputHandbrake  サイドブレーキ入力
     * @param grounded        タイヤまたは車体が接地しているか
     * @param wallAhead       前方に壁が密着しているか
     * @param maxSpeed        モーター＆車体コンフィグ由来の最高速度 (blocks/tick)
     * @param accel           モーター＆車体コンフィグ由来の基礎加速度
     * @param turnMult        車体コンフィグの旋回倍率
     * @param invertSteer     ステア反転フラグ
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

        // 2. 前輪操舵角の更新（左Aキーが正、最大±35度）
        float steerDir = invertSteer ? -1.0f : 1.0f;
        float targetSteerDeg = inputSteer * config.maxSteeringAngleDeg * steerDir;
        this.steerAngleDeg = Mth.lerp(config.steeringLerpRate, this.steerAngleDeg, targetSteerDeg);

        // 3. 前後方向のアクセル・ブレーキ・転がり抵抗（接地中のみ駆動力・ブレーキが効く）
        double effectiveAccel = wallAhead && inputForward > 0.05f ? accel * 0.08 : accel;
        if (wallAhead && inputForward > 0.05f) {
            this.forwardVelocity = Math.min(this.forwardVelocity, 0.015);
        }

        if (grounded) {
            if (inputForward > 0.05f) {
                if (this.forwardVelocity < -0.01) {
                    // 後退中の前進入力は強ブレーキ
                    this.forwardVelocity += effectiveAccel * config.brakeDecelMultiplier;
                    if (this.forwardVelocity > 0.0) {
                        this.forwardVelocity = 0.0;
                    }
                } else {
                    this.forwardVelocity += effectiveAccel * inputForward;
                }
            } else if (inputForward < -0.05f) {
                if (this.forwardVelocity > 0.01) {
                    // 前進中の後退入力はブレーキ
                    this.forwardVelocity -= effectiveAccel * config.brakeDecelMultiplier;
                    if (this.forwardVelocity < 0.0) {
                        this.forwardVelocity = 0.0;
                    }
                } else {
                    this.forwardVelocity += effectiveAccel * inputForward * config.reversePowerMultiplier;
                }
            } else {
                // アクセルOFF時の自然な転がり抵抗
                this.forwardVelocity *= config.rollingFriction;
            }

            // サイドブレーキ押下時は少し制動が掛かる
            if (inputHandbrake) {
                this.forwardVelocity *= 0.96;
            }
        } else {
            // 空中飛行中はわずかな空気抵抗のみ
            this.forwardVelocity *= 0.995;
            this.lateralVelocity *= 0.995;
        }

        this.forwardVelocity = Mth.clamp(this.forwardVelocity, -maxSpeed * 0.45, maxSpeed);

        // 4. 旋回角速度 (YawRate) と車体向きの更新
        //    （第2段階: ホイールベース＆速度に応じた自然な旋回 / 第3段階で前後タイヤ横力差モーメントへ発展）
        float newYawDeg = currentYawDeg;
        if (grounded && Math.abs(this.forwardVelocity) > 0.002) {
            double speedAbs = Math.abs(this.forwardVelocity);
            double speedRatio = Mth.clamp(speedAbs / Math.max(0.15, maxSpeed), 0.0, 1.0);
            // 高速域ではハンドル角あたりの急激な旋回をマイルドにし、旋回半径を大きくする
            double highSpeedDamp = 1.0 - speedRatio * 0.38;
            double steerRad = Math.toRadians(this.steerAngleDeg) * config.steeringSensitivity * turnMult;
            // 自転車幾何学ベースの目標角速度 (速度 × tan(steer) / wheelBase)
            double angularVelRad = (this.forwardVelocity / Math.max(0.20, config.wheelBase)) * Math.tan(steerRad) * 0.42 * highSpeedDamp;
            if (inputHandbrake && speedAbs > 0.04) {
                // サイドブレーキ時はテールが流れやすくヨーレートが増幅
                angularVelRad *= 1.35;
            }
            float targetYawRate = (float) Math.toDegrees(-angularVelRad); // 左A(steer>0)でYaw減
            targetYawRate = Mth.clamp(targetYawRate, -config.maxYawRateDegPerTick, config.maxYawRateDegPerTick);

            this.yawRateDeg = Mth.lerp(0.45f, this.yawRateDeg, targetYawRate);
            newYawDeg = currentYawDeg + this.yawRateDeg;
        } else {
            this.yawRateDeg = Mth.lerp(0.25f, this.yawRateDeg, 0.0f);
        }

        // 5. 車体が回転しても移動慣性ベクトル自体はすぐには回らないため、
        //    回転前のローカル速度(forward, lateral)を回転前Yawでワールドへ戻し、回転後Yawへ再射影する！
        //    これにより「車体が曲がった瞬間に横方向速度が自然に生まれる」状態になる。
        double preWorldX = prevFwdX * this.forwardVelocity + prevRightX * this.lateralVelocity;
        double preWorldZ = prevFwdZ * this.forwardVelocity + prevRightZ * this.lateralVelocity;

        float newYawRad = newYawDeg * Mth.DEG_TO_RAD;
        double newFwdX = -Mth.sin(newYawRad);
        double newFwdZ = Mth.cos(newYawRad);
        double newRightX = -newFwdZ;
        double newRightZ = newFwdX;

        this.forwardVelocity = preWorldX * newFwdX + preWorldZ * newFwdZ;
        this.lateralVelocity = preWorldX * newRightX + preWorldZ * newRightZ;

        // 6. 横方向速度のタイヤグリップによる段階的減衰（毎tickゼロにせず慣性を残す）
        if (grounded) {
            double retention = inputHandbrake ? config.handbrakeLateralRetention : config.normalLateralRetention;
            this.lateralVelocity *= retention;

            // 低速域での不安定な微小滑りを抑制
            if (Math.abs(this.forwardVelocity) < config.lowSpeedSnapThreshold && Math.abs(inputForward) < 0.05f) {
                this.forwardVelocity = 0.0;
            }
            if (Math.abs(this.lateralVelocity) < config.lowSpeedSnapThreshold * 0.5) {
                this.lateralVelocity = 0.0;
            }
        }

        // 発散防止の上限クランプ
        this.forwardVelocity = Mth.clamp(this.forwardVelocity, -maxSpeed * 0.6, maxSpeed * 1.15);
        this.lateralVelocity = Mth.clamp(this.lateralVelocity, -maxSpeed * 1.1, maxSpeed * 1.1);

        updateSlipAndDriftFlags();

        // 7. 新しいローカル速度から最終ワールド速度ベクトルを再合成
        double outWorldX = newFwdX * this.forwardVelocity + newRightX * this.lateralVelocity;
        double outWorldZ = newFwdZ * this.forwardVelocity + newRightZ * this.lateralVelocity;
        Vec3 outWorldVel = new Vec3(outWorldX, 0.0, outWorldZ);

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

    private void updateSlipAndDriftFlags() {
        double totalHoriz = Math.hypot(this.forwardVelocity, this.lateralVelocity);
        if (totalHoriz > 0.015) {
            this.slipAngleDeg = (float) Math.toDegrees(Math.atan2(this.lateralVelocity, Math.abs(this.forwardVelocity)));
        } else {
            this.slipAngleDeg = 0.0f;
        }
        // スリップ角 10度以上 かつ 一定の速度がある時をドリフト状態と判定
        this.drifting = (totalHoriz > 0.045) && (Math.abs(this.slipAngleDeg) >= 10.0f);
    }
}
