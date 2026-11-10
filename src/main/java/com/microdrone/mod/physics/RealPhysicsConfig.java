package com.microdrone.mod.physics;

/**
 * REALモード物理演算用の調整パラメーター集
 * （Minecraft 1tick = 0.05s → 1G = 9.8 * 0.05^2 = 0.0245 blocks/tick^2 の実物理スケール準拠）
 */
public class RealPhysicsConfig {
    // 操舵・車体ディメンション
    public float maxSteeringAngleDeg = 35.0f;       // 通常時の最大前輪操舵角 (度)
    public float maxCounterSteerAngleDeg = 52.0f;   // ドリフト・カウンター側の最大切れ角 (度)
    public float steeringLerpRate = 0.24f;          // 通常切り込み追従速度 (微調整しやすい滑らかさ)
    public float counterSteerLerpRate = 0.28f;      // カウンター側への当て込み速度
    public float steeringReturnRate = 0.34f;        // センターへの戻し速度
    public float wheelBase = 0.42f;                 // ホイールベース (blocks相当)
    public float frontAxleRatio = 0.48f;            // 重心→前軸の比率 (0.48 = ほぼ50:50)
    public float steeringSensitivity = 1.0f;        // 操舵感度倍率

    // 加減速・転がり抵抗
    public double rollingFriction = 0.968;          // アクセルOFF時の自然な転がり慣性
    public double brakeDecelMultiplier = 2.4;       // ブレーキ減速度倍率
    public double reversePowerMultiplier = 0.55;    // リバース出力倍率
    public double corneringDragFactor = 0.022;      // 横滑り時のタイヤ引きずり減速係数

    // 第3段階：前後輪独立可変グリップパラメーター
    // 前後2軸合計で約 0.90G (≒ 0.022 blocks/tick^2) を基準最大横加速度とする
    public float baseFrontGrip = 1.08f;             // 前輪の基本グリップ係数
    public float baseRearGrip = 0.98f;              // 後輪の基本グリップ係数
    public double corneringStiffnessFront = 0.065;  // 前輪コーナリング剛性（スリップ角10°前後で限界付近）
    public double corneringStiffnessRear = 0.054;   // 後輪コーナリング剛性
    public double maxLateralAccelPerTick = 0.0115;  // 1軸あたりの基準最大横加速度 (前後合計 ~0.94G)
    public double frontSlipPeakDropRatio = 0.96;    // 前輪限界超過後の粘り率 (96%: カウンター時に逃げにくい)
    public double slipLimitPeakDropRatio = 0.85;    // 後輪限界超過後の動摩擦残存率 (85%)
    public double handbrakeRearGripMult = 0.30;     // サイドブレーキ時の後輪グリップ倍率
    public double powerOversteerGripMult = 0.60;    // 強アクセル時の後輪横グリップ低下倍率 (パワースライド維持)
    public double counterFrontBiteGripMult = 1.28;  // カウンターステア中のフロント食いつき限界ブースト
    public double counterFrontBiteStiffMult = 1.22; // カウンターステア中のフロント剛性ブースト

    // ヨー角速度・低速安定化・発散防止
    public double yawTorqueGain = 22.0;             // タイヤ横力モーメントからYaw角速度への変換ゲイン
    public double yawAngularDamping = 0.80;         // ヨー回転の自然減衰（過剰スピン抑制・カウンター効き両立）
    public float maxYawRateDegPerTick = 14.0f;      // 旋回角速度上限 (deg/tick)
    public double lowSpeedGeomThreshold = 0.055;    // この速度以下のみ幾何学ステア補助をブレンド
    public double lowSpeedSnapThreshold = 0.004;    // 停止スナップ閾値
    public int physicsSubSteps = 4;                 // 1tick内のタイヤ物理サブステップ分割数 (振動・発散防止)

    // 第4段階：荷重移動システムパラメーター
    public float longitudinalWeightTransferGain = 2.6f; // ブレーキ・減速Gに対する前後荷重移動感度
    public float accelWeightTransferScale = 0.55f;      // 加速時のフロント抜け軽減倍率 (0.55=加速中も前輪が残りやすい)
    public float lateralWeightTransferGain = 8.5f;      // 横G(0.022基準)に対する左右荷重移動感度
    public float maxLongitudinalLoadShift = 0.24f;      // 前後荷重移動の最大変位 (0.50±0.24 → 0.26〜0.74)
    public float maxLateralLoadShift = 0.22f;           // 左右荷重移動の最大変位
    public float minAxleLoadRatio = 0.22f;              // 抜重時の最小軸荷重比率（極端なスピン発散防止）
    public float maxAxleLoadRatio = 0.78f;              // 加重時の最大軸荷重比率
    public float weightTransferLerp = 0.45f;            // 荷重移動のサスペンション応答速度
    public float lateralLoadSensitivity = 0.45f;        // 左右荷重偏り時の非線形タイヤ効率低下係数
}
