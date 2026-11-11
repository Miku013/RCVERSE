package com.microdrone.mod.physics;

/**
 * REALモード物理演算用の調整パラメーター集
 * （Minecraft 1tick = 0.05s → 1G = 9.8 * 0.05^2 = 0.0245 blocks/tick^2
 * の実物理スケール準拠）
 */
public class RealPhysicsConfig {
    // 操舵・車体ディメンション
    public float maxSteeringAngleDeg = 35.0f; // 通常時の最大前輪操舵角 (度)
    public float maxCounterSteerAngleDeg = 44.0f; // ドリフト・カウンター側の最大切れ角 (度)
    public float driftStartAngleDeg = 22.0f; // 横滑り(パワースライド)が始まるステア切れ角しきい値 (度)
    public float steeringLerpRate = 0.24f; // 通常切り込み追従速度 (微調整しやすい滑らかさ)
    public float counterSteerLerpRate = 0.18f; // カウンター側への当て込み速度 (立ち上がり抜け・当てすぎ防止)
    public float steeringReturnRate = 0.26f; // センターへの戻し速度 (徐々にグリップ回復させる)
    public float counterReturnRate = 0.28f; // カウンターステア解放時のセンター戻し速度
    public float wheelBase = 0.42f; // ホイールベース (blocks相当)
    public float frontAxleRatio = 0.48f; // 重心→前軸の比率 (0.48 = ほぼ50:50)
    public float steeringSensitivity = 1.0f; // 操舵感度倍率

    // 加減速・転がり抵抗・ブレーキ
    public double realSpeedScale = 1.32; // REALモード最高速倍率 (走行スピード・ドリフト進入速度の確保)
    public double realAccelScale = 1.25; // REALモード加速度倍率 (コーナー立ち上がり・空転トラクション確保)
    public double rollingFriction = 0.968; // アクセルOFF時の自然な転がり慣性
    public double brakeDecelMultiplier = 2.4; // Sキーのブレーキ減速度倍率
    public double spaceBrakeDecelMultiplier = 0.68; // スペースキー専用フットブレーキ減速度倍率 (マイルド制御)
    public double reversePowerMultiplier = 0.55; // リバース出力倍率
    public double corneringDragFactor = 0.020; // 横滑り時のタイヤ引きずり減速係数

    // 第3段階：前後輪独立可変グリップパラメーター
    // 前輪がしっかりインへ切れ込み、パワーオン大舵角で自然にリアがステップアウトする設定
    public float baseFrontGrip = 1.16f; // 前輪の基本グリップ係数 (進入アンダーステア防止)
    public float baseRearGrip = 1.04f; // 後輪の基本グリップ係数
    public double corneringStiffnessFront = 0.068; // 前輪コーナリング剛性 (適度な進入応答とカウンター許容幅)
    public double corneringStiffnessRear = 0.060; // 後輪コーナリング剛性
    public double maxLateralAccelPerTick = 0.0165; // 1軸あたりの基準最大横加速度 (速度向上に合わせた旋回力)
    public double frontSlipPeakDropRatio = 0.96; // 前輪限界超過後の粘り率 (96%: カウンター時に逃げにくい)
    public double slipLimitPeakDropRatio = 0.90; // 後輪限界超過後の動摩擦残存率 (90%)
    public double brakeRearGripRetention = 0.88; // フットブレーキ時の後輪横グリップ保持率 (制動と荷重移動を優先)
    public double powerOversteerGripMult = 0.62; // 強アクセル時の後輪横グリップ低下倍率 (抜重感と持続の両立)
    public double counterFrontBiteGripMult = 1.00; // カウンターステア中のフロント食いつき倍率 (1.0=急激な立て直し抜けを防止)
    public double counterFrontBiteStiffMult = 0.88; // カウンターステア中のフロント剛性補正 (0.88=カウンターの甘い当て維持がしやすい)

    // ヨー角速度・低速安定化・発散防止
    public double yawTorqueGain = 17.0; // タイヤ横力モーメントからYaw角速度への変換ゲイン (急な立ち上がり戻し緩和)
    public double yawAngularDamping = 0.82; // ヨー回転の自然減衰（過剰スピン抑制・持続ドリフト両立）
    public float maxYawRateDegPerTick = 14.0f; // 旋回角速度上限 (deg/tick)
    public double lowSpeedGeomThreshold = 0.055; // この速度以下のみ幾何学ステア補助をブレンド
    public double lowSpeedSnapThreshold = 0.004; // 停止スナップ閾値
    public int physicsSubSteps = 4; // 1tick内のタイヤ物理サブステップ分割数 (振動・発散防止)

    // ② RCドリフト用ステアジャイロ／立ち上がりダンピング（テスト実装）
    public boolean enableExitGyro = true; // 立ち上がりジャイロ＆Yaw減衰の有効化
    public double exitDampingLatThreshold = 0.042; // 立ち上がり収束帯とみなす横速度しきい値 (blocks/tick)
    public double exitGyroYawDamping = 0.65; // 立ち上がり収束帯＆中立戻し時の追加Yaw減衰 (徐々にグリップ・直進へ収束)
    public double gyroCounterAssistGain = 0.20; // ステア中立戻し時の過剰YawRate抑止ジャイロゲイン
    public double exitLatZeroCrossDamping = 0.35; // 中立時lateralVelocity=0跨ぎ（おつり）瞬間の横滑り減衰倍率

    // 一時テスト用: 挙動解析CSVログ (logs/rc_real_telemetry.csv)
    public boolean enableTelemetryLog = true;

    // 第4段階：荷重移動システムパラメーター
    public float longitudinalWeightTransferGain = 3.4f; // ブレーキ・減速Gに対する前後荷重移動感度
    public float accelWeightTransferScale = 0.42f; // 加速時のフロント抜け軽減倍率 (0.42=加速中は前輪抜けを抑制)
    public float lateralWeightTransferGain = 8.5f; // 横G(0.022基準)に対する左右荷重移動感度
    public float maxLongitudinalLoadShift = 0.24f; // 前後荷重移動の最大変位 (0.50±0.24 → 0.26〜0.74)
    public float maxLateralLoadShift = 0.22f; // 左右荷重移動の最大変位
    public float minAxleLoadRatio = 0.22f; // 抜重時の最小軸荷重比率（極端なスピン発散防止）
    public float maxAxleLoadRatio = 0.78f; // 加重時の最大軸荷重比率
    public float weightTransferLerp = 0.45f; // 荷重移動のサスペンション応答速度
    public float lateralLoadSensitivity = 0.45f; // 左右荷重偏り時の非線形タイヤ効率低下係数
}
