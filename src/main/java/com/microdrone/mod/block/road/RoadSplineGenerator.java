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

        // 進入方向単位ベクトル T0
        Vec3 t0 = getDirectionVec(startFacing);
        // 退出方向単位ベクトル T3 (終点から道路が抜けていく向き)
        Vec3 t3 = getDirectionVec(endFacing);

        // 始点 P0 と 終点 P3 (マーカー上面高さ基準: Y + 1.0)
        // ブロック境界 (1マス全体) を完全にカバーするように、始点は進入側手前へ -0.5m、終点は退出側先へ +0.5m オフセット
        Vec3 p0 = new Vec3(startPos.getX() + 0.5, startPos.getY() + 1.0, startPos.getZ() + 0.5).subtract(t0.scale(0.5));
        Vec3 p3 = new Vec3(endPos.getX() + 0.5, endPos.getY() + 1.0, endPos.getZ() + 0.5).add(t3.scale(0.5));

        // 90°真円近似の黄金比定数 k = 4/3 * (sqrt(2) - 1)
        final double CIRCLE_K = 0.5522847498;

        Vec3 chord = p3.subtract(p0);
        Vec3 chordH = new Vec3(chord.x, 0.0, chord.z);
        double dirDot = t0.dot(t3);

        // 2区間真円半円弧モード（真円フィットON かつ 180°逆向き平行ヘアピン かつ 横幅2m以上）
        boolean useTwoSegmentArc = false;
        Vec3 apexPos = null;
        Vec3 apexTangent = null;
        Vec3 p0_1 = null, p0_2 = null, p1_1 = null, p1_2 = null;

        // 1区間用制御点
        Vec3 p1;
        Vec3 p2;

        if (data.isArcFitMode()) {
            if (dirDot < -0.9) {
                // 180°ヘアピン: t0 に対する前進ズレと横幅を分解し、2つの90°真円ベジェで完全半円弧を生成
                double fwdOffset = chordH.dot(t0);
                Vec3 latVec = chordH.subtract(t0.scale(fwdOffset));
                double latDist = latVec.length();
                if (latDist >= 2.0) {
                    useTwoSegmentArc = true;
                    double radius = latDist * 0.5;
                    apexTangent = latVec.normalize();
                    double apexFwdDist = Math.max(0.0, fwdOffset) + radius;
                    double apexY = (p0.y + p3.y) * 0.5;

                    Vec3 midHoriz = p0.add(latVec.scale(0.5)).add(t0.scale(apexFwdDist));
                    apexPos = new Vec3(midHoriz.x, apexY, midHoriz.z);

                    double dStart = apexPos.subtract(p0).dot(t0);
                    double dMid1 = apexPos.subtract(p0).dot(apexTangent);
                    p0_1 = p0.add(t0.scale(dStart * CIRCLE_K));
                    p0_2 = apexPos.subtract(apexTangent.scale(dMid1 * CIRCLE_K));

                    double dMid2 = p3.subtract(apexPos).dot(apexTangent);
                    double dEnd = p3.subtract(apexPos).dot(t3);
                    p1_1 = apexPos.add(apexTangent.scale(dMid2 * CIRCLE_K));
                    p1_2 = p3.subtract(t3.scale(dEnd * CIRCLE_K));
                }
            }

            if (!useTwoSegmentArc) {
                if (Math.abs(dirDot) < 0.1) {
                    // 90°直交コーナー: 黄金比 0.5523 で誤差 0.027% 未満の真円弧を生成
                    double d0 = chordH.dot(t0);
                    double d3 = chordH.dot(t3);
                    if (d0 > 1.0 && d3 > 1.0) {
                        p1 = p0.add(t0.scale(d0 * CIRCLE_K));
                        p2 = p3.subtract(t3.scale(d3 * CIRCLE_K));
                    } else {
                        double d = data.getControlPointDistance();
                        p1 = p0.add(t0.scale(d));
                        p2 = p3.subtract(t3.scale(d));
                    }
                } else {
                    // 同方向S字/レーンチェンジなど: 弦長に応じた自然な制御点距離
                    double autoDist = Mth.clamp(chord.length() * 0.42, 4.0, 48.0);
                    p1 = p0.add(t0.scale(autoDist));
                    p2 = p3.subtract(t3.scale(autoDist));
                }
            } else {
                p1 = p0_1;
                p2 = p1_2;
            }
        } else {
            // 手動ベジェモード（従来の挙動を完全維持）
            double d = data.getControlPointDistance();
            p1 = p0.add(t0.scale(d));
            p2 = p3.subtract(t3.scale(d)); // 終点に入る向きなので終点から -T3
        }

        // カーブ全体の曲がる向き (左カーブ / 右カーブ) を判定
        // ベジェ始点から終点へのベクトルと T0 の外積 Y 成分
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

            Vec3 pos;
            Vec3 dpos;
            if (useTwoSegmentArc) {
                if (t <= 0.5) {
                    double localT = t * 2.0;
                    pos = evalCubicBezier(p0, p0_1, p0_2, apexPos, localT);
                    dpos = evalCubicBezierDeriv(p0, p0_1, p0_2, apexPos, localT);
                } else {
                    double localT = (t - 0.5) * 2.0;
                    pos = evalCubicBezier(apexPos, p1_1, p1_2, p3, localT);
                    dpos = evalCubicBezierDeriv(apexPos, p1_1, p1_2, p3, localT);
                }
            } else {
                pos = evalCubicBezier(p0, p1, p2, p3, t);
                dpos = evalCubicBezierDeriv(p0, p1, p2, p3, t);
            }

            if (i > 0) {
                totalDist += pos.distanceTo(prevCenter);
            }
            prevCenter = pos;

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

    private static Vec3 evalCubicBezier(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double u = 1.0 - t;
        double tt = t * t;
        double uu = u * u;
        return p0.scale(uu * u)
                .add(p1.scale(3.0 * uu * t))
                .add(p2.scale(3.0 * u * tt))
                .add(p3.scale(tt * t));
    }

    private static Vec3 evalCubicBezierDeriv(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double u = 1.0 - t;
        return p1.subtract(p0).scale(3.0 * u * u)
                .add(p2.subtract(p1).scale(6.0 * u * t))
                .add(p3.subtract(p2).scale(3.0 * t * t));
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

