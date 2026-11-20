package com.microdrone.mod.entity.spider.ik;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * 多脚ロボット用 2リンク幾何学的3D逆運動学（Analytical IK）ソルバー
 * 
 * 股関節 (Hip) -> 膝関節 (Knee) -> 足先 (Foot) の2リンク構造に対し、
 * 目標足先位置 (Target) に到達するための関節角（Yaw, Pitch, Knee）を余弦定理により高精度・低負荷で算出します。
 */
public class SpiderLegIkSolver {

    /**
     * IK計算結果
     */
    public static class IkResult {
        public final float yawRad;     // 股関節水平旋回（Y軸回転 / 前後スイング）
        public final float pitchRad;   // 股関節上下回転（Z/X軸回転 / 持ち上げ・接地高調整）
        public final float kneeRad;    // 膝関節屈曲（Z/X軸回転 / 膝の曲げ伸ばし）
        public final boolean reachable; // 目標位置が関節の可動範囲内か

        public IkResult(float yawRad, float pitchRad, float kneeRad, boolean reachable) {
            this.yawRad = yawRad;
            this.pitchRad = pitchRad;
            this.kneeRad = kneeRad;
            this.reachable = reachable;
        }

        public static IkResult zero() {
            return new IkResult(0.0f, 0.0f, 0.0f, true);
        }
    }

    /**
     * 2リンク幾何学的IKの計算
     * 
     * @param targetLocal Hip（付け根）を原点 (0,0,0) とした目標足先ローカル座標
     * @param lenUpper 大腿ボーン長 (Hip -> Knee)
     * @param lenLower 下腿ボーン長 (Knee -> Foot)
     * @param isLeft 左脚かどうか（左右の対称鏡像処理用）
     * @return 算出された関節回転角
     */
    public static IkResult solve(Vector3f targetLocal, float lenUpper, float lenLower, boolean isLeft) {
        float tx = targetLocal.x;
        float ty = targetLocal.y;
        float tz = targetLocal.z;

        // 左右対称処理: 右脚の場合はX軸を反転してローカル基準に揃える
        float localX = isLeft ? tx : -tx;

        // 1. 水平旋回角 (Yaw): X-Z 平面における角度
        // モデル基準では外側（X軸正方向）が脚のニュートラル方向
        float yawRad = (float) Math.atan2(tz, Math.max(0.001f, localX));

        // 水平方向距離
        float horizontalDist = (float) Math.sqrt(localX * localX + tz * tz);

        // 3D直線距離
        float totalDist = (float) Math.sqrt(horizontalDist * horizontalDist + ty * ty);

        // 届く範囲の制約（シンギュラリティ・過伸展防止クランプ）
        float minReach = Math.abs(lenUpper - lenLower) + 0.05f;
        float maxReach = (lenUpper + lenLower) - 0.05f;

        boolean reachable = totalDist >= minReach && totalDist <= maxReach;
        float clampedDist = Mth.clamp(totalDist, minReach, maxReach);

        // 2. 余弦定理による関節角の導出
        // cos(alpha) = (L1^2 + D^2 - L2^2) / (2 * L1 * D)
        float cosAlpha = (lenUpper * lenUpper + clampedDist * clampedDist - lenLower * lenLower)
                / (2.0f * lenUpper * clampedDist);
        cosAlpha = Mth.clamp(cosAlpha, -1.0f, 1.0f);
        float alpha = (float) Math.acos(cosAlpha);

        // 股関節のベース仰角 (Base Elevation): 水平線に対する目標ベクトルの仰角
        float baseElevation = (float) Math.atan2(ty, horizontalDist);

        // 股関節ピッチ角（上向き凸の姿勢を維持するため baseElevation + alpha）
        float pitchRad = baseElevation + alpha;

        // 3. 膝関節角 (Knee):
        // cos(gamma) = (L1^2 + L2^2 - D^2) / (2 * L1 * L2)
        float cosGamma = (lenUpper * lenUpper + lenLower * lenLower - clampedDist * clampedDist)
                / (2.0f * lenUpper * lenLower);
        cosGamma = Mth.clamp(cosGamma, -1.0f, 1.0f);
        float gamma = (float) Math.acos(cosGamma);

        // 膝の屈曲角（外角: まっすぐ伸びた状態を0とし、曲げるほどプラス）
        float kneeRad = (float) Math.PI - gamma;

        return new IkResult(yawRad, pitchRad, kneeRad, reachable);
    }

    /**
     * 初期ポーズからの変位（デルタ）に基づく簡易補正計算
     * （既存の歩行アニメーションに地面凹凸の適応差分を加算する際に使用）
     * 
     * @param groundDeltaY 地面高低差 (ブロック単位)
     * @param swingOffset 前後スイング変位
     * @param lenUpper 大腿長
     * @param lenLower 下腿長
     * @param isLeft 左脚かどうか
     */
    public static IkResult solveTerrainAdaptation(float groundDeltaY, float swingOffset, float lenUpper, float lenLower, boolean isLeft) {
        // 標準接地位置における代表的な大腿・下腿の展開角をもとに、
        // 地面の高低差 groundDeltaY に応じた微小回転角（線形＋三角関数補正）を算出
        float heightRatio = groundDeltaY / Math.max(1.0f, (lenUpper + lenLower) * 0.0625f);
        heightRatio = Mth.clamp(heightRatio, -1.0f, 1.0f);

        // 足が上がった時は膝を曲げ、付け根を上げる
        float deltaPitch = heightRatio * 0.45f;
        float deltaKnee = -heightRatio * 0.65f;

        return new IkResult(swingOffset, deltaPitch, deltaKnee, true);
    }
}
