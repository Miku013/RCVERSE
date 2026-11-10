package com.microdrone.mod.physics;

/**
 * REALモード物理演算用の調整パラメーター集
 * （数値の発散防止クランプおよび第3段階：可変グリップ／第4・5段階用パラメーターを含む）
 */
public class RealPhysicsConfig {
    // 操舵・車体ディメンション
    public float maxSteeringAngleDeg = 35.0f;       // 最大前輪操舵角 (度)
    public float steeringLerpRate = 0.35f;          // ステアリング追従速度 (0〜1)
    public float wheelBase = 0.42f;                 // ホイールベース (blocks相当)
    public float frontAxleRatio = 0.48f;            // 重心→前軸の比率 (0.48 = ほぼ50:50バランス)
    public float steeringSensitivity = 1.0f;        // 操舵感度倍率

    // 加減速・転がり抵抗
    public double rollingFriction = 0.968;          // アクセルOFF時の自然な転がり慣性
    public double brakeDecelMultiplier = 2.4;       // ブレーキ減速度倍率
    public double reversePowerMultiplier = 0.55;    // リバース出力倍率

    // 第3段階：前後輪独立可変グリップパラメーター
    public float baseFrontGrip = 1.12f;             // 前輪の基本グリップ係数 (少し高め＝しっかり曲がる)
    public float baseRearGrip = 1.02f;              // 後輪の基本グリップ係数
    public double corneringStiffnessFront = 0.46;   // 前輪コーナリング剛性（スリップ角に対する立ち上がり）
    public double corneringStiffnessRear = 0.44;    // 後輪コーナリング剛性
    public double maxLateralAccelPerTick = 0.058;   // 基準最大横加速度 (1.0G相当の横グリップ上限基準)
    public double slipLimitPeakDropRatio = 0.88;    // グリップ限界超過後の動摩擦残存率 (88%: コントロールしやすい滑り)
    public double handbrakeRearGripMult = 0.32;     // サイドブレーキ時の後輪グリップ倍率
    public double powerOversteerGripMult = 0.78;    // 強アクセル時の後輪横グリップ低下倍率 (駆動力消費の簡易表現)

    // ヨー角速度・低速安定化・発散防止
    public double yawTorqueGain = 14.5;             // タイヤ横力モーメントからYaw角速度への変換ゲイン
    public double yawAngularDamping = 0.86;         // ヨー回転の自然減衰（極端なスピン発散を抑制）
    public float maxYawRateDegPerTick = 14.5f;      // 旋回角速度上限 (deg/tick)
    public double lowSpeedGeomThreshold = 0.035;    // 低速域の幾何学旋回ブレンド閾値
    public double lowSpeedSnapThreshold = 0.004;    // 停止スナップ閾値
    public int physicsSubSteps = 4;                 // 1tick内のタイヤ物理サブステップ分割数 (振動・発散防止)

    // 第4段階：荷重移動システムパラメーター
    public float longitudinalWeightTransferGain = 2.4f; // 縦加減速Gに対する前後荷重移動感度
    public float lateralWeightTransferGain = 2.6f;      // 横Gに対する左右荷重移動感度
    public float maxLongitudinalLoadShift = 0.22f;      // 前後荷重移動の最大変位 (0.50±0.22 → 0.28〜0.72)
    public float maxLateralLoadShift = 0.20f;           // 左右荷重移動の最大変位
    public float minAxleLoadRatio = 0.24f;              // 抜重時の最小軸荷重比率（極端なスピン発散防止）
    public float maxAxleLoadRatio = 0.76f;              // 加重時の最大軸荷重比率
    public float weightTransferLerp = 0.45f;            // 荷重移動のサスペンション応答速度
    public float lateralLoadSensitivity = 0.45f;        // 左右荷重偏り時の非線形タイヤ効率低下係数
}
