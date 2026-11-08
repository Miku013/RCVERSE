package com.microdrone.mod.block.road;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 道路・ヘアピンカーブ用 3次ベジェ曲線 (Cubic Bezier Curve) 数学計算エンジン
 * 始点・終点の位置と進入・退出方向、道幅、バンク角からスプライン断面頂点列を生成します。
 */
public class RoadSplineGenerator {

    /**
     * スプライン断面サンプル点
     */
    public static class SplineSample {
        public final double t;
        public final Vec3 center;     // 中心座標 (ワールド絶対座標)
        public final Vec3 tangent;    // 進行方向単位接線
        public final Vec3 right;      // 水平横方向単位ベクトル
        public final Vec3 rightBanked;// バンク回転後横方向単位ベクトル
        public final Vec3 upBanked;   // バンク回転後法線(上方向)単位ベクトル
        public final float bankAngle; // 度数
        public final double distSoFar;// 始点からの累積距離 (m)

        public SplineSample(double t, Vec3 center, Vec3 tangent, Vec3 right,
                            Vec3 rightBanked, Vec3 upBanked, float bankAngle, double distSoFar) {
            this.t = t;
            this.center = center;
            this.tangent = tangent;
            this.right = right;
            this.rightBanked = rightBanked;
            this.upBanked = upBanked;
            this.bankAngle = bankAngle;
            this.distSoFar = distSoFar;
        }
    }

    /**
     * 始点と終点の情報からスプラインのサンプル点リストを生成
     */
    public static List<SplineSample> generateSpline(
            BlockPos startPos, Direction startFacing,
            BlockPos endPos, Direction endFacing,
            RoadSplineData data) {

        List<SplineSample> samples = new ArrayList<>();
        int segments = Math.max(8, data.getSegments());

        // 始点 P0 と 終点 P3 (マーカー上面高さ基準: Y + 1.0)
        Vec3 p0 = new Vec3(startPos.getX() + 0.5, startPos.getY() + 1.0, startPos.getZ() + 0.5);
        Vec3 p3 = new Vec3(endPos.getX() + 0.5, endPos.getY() + 1.0, endPos.getZ() + 0.5);

        // 進入方向単位ベクトル T0
        Vec3 t0 = getDirectionVec(startFacing);
        // 退出方向単位ベクトル T3 (終点から道路が抜けていく向き)
        Vec3 t3 = getDirectionVec(endFacing);

        // 制御点距離
        double d = data.getControlPointDistance();
        Vec3 p1 = p0.add(t0.scale(d));
        Vec3 p2 = p3.subtract(t3.scale(d)); // 終点に入る向きなので終点から -T3

        // カーブ全体の曲がる向き (左カーブ / 右カーブ) を判定
        // ベジェ始点から終点へのベクトルと T0 の外積 Y 成分
        Vec3 chord = p3.subtract(p0);
        double crossY = t0.x * chord.z - t0.z * chord.x;
        boolean isTurnRight = crossY > 0.0; // 時計回り (右)
        if (data.getTurnDirection() == 1) isTurnRight = false; // 強制左
        if (data.getTurnDirection() == 2) isTurnRight = true;  // 強制右

        float maxBank = data.getMaxBankAngle();
        float startBank = data.getStartBankAngle();
        float endBank = data.getEndBankAngle();

        double totalDist = 0.0;
        Vec3 prevCenter = p0;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / (double) segments;

            // 3次ベジェ曲線位置: P(t) = (1-t)^3 P0 + 3(1-t)^2 t P1 + 3(1-t) t^2 P2 + t^3 P3
            double u = 1.0 - t;
            double tt = t * t;
            double uu = u * u;
            double uuu = uu * u;
            double ttt = tt * t;

            Vec3 pos = p0.scale(uuu)
                    .add(p1.scale(3.0 * uu * t))
                    .add(p2.scale(3.0 * u * tt))
                    .add(p3.scale(ttt));

            if (i > 0) {
                totalDist += pos.distanceTo(prevCenter);
            }
            prevCenter = pos;

            // 1階微分 (接線ベクトル): P'(t) = 3(1-t)^2 (P1-P0) + 6(1-t)t (P2-P1) + 3t^2 (P3-P2)
            Vec3 dpos = p1.subtract(p0).scale(3.0 * uu)
                    .add(p2.subtract(p1).scale(6.0 * u * t))
                    .add(p3.subtract(p2).scale(3.0 * tt));

            Vec3 tangent = dpos.lengthSqr() > 1e-6 ? dpos.normalize() : t0;

            // 水平横方向単位ベクトル (Right = Tangent x Up)
            Vec3 up = new Vec3(0, 1, 0);
            Vec3 right;
            double hLen = Math.sqrt(tangent.x * tangent.x + tangent.z * tangent.z);
            if (hLen > 1e-4) {
                right = new Vec3(tangent.z / hLen, 0.0, -tangent.x / hLen);
            } else {
                right = new Vec3(1, 0, 0);
            }

            // バンク角計算: 始点バンク〜中間で最大バンク〜終点バンクへ滑らかに正弦補間
            // 右カーブなら右下がり(バンク正)、左カーブなら左下がり(バンク負)
            float bankSign = isTurnRight ? -1.0f : 1.0f;
            float smoothT = (float) Math.sin(Math.PI * t); // 中央で 1.0
            float baseInterpBank = Mth.lerp((float) t, startBank, endBank);
            float curBankDeg = (baseInterpBank + maxBank * smoothT) * bankSign;

            // バンク回転
            double bankRad = Math.toRadians(curBankDeg);
            double cosB = Math.cos(bankRad);
            double sinB = Math.sin(bankRad);

            // Right を Tangent 軸周りに回転 (または Right と Up の平面回転)
            Vec3 rightBanked = right.scale(cosB).add(up.scale(sinB)).normalize();
            Vec3 upBanked = right.scale(-sinB).add(up.scale(cosB)).normalize();

            samples.add(new SplineSample(t, pos, tangent, right, rightBanked, upBanked, curBankDeg, totalDist));
        }

        return samples;
    }

    private static Vec3 getDirectionVec(Direction dir) {
        return switch (dir) {
            case SOUTH -> new Vec3(0, 0, 1);
            case NORTH -> new Vec3(0, 0, -1);
            case EAST  -> new Vec3(1, 0, 0);
            case WEST  -> new Vec3(-1, 0, 0);
            default    -> new Vec3(0, 0, 1);
        };
    }

    /**
     * 道路衝突レイキャスト結果
     */
    public static class RoadHitResult {
        public static final RoadHitResult MISS = new RoadHitResult(false, 0.0, new Vec3(0, 1, 0), new Vec3(0, 0, 1), 0.0, 0.0);

        public final boolean hit;
        public final double groundY;
        public final Vec3 normal;
        public final Vec3 tangent;
        public final double lateralOffset; // 中心線からの横方向オフセット (左が負、右が正)
        public final double progress;      // スプライン進行率 0.0 ~ 1.0

        public RoadHitResult(boolean hit, double groundY, Vec3 normal, Vec3 tangent, double lateralOffset, double progress) {
            this.hit = hit;
            this.groundY = groundY;
            this.normal = normal;
            this.tangent = tangent;
            this.lateralOffset = lateralOffset;
            this.progress = progress;
        }
    }

    /**
     * 車両タイヤ位置 (x, y, z) から、スプライン道路表面の接地高さを高精度サンプリング
     */
    public static RoadHitResult sampleRoadSurface(List<SplineSample> samples, float roadWidth, double x, double y, double z) {
        if (samples == null || samples.size() < 2) return RoadHitResult.MISS;

        double bestDistSq = Double.MAX_VALUE;
        int bestIdx = -1;
        double bestU = 0.0;
        double bestRawU = 0.0;
        Vec3 bestCenter = null;

        // 1. 最も近いセグメントを探索
        for (int i = 0; i < samples.size() - 1; i++) {
            SplineSample s0 = samples.get(i);
            SplineSample s1 = samples.get(i + 1);

            double segDx = s1.center.x - s0.center.x;
            double segDz = s1.center.z - s0.center.z;
            double segLenSq = segDx * segDx + segDz * segDz;
            if (segLenSq < 1e-6) continue;

            double ptDx = x - s0.center.x;
            double ptDz = z - s0.center.z;
            double rawU = (ptDx * segDx + ptDz * segDz) / segLenSq;
            double u = Mth.clamp(rawU, 0.0, 1.0);

            double projX = s0.center.x + u * segDx;
            double projZ = s0.center.z + u * segDz;
            double dSq = (x - projX) * (x - projX) + (z - projZ) * (z - projZ);

            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                bestIdx = i;
                bestU = u;
                bestRawU = rawU;
                bestCenter = new Vec3(projX, Mth.lerp(u, s0.center.y, s1.center.y), projZ);
            }
        }

        if (bestIdx == -1 || bestCenter == null) return RoadHitResult.MISS;

        // 2. 始点より手前、終点より先の境界外判定 (突入前・通過後の浮遊を完全防止)
        SplineSample s0 = samples.get(bestIdx);
        SplineSample s1 = samples.get(bestIdx + 1);
        double segLen = Math.sqrt((s1.center.x - s0.center.x) * (s1.center.x - s0.center.x) + (s1.center.z - s0.center.z) * (s1.center.z - s0.center.z));

        if (bestIdx == 0 && bestRawU < 0.0) {
            double distBeforeStart = -bestRawU * segLen;
            if (distBeforeStart > 0.15) {
                return RoadHitResult.MISS;
            }
        }

        if (bestIdx == samples.size() - 2 && bestRawU > 1.0) {
            double distAfterEnd = (bestRawU - 1.0) * segLen;
            if (distAfterEnd > 0.15) {
                return RoadHitResult.MISS;
            }
        }

        // 3. 断面パラメータの線形補間
        Vec3 rightBanked = s0.rightBanked.lerp(s1.rightBanked, bestU).normalize();
        Vec3 upBanked = s0.upBanked.lerp(s1.upBanked, bestU).normalize();
        Vec3 tangent = s0.tangent.lerp(s1.tangent, bestU).normalize();
        double progress = Mth.lerp(bestU, s0.t, s1.t);

        // 3. 横方向オフセット (中心線からの距離)
        double diffX = x - bestCenter.x;
        double diffZ = z - bestCenter.z;
        double latOffset = diffX * rightBanked.x + diffZ * rightBanked.z;

        // 道幅判定: 縁石の外側マージン (0.25m) まで許容
        double halfWidth = roadWidth * 0.5;
        if (Math.abs(latOffset) > halfWidth + 0.25) {
            return RoadHitResult.MISS;
        }

        // 4. バンク角を考慮した路面表面の高さ計算
        // rightBanked.y がバンクによる傾斜成分
        double roadY = bestCenter.y + latOffset * rightBanked.y;

        // 縁石の上に乗っている場合 (外側0.4m) は縁石の段差 (+0.15m) を加算
        double curbDistFromEdge = halfWidth - Math.abs(latOffset);
        if (curbDistFromEdge < 0.40) {
            double curbFactor = 1.0 - Math.max(0.0, curbDistFromEdge / 0.40);
            roadY += 0.15 * curbFactor;
        }

        // 5. 車両のY座標との許容範囲判定 (路面直上 1.2m 〜 下 0.5m)
        double distY = y - roadY;
        if (distY < -0.50 || distY > 1.20) {
            return RoadHitResult.MISS;
        }

        return new RoadHitResult(true, roadY, upBanked, tangent, latOffset, progress);
    }
}

