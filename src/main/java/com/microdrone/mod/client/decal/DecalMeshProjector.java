package com.microdrone.mod.client.decal;

import com.microdrone.mod.decal.CarDecalData;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 【B方式：デカール投影処理クラス】
 * 車体表面メッシュに対してデカールボックス（プロジェクター）から光線を落とし、
 * 車体の凹凸・傾斜・段差に沿ったデカール専用ポリゴンを連続生成する。
 */
public class DecalMeshProjector {

    public static class DecalVertex {
        public final Vector3f pos;
        public final float u;
        public final float v;

        public DecalVertex(Vector3f pos, float u, float v) {
            this.pos = pos;
            this.u = u;
            this.v = v;
        }
    }

    public static class ProjectedDecalPolygon {
        public final List<DecalVertex> vertices; // 3頂点以上の凸多角形
        public final Vector3f normal;
        public final float depth;
        public final VehicleSurfaceMeshBuilder.SurfacePolygon sourcePolygon;

        public ProjectedDecalPolygon(List<DecalVertex> vertices, Vector3f normal, float depth,
                                     VehicleSurfaceMeshBuilder.SurfacePolygon sourcePolygon) {
            this.vertices = vertices;
            this.normal = normal;
            this.depth = depth;
            this.sourcePolygon = sourcePolygon;
        }
    }

    /**
     * 車体表面メッシュにデカールを投影し、描画用ポリゴン群を生成
     */
    public static List<ProjectedDecalPolygon> projectDecal(CarDecalData decal, List<VehicleSurfaceMeshBuilder.SurfacePolygon> polygons) {
        if (polygons == null || polygons.isEmpty() || decal == null) {
            return Collections.emptyList();
        }

        // デカールの幾何学的姿勢・サイズ
        float aspect = decal.getAspect() > 0 ? decal.getAspect() : 1.0f;
        float halfW = (decal.getScale() * 0.5f) * aspect;
        float halfH = (decal.getScale() * 0.5f);

        Vector3f decalPos = new Vector3f(decal.getPosX(), decal.getPosY(), decal.getPosZ());

        // デカールの向きからローカル軸を計算
        Quaternionf decalRot = new Quaternionf()
                .rotateY(decal.getRotY() * Mth.DEG_TO_RAD)
                .rotateX(decal.getRotX() * Mth.DEG_TO_RAD)
                .rotateZ(decal.getRotZ() * Mth.DEG_TO_RAD);

        Vector3f localXAxis = new Vector3f(1, 0, 0);
        Vector3f localYAxis = new Vector3f(0, 1, 0);
        Vector3f decalNormal = new Vector3f(0, 0, 1);
        decalRot.transform(localXAxis);
        decalRot.transform(localYAxis);
        decalRot.transform(decalNormal);

        Vector3f rayDir = new Vector3f(decalNormal).negate().normalize();

        // 投影直方体（デカールボックス）の4つの境界平面を定義（内側を向く法線）
        // 平面方程式: (P - planePoint)・planeNormal >= 0 なら内側
        BoundingPlane leftPlane   = new BoundingPlane(new Vector3f(decalPos).add(new Vector3f(localXAxis).mul(-halfW)), localXAxis);
        BoundingPlane rightPlane  = new BoundingPlane(new Vector3f(decalPos).add(new Vector3f(localXAxis).mul(halfW)),  new Vector3f(localXAxis).negate());
        BoundingPlane bottomPlane = new BoundingPlane(new Vector3f(decalPos).add(new Vector3f(localYAxis).mul(-halfH)), localYAxis);
        BoundingPlane topPlane    = new BoundingPlane(new Vector3f(decalPos).add(new Vector3f(localYAxis).mul(halfH)),  new Vector3f(localYAxis).negate());
        BoundingPlane[] clipPlanes = new BoundingPlane[]{leftPlane, rightPlane, bottomPlane, topPlane};

        List<ProjectedDecalPolygon> candidates = new ArrayList<>();

        for (VehicleSurfaceMeshBuilder.SurfacePolygon poly : polygons) {
            // 1. 背面カリング: デカール方向を向いている面のみ（dot > 0.20）
            float dot = poly.normal.dot(decalNormal);
            if (dot < 0.20f) continue;

            // 1.5. 【薄い装甲板（厚み <= 1.5f）の内側面除外・外側面最優先判定】
            // 砲塔・車体中心から外に向かう法線を持つ面を最優先し、裏側（内側面）への誤吸着を完全に除外
            if (poly.minDimension <= 1.5f) {
                float distSq = poly.center.x * poly.center.x + poly.center.z * poly.center.z;
                if (distSq > 1.0f) {
                    Vector3f outward = new Vector3f(poly.center.x, 0.0f, poly.center.z).normalize();
                    float outwardDot = poly.normal.dot(outward);
                    if (outwardDot < -0.15f) {
                        continue; // 中心を向く内側面（裏側）を除外
                    }
                }
            }

            // 2. 光線到達距離チェック（デカール位置から前後に広がりすぎないよう制限）
            Vector3f toCenter = new Vector3f(poly.center).sub(decalPos);
            float depthAlongRay = toCenter.dot(rayDir);
            if (depthAlongRay < -2.0f || depthAlongRay > 12.0f) continue;

            // 3. 多角形をデカール4境界平面でクリッピング (Sutherland-Hodgman アルゴリズム)
            List<Vector3f> currentVerts = new ArrayList<>();
            for (Vector3f v : poly.vertices) {
                currentVerts.add(new Vector3f(v));
            }

            for (BoundingPlane plane : clipPlanes) {
                currentVerts = plane.clipPolygon(currentVerts);
                if (currentVerts.size() < 3) break;
            }

            if (currentVerts.size() < 3) continue;

            // 4. 切り抜かれた頂点に連続UVを付与
            List<DecalVertex> decalVerts = new ArrayList<>(currentVerts.size());
            for (Vector3f p : currentVerts) {
                Vector3f delta = new Vector3f(p).sub(decalPos);
                float projX = delta.dot(localXAxis);
                float projY = delta.dot(localYAxis);

                float u = (projX / (2.0f * halfW)) + 0.5f;
                float v = 0.5f - (projY / (2.0f * halfH));

                if (decal.isMirrorX()) {
                    u = 1.0f - u;
                }
                decalVerts.add(new DecalVertex(p, u, v));
            }

            float depth = poly.center.dot(decalNormal);
            // 薄い装甲板の外側面は、裏面や奥のパーツよりも確実に最前面として選ばれるように優先度ボーナスを付与
            if (poly.minDimension <= 1.5f) {
                depth += 0.2f;
            }
            candidates.add(new ProjectedDecalPolygon(decalVerts, poly.normal, depth, poly));
        }

        // 5. 最前面ソート（外側・手前にある面を優先）
        candidates.sort((a, b) -> Float.compare(b.depth, a.depth));

        // 6. 遮蔽カリング & 最大投影深度カリング（突き抜け・裏写り防止）
        // 手前の面（ドア外板やシュルツェン等）から一定以上奥（2.5ユニット＝約15cm以上奥）にある面は、
        // 奥のパーツ（砲塔本体や車内パーツ等）への貫通映り込みとして完全に除外！
        List<ProjectedDecalPolygon> visibleList = new ArrayList<>();
        Float frontmostDepth = null;

        for (ProjectedDecalPolygon cand : candidates) {
            if (frontmostDepth == null) {
                frontmostDepth = cand.depth;
                visibleList.add(cand);
                continue;
            }

            // 最前面からの深度差（手前の面からの深さ）
            float depthDiff = frontmostDepth - cand.depth;
            if (depthDiff > 2.5f) {
                continue; // 2.5ユニット以上奥にあるパーツへの映り込みをカット
            }

            visibleList.add(cand);
        }

        return visibleList;
    }

    /**
     * 境界平面とクリッピング用ヘルパークラス
     */
    private static class BoundingPlane {
        final Vector3f point;
        final Vector3f normal;

        BoundingPlane(Vector3f point, Vector3f normal) {
            this.point = point;
            this.normal = normal;
        }

        boolean isInside(Vector3f p) {
            return new Vector3f(p).sub(point).dot(normal) >= -0.0001f;
        }

        Vector3f intersect(Vector3f p1, Vector3f p2) {
            Vector3f dir = new Vector3f(p2).sub(p1);
            float denom = dir.dot(normal);
            if (Math.abs(denom) < 1e-6f) return new Vector3f(p1);
            float t = new Vector3f(point).sub(p1).dot(normal) / denom;
            t = Math.max(0.0f, Math.min(1.0f, t));
            return new Vector3f(p1).add(new Vector3f(dir).mul(t));
        }

        List<Vector3f> clipPolygon(List<Vector3f> inVerts) {
            List<Vector3f> outVerts = new ArrayList<>();
            if (inVerts.isEmpty()) return outVerts;

            Vector3f prev = inVerts.get(inVerts.size() - 1);
            boolean prevInside = isInside(prev);

            for (Vector3f curr : inVerts) {
                boolean currInside = isInside(curr);
                if (currInside) {
                    if (!prevInside) {
                        outVerts.add(intersect(prev, curr));
                    }
                    outVerts.add(new Vector3f(curr));
                } else if (prevInside) {
                    outVerts.add(intersect(prev, curr));
                }
                prev = curr;
                prevInside = currInside;
            }
            return outVerts;
        }
    }
}
