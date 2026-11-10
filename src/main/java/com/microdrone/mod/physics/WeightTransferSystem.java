package com.microdrone.mod.physics;

import net.minecraft.util.Mth;

/**
 * 荷重移動システム (WeightTransferSystem)
 *
 * 【第4段階：加減速・旋回による動的荷重配分】
 * - 加速時は後輪側へ、ブレーキ・減速時は前輪側へ荷重を移動させる。
 * - 左旋回時は右側へ、右旋回時は左側へ遠心力による荷重を移動させる。
 * - 将来的に4輪サスペンション（モンスタートラック等）と直接連動できるよう、
 *   前後・左右および4輪個別の荷重データを独立して保持・管理する。
 */
public class WeightTransferSystem {

    // 前後・左右の荷重比率（合計 = 1.0）
    private float frontLoadRatio = 0.50f;
    private float rearLoadRatio = 0.50f;
    private float leftLoadRatio = 0.50f;
    private float rightLoadRatio = 0.50f;

    // 中立 (0.50) からの移動量（正=後ろ/右、負=前/左）
    private float longitudinalShift = 0.0f;
    private float lateralShift = 0.0f;

    // 4輪個別の荷重比率（4輪合計 = 1.0、将来サスペンション連動用）
    private float loadFL = 0.25f;
    private float loadFR = 0.25f;
    private float loadRL = 0.25f;
    private float loadRR = 0.25f;

    // バイシクルモデル（前軸・後軸）用の実効グリップ荷重（左右荷重感度を考慮）
    private float frontEffectiveLoad = 0.50f;
    private float rearEffectiveLoad = 0.50f;

    public float getFrontLoadRatio() {
        return this.frontLoadRatio;
    }

    public float getRearLoadRatio() {
        return this.rearLoadRatio;
    }

    public float getLeftLoadRatio() {
        return this.leftLoadRatio;
    }

    public float getRightLoadRatio() {
        return this.rightLoadRatio;
    }

    public float getLongitudinalShift() {
        return this.longitudinalShift;
    }

    public float getLateralShift() {
        return this.lateralShift;
    }

    public float getLoadFL() {
        return this.loadFL;
    }

    public float getLoadFR() {
        return this.loadFR;
    }

    public float getLoadRL() {
        return this.loadRL;
    }

    public float getLoadRR() {
        return this.loadRR;
    }

    public float getFrontEffectiveLoad() {
        return this.frontEffectiveLoad;
    }

    public float getRearEffectiveLoad() {
        return this.rearEffectiveLoad;
    }

    public void reset() {
        this.longitudinalShift = 0.0f;
        this.lateralShift = 0.0f;
        this.frontLoadRatio = 0.50f;
        this.rearLoadRatio = 0.50f;
        this.leftLoadRatio = 0.50f;
        this.rightLoadRatio = 0.50f;
        this.loadFL = 0.25f;
        this.loadFR = 0.25f;
        this.loadRL = 0.25f;
        this.loadRR = 0.25f;
        this.frontEffectiveLoad = 0.50f;
        this.rearEffectiveLoad = 0.50f;
    }

    /**
     * 1tick分の縦加速度・横力から荷重配分を更新する。
     *
     * @param longitudinalAccel 前後方向加速度 (正=加速→後輪荷重増 / 負=ブレーキ減速→前輪荷重増)
     * @param totalLateralForce タイヤ合計横力 (負=左旋回→右荷重増 / 正=右旋回→左荷重増)
     * @param grounded          接地中かどうか（空中時は中立へ戻す）
     * @param config            調整パラメーター
     */
    public void update(double longitudinalAccel, double totalLateralForce, boolean grounded, RealPhysicsConfig config) {
        float targetLongShift = 0.0f;
        float targetLatShift = 0.0f;

        if (grounded) {
            // 加速(a>0) → 後輪側(+)へ移動 / ブレーキ(a<0) → 前輪側(-)へ移動
            targetLongShift = (float) Mth.clamp(
                    longitudinalAccel * config.longitudinalWeightTransferGain,
                    -config.maxLongitudinalLoadShift,
                    config.maxLongitudinalLoadShift
            );

            // 左旋回(Fy<0) → 右側(+)へ移動 / 右旋回(Fy>0) → 左側(-)へ移動
            targetLatShift = (float) Mth.clamp(
                    (-totalLateralForce) * config.lateralWeightTransferGain,
                    -config.maxLateralLoadShift,
                    config.maxLateralLoadShift
            );
        }

        // サスペンションの沈み込み遅れを再現するため滑らかに補間
        this.longitudinalShift = Mth.lerp(config.weightTransferLerp, this.longitudinalShift, targetLongShift);
        this.lateralShift = Mth.lerp(config.weightTransferLerp, this.lateralShift, targetLatShift);

        // 前後・左右の軸荷重比率を更新
        this.frontLoadRatio = Mth.clamp(0.50f - this.longitudinalShift, config.minAxleLoadRatio, config.maxAxleLoadRatio);
        this.rearLoadRatio = 1.0f - this.frontLoadRatio;

        this.rightLoadRatio = Mth.clamp(0.50f + this.lateralShift, config.minAxleLoadRatio, config.maxAxleLoadRatio);
        this.leftLoadRatio = 1.0f - this.rightLoadRatio;

        // 4輪それぞれの個別荷重比率を算出（将来サスペンション連動用）
        this.loadFL = this.frontLoadRatio * this.leftLoadRatio;
        this.loadFR = this.frontLoadRatio * this.rightLoadRatio;
        this.loadRL = this.rearLoadRatio * this.leftLoadRatio;
        this.loadRR = this.rearLoadRatio * this.rightLoadRatio;

        // 左右荷重偏り時の非線形タイヤ荷重感度（偏るほどトータルグリップがわずかに落ちる実車特性）
        float latImbalance = Math.abs(this.lateralShift) * 2.0f; // 0.0〜0.48程度
        float lateralEfficiency = 1.0f - (latImbalance * latImbalance * config.lateralLoadSensitivity);
        lateralEfficiency = Mth.clamp(lateralEfficiency, 0.78f, 1.0f);

        this.frontEffectiveLoad = this.frontLoadRatio * lateralEfficiency;
        this.rearEffectiveLoad = this.rearLoadRatio * lateralEfficiency;
    }
}
