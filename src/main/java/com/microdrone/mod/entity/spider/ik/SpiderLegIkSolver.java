package com.microdrone.mod.entity.spider.ik;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 多脚ロボット用 2リンク幾何学的3D逆運動学（Analytical IK）ソルバー & 遊脚軌道エンジン
 * 
 * - 股関節 (Hip) -> 膝関節 (Knee) -> 足先 (Foot) の2リンク構造の幾何学的解法
 * - 多方向レイキャストによる段差・障害物検出
 * - 接地期（Stance）と遊脚期（Swing）の高度な状態遷移と放物線軌道補間
 */
public class SpiderLegIkSolver {

    /**
     * 歩行フェーズ状態
     */
    public enum GaitState {
        STANCE, // 接地期: 足先は地面に固定され、車体を支持・推進
        SWING   // 遊脚期: 足先を空中に持ち上げ、次の接地目標へ運ぶ
    }

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

    /**
     * 多方向レイキャストによる接地位置検出
     * 1. 真下の地面レイキャスト
     * 2. 移動方向・進行方向への前方斜めレイキャスト（段差・階段・壁のエッジ検出）
     * 
     * @param level ワールド
     * @param idealWorldPos 脚の理想接地基準座標
     * @param bodyPos 車体中心座標
     * @param moveDir 車体の移動方向ベクトル (正規化または速度)
     * @param scale 機体スケール倍率
     * @param entity 対象エンティティ
     * @return 最適な接地ワールド座標
     */
    public static Vec3 sampleGroundMultiRay(Level level, Vec3 idealWorldPos, Vec3 bodyPos, Vec3 moveDir, float scale, net.minecraft.world.entity.Entity entity) {
        // 1. 直下レイキャスト（基準となる地面高さ）
        Vec3 downStart = new Vec3(idealWorldPos.x, bodyPos.y + (1.2 * scale), idealWorldPos.z);
        Vec3 downEnd = new Vec3(idealWorldPos.x, bodyPos.y - (1.6 * scale), idealWorldPos.z);
        BlockHitResult downHit = level.clip(new ClipContext(downStart, downEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));

        double baseGroundY = (downHit.getType() == HitResult.Type.BLOCK) ? downHit.getLocation().y : bodyPos.y;
        Vec3 bestPos = new Vec3(idealWorldPos.x, baseGroundY, idealWorldPos.z);

        // 2. 移動中・前進時の斜め前方レイキャスト（段差上面・エッジ検出）
        if (moveDir.lengthSqr() > 0.001) {
            Vec3 dirNorm = moveDir.normalize();
            double forwardOffset = 0.35 * scale;
            Vec3 forwardStart = new Vec3(
                    idealWorldPos.x + dirNorm.x * forwardOffset,
                    bodyPos.y + (1.2 * scale),
                    idealWorldPos.z + dirNorm.z * forwardOffset
            );
            Vec3 forwardEnd = new Vec3(
                    idealWorldPos.x + dirNorm.x * forwardOffset,
                    bodyPos.y - (1.6 * scale),
                    idealWorldPos.z + dirNorm.z * forwardOffset
            );

            BlockHitResult fwdHit = level.clip(new ClipContext(forwardStart, forwardEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
            if (fwdHit.getType() == HitResult.Type.BLOCK) {
                double fwdGroundY = fwdHit.getLocation().y;
                // 段差を登る場合（前方が一段高い場合）、その高さを優先採用
                if (fwdGroundY > baseGroundY && (fwdGroundY - baseGroundY) <= (1.1 * scale)) {
                    bestPos = new Vec3(fwdHit.getLocation().x, fwdGroundY, fwdHit.getLocation().z);
                }
            }
        }

        return bestPos;
    }

    /**
     * 遊脚期（Swing）の滑らかな3次元ステップ放物線軌道補間
     * 段差を跨ぐ際に障害物に衝突しないよう、放物線ピークを制御します。
     * 
     * @param start 踏み出し開始位置
     * @param target 目標接地位置
     * @param progress ステップ進行度 (0.0 〜 1.0)
     * @param stepHeight 持ち上げ高さ (スケール適用済み)
     * @return 補間された現在フレームのワールド座標
     */
    public static Vec3 computeStepTrajectory(Vec3 start, Vec3 target, float progress, double stepHeight) {
        float t = Mth.clamp(progress, 0.0f, 1.0f);

        // Smoothstep による水平移動の緩急（出だしと着地が滑らか）
        float s = t * t * (3.0f - 2.0f * t);
        Vec3 horizLerp = start.lerp(target, s);

        // 段差上昇時の補正: 目標が開始点より高い場合、山なりの頂点を少し前方にバイアス
        double heightDelta = target.y - start.y;
        double liftCurve = Math.sin(t * Math.PI);
        if (heightDelta > 0.05) {
            // 上り段差: 足をより高く持ち上げて引っかかりを防止
            liftCurve = Math.pow(Math.sin(t * Math.PI), 0.75);
        }

        double currentLift = liftCurve * stepHeight;
        double currentY = Mth.lerp(s, start.y, target.y) + currentLift;

        return new Vec3(horizLerp.x, currentY, horizLerp.z);
    }
}
