package com.microdrone.mod.physics;

/**
 * REALモード物理演算用の調整パラメーター集
 * （第2段階〜第5段階で段階的に拡張できるようパラメーターを一元管理）
 */
public class RealPhysicsConfig {
    // 操舵・車体ディメンション
    public float maxSteeringAngleDeg = 35.0f;     // 最大前輪操舵角 (度)
    public float steeringLerpRate = 0.35f;        // ステアリング追従速度 (0〜1)
    public float wheelBase = 0.40f;               // ホイールベース (blocks相当)
    public float steeringSensitivity = 1.0f;      // 操舵感度倍率

    // 加減速・転がり抵抗
    public double rollingFriction = 0.965;        // アクセルOFF時の自然な転がり慣性 (RACEより長く滑る)
    public double brakeDecelMultiplier = 2.4;     // ブレーキ減速度倍率
    public double reversePowerMultiplier = 0.55;  // リバース出力倍率

    // 横方向グリップ減衰（第2段階の簡易減衰率 / 第3段階で前後タイヤグリップ限界へ発展）
    public double normalLateralRetention = 0.72;  // 通常走行時の横速度残存率 (値が小さいほどすぐ曲がり・大きいほどよく滑る)
    public double handbrakeLateralRetention = 0.94; // サイドブレーキ時の横速度残存率
    public double lowSpeedSnapThreshold = 0.004;  // 低速不安定防止の停止スナップ閾値

    // 将来の第3・第4・第5段階用パラメーター（初期値保持）
    public float baseFrontGrip = 1.05f;           // 前輪基本グリップ係数
    public float baseRearGrip = 0.98f;            // 後輪基本グリップ係数
    public float maxYawRateDegPerTick = 16.0f;    // 旋回角速度上限 (発散防止)
}
