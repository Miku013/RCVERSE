package com.microdrone.mod.client.decal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.decal.CarDecalData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 【新設計：専用デカールメッシュ方式 第1段階】
 * Blockbenchモデルの単一キューブ面を検出し、その平面に正確にステッカーを配置・クリッピングするヘルパー
 */
public class GeoCubeFaceRaycaster {

    public static class ModelCubeFace {
        public final String boneName;
        public final int cubeIndex;
        public final Direction direction;
        public final Vector3f localNormal = new Vector3f();
        public final Vector3f localCenter = new Vector3f();
        public final Vector3f uAxis = new Vector3f(); // 面の横方向ローカル軸
        public final Vector3f vAxis = new Vector3f(); // 面の縦方向ローカル軸
        public final float width;
        public final float height;
        public final Vector3f[] vertices = new Vector3f[4]; // p0, p1, p2, p3 (CCW)

        public ModelCubeFace(String boneName, int cubeIndex, Direction dir,
                             Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3,
                             Vector3f normal, float width, float height) {
            this.boneName = boneName;
            this.cubeIndex = cubeIndex;
            this.direction = dir;
            this.vertices[0] = p0;
            this.vertices[1] = p1;
            this.vertices[2] = p2;
            this.vertices[3] = p3;
            this.localNormal.set(normal);
            this.width = width;
            this.height = height;

            // 中心座標
            this.localCenter.set(p0).add(p1).add(p2).add(p3).mul(0.25f);

            // U軸（横方向単位ベクトル）とV軸（縦方向単位ベクトル）
            this.uAxis.set(p1).sub(p0).normalize();
            this.vAxis.set(p3).sub(p0).normalize();
        }
    }

    public static class RayHitResult {
        public final ModelCubeFace hitFace;
        public final Vector3f hitPoint = new Vector3f();
        public final float distance;
        public final float rotY;
        public final float rotX;
        public final float rotZ;

        public RayHitResult(ModelCubeFace face, Vector3f point, float distance, float rotY, float rotX, float rotZ) {
            this.hitFace = face;
            this.hitPoint.set(point);
            this.distance = distance;
            this.rotY = rotY;
            this.rotX = rotX;
            this.rotZ = rotZ;
        }
    }

    public static class ClippedVertex {
        public final Vector3f pos = new Vector3f();
        public final float u;
        public final float v;

        public ClippedVertex(Vector3f pos, float u, float v) {
            this.pos.set(pos);
            this.u = u;
            this.v = v;
        }
    }

    private static final Map<ResourceLocation, List<ModelCubeFace>> CACHED_FACES = new HashMap<>();

    /**
     * モデルのキューブ面リストを取得（初回のみパース＆キャッシュ）
     */
    public static List<ModelCubeFace> getModelFaces(ResourceLocation location) {
        if (location == null) return Collections.emptyList();
        if (CACHED_FACES.containsKey(location)) {
            return CACHED_FACES.get(location);
        }

        List<ModelCubeFace> faces = parseModelFaces(location);
        CACHED_FACES.put(location, faces);
        return faces;
    }

    private static List<ModelCubeFace> parseModelFaces(ResourceLocation location) {
        List<ModelCubeFace> list = new ArrayList<>();
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(location);
            if (res.isEmpty()) return list;

            JsonObject root = JsonParser.parseReader(new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray bones = null;
            if (root.has("minecraft:geometry")) {
                JsonArray geom = root.getAsJsonArray("minecraft:geometry");
                if (!geom.isEmpty() && geom.get(0).getAsJsonObject().has("bones")) {
                    bones = geom.get(0).getAsJsonObject().getAsJsonArray("bones");
                }
            } else if (root.has("bones")) {
                bones = root.getAsJsonArray("bones");
            }
            if (bones == null) return list;

            // 車体ボディ関連ボーンの判定（足回りやタイヤを除外）
            int cubeCounter = 0;
            for (JsonElement bElem : bones) {
                if (!bElem.isJsonObject()) continue;
                JsonObject bObj = bElem.getAsJsonObject();
                String boneName = bObj.has("name") ? bObj.get("name").getAsString() : "";
                if (!isEligibleBodyBone(boneName)) continue;

                if (!bObj.has("cubes")) continue;
                JsonArray cubes = bObj.getAsJsonArray("cubes");

                for (JsonElement cElem : cubes) {
                    if (!cElem.isJsonObject()) continue;
                    JsonObject cube = cElem.getAsJsonObject();
                    cubeCounter++;
                    extractCubeFaces(list, boneName, cubeCounter, cube);
                }
            }

            MicroDroneMod.LOGGER.info("[GeoCubeFaceRaycaster] Parsed {} faces for model {}", list.size(), location);
        } catch (Exception e) {
            MicroDroneMod.LOGGER.error("[GeoCubeFaceRaycaster] Failed to parse model {}: {}", location, e.getMessage());
        }
        return list;
    }

    private static boolean isEligibleBodyBone(String boneName) {
        if (boneName == null || boneName.isBlank()) return false;
        String lower = boneName.toLowerCase();
        // 除外対象
        if (lower.startsWith("wheel") || lower.startsWith("tire") ||
            lower.startsWith("steer") || lower.startsWith("arm_") ||
            lower.startsWith("shock_") || lower.startsWith("knuckle_") ||
            lower.startsWith("track_") || lower.startsWith("sprocket_") ||
            lower.startsWith("motor") || lower.startsWith("battery") ||
            lower.startsWith("camera") || lower.startsWith("antenna") ||
            lower.equals("root")) {
            return false;
        }
        return true;
    }

    private static void extractCubeFaces(List<ModelCubeFace> outList, String boneName, int cubeId, JsonObject cube) {
        if (!cube.has("origin") || !cube.has("size")) return;
        JsonArray origArr = cube.getAsJsonArray("origin");
        JsonArray sizeArr = cube.getAsJsonArray("size");
        if (origArr.size() < 3 || sizeArr.size() < 3) return;

        float ox = origArr.get(0).getAsFloat();
        float oy = origArr.get(1).getAsFloat();
        float oz = origArr.get(2).getAsFloat();

        float sx = sizeArr.get(0).getAsFloat();
        float sy = sizeArr.get(1).getAsFloat();
        float sz = sizeArr.get(2).getAsFloat();
        if (sx <= 0 || sy <= 0 || sz <= 0) return;

        float rx = 0, ry = 0, rz = 0;
        float px = 0, py = 0, pz = 0;
        boolean hasRot = false;

        if (cube.has("rotation")) {
            JsonArray rotArr = cube.getAsJsonArray("rotation");
            if (rotArr.size() >= 3) {
                rx = rotArr.get(0).getAsFloat();
                ry = rotArr.get(1).getAsFloat();
                rz = rotArr.get(2).getAsFloat();
                hasRot = true;
            }
        }
        if (cube.has("pivot")) {
            JsonArray pivArr = cube.getAsJsonArray("pivot");
            if (pivArr.size() >= 3) {
                px = pivArr.get(0).getAsFloat();
                py = pivArr.get(1).getAsFloat();
                pz = pivArr.get(2).getAsFloat();
            }
        }

        Matrix4f cubeLocal = new Matrix4f();
        if (hasRot) {
            cubeLocal.translate(px, py, pz);
            cubeLocal.rotateZYX(rz * Mth.DEG_TO_RAD, ry * Mth.DEG_TO_RAD, rx * Mth.DEG_TO_RAD);
            cubeLocal.translate(-px, -py, -pz);
        }

        Vector3f c000 = new Vector3f(ox, oy, oz);
        Vector3f c100 = new Vector3f(ox + sx, oy, oz);
        Vector3f c110 = new Vector3f(ox + sx, oy + sy, oz);
        Vector3f c010 = new Vector3f(ox, oy + sy, oz);
        Vector3f c001 = new Vector3f(ox, oy, oz + sz);
        Vector3f c101 = new Vector3f(ox + sx, oy, oz + sz);
        Vector3f c111 = new Vector3f(ox + sx, oy + sy, oz + sz);
        Vector3f c011 = new Vector3f(ox, oy + sy, oz + sz);

        cubeLocal.transformPosition(c000);
        cubeLocal.transformPosition(c100);
        cubeLocal.transformPosition(c110);
        cubeLocal.transformPosition(c010);
        cubeLocal.transformPosition(c001);
        cubeLocal.transformPosition(c101);
        cubeLocal.transformPosition(c111);
        cubeLocal.transformPosition(c011);

        // 6面を抽出（法線計算含む）
        addFace(outList, boneName, cubeId, Direction.UP, c010, c110, c111, c011, sx, sz, cubeLocal, Direction.UP.step());
        addFace(outList, boneName, cubeId, Direction.DOWN, c001, c101, c100, c000, sx, sz, cubeLocal, Direction.DOWN.step());
        addFace(outList, boneName, cubeId, Direction.NORTH, c110, c010, c000, c100, sx, sy, cubeLocal, Direction.NORTH.step());
        addFace(outList, boneName, cubeId, Direction.SOUTH, c011, c111, c101, c001, sx, sy, cubeLocal, Direction.SOUTH.step());
        addFace(outList, boneName, cubeId, Direction.WEST, c010, c011, c001, c000, sz, sy, cubeLocal, Direction.WEST.step());
        addFace(outList, boneName, cubeId, Direction.EAST, c111, c110, c100, c101, sz, sy, cubeLocal, Direction.EAST.step());
    }

    private static void addFace(List<ModelCubeFace> list, String boneName, int cubeId, Direction dir,
                                Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3,
                                float w, float h, Matrix4f cubeMat, org.joml.Vector3fc defaultNormal) {
        Vector3f normal = new Vector3f();
        cubeMat.transformDirection(defaultNormal, normal).normalize();
        list.add(new ModelCubeFace(boneName, cubeId, dir, p0, p1, p2, p3, normal, w, h));
    }

    /**
     * カメラレイとボーンローカルキューブ面との交差判定を行い、最も手前の面を取得
     */
    public static RayHitResult raycast(ResourceLocation modelLoc, Vector3f rayOrigin, Vector3f rayDir, String preferredBone) {
        List<ModelCubeFace> faces = getModelFaces(modelLoc);
        if (faces.isEmpty()) return null;

        ModelCubeFace closestFace = null;
        float closestDist = Float.MAX_VALUE;
        Vector3f closestHitPoint = new Vector3f();

        for (ModelCubeFace face : faces) {
            // ボーン指定がある場合は優先
            if (preferredBone != null && !preferredBone.isBlank() && !face.boneName.equalsIgnoreCase(preferredBone)) {
                // body / chassis の互換
                if (!("body".equalsIgnoreCase(preferredBone) || "chassis".equalsIgnoreCase(preferredBone)) ||
                    !("body".equalsIgnoreCase(face.boneName) || "chassis".equalsIgnoreCase(face.boneName))) {
                    continue;
                }
            }

            // 面の法線とレイの内積（手前を向いている面のみ交差判定）
            float dot = face.localNormal.dot(rayDir);
            if (dot >= -0.001f) continue; // カメラに背を向けている面は除外

            // 2つの三角形 (p0, p1, p2) と (p0, p2, p3) で判定
            Float t1 = rayIntersectsTriangle(rayOrigin, rayDir, face.vertices[0], face.vertices[1], face.vertices[2]);
            Float t2 = rayIntersectsTriangle(rayOrigin, rayDir, face.vertices[0], face.vertices[2], face.vertices[3]);

            Float hitT = null;
            if (t1 != null && t2 != null) hitT = Math.min(t1, t2);
            else if (t1 != null) hitT = t1;
            else if (t2 != null) hitT = t2;

            if (hitT != null && hitT > 0 && hitT < closestDist) {
                closestDist = hitT;
                closestFace = face;
                closestHitPoint.set(rayDir).mul(hitT).add(rayOrigin);
            }
        }

        if (closestFace == null) return null;

        // 【第2段階：回転キューブの法線・軸から完全なYaw/Pitch/Rollオイラー角を逆算】
        Vector3f angles = computeFaceAngles(closestFace);
        return new RayHitResult(closestFace, closestHitPoint, closestDist, angles.y, angles.x, angles.z);
    }

    /**
     * 【画像垂直レイキャスト】
     * デカール位置から車体に向かって垂直にレイを飛ばし、
     * 最初に当たった（最前面の）キューブ面の交点と面情報を取得する
     */
    public static RayHitResult raycastFromDecal(ResourceLocation modelLoc, Vector3f decalPos, Vector3f decalNormal, String preferredBone) {
        List<ModelCubeFace> faces = getModelFaces(modelLoc);
        if (faces.isEmpty()) return null;

        Vector3f rayDir = new Vector3f(decalNormal).negate().normalize();
        // デカール位置より手前（外側）から車体内部へレイを照射
        Vector3f rayOrigin = new Vector3f(decalPos).add(new Vector3f(decalNormal).mul(15.0f));

        ModelCubeFace closestFace = null;
        float closestDist = Float.MAX_VALUE;
        Vector3f closestHitPoint = new Vector3f();

        for (ModelCubeFace face : faces) {
            if (preferredBone != null && !preferredBone.isBlank() && !face.boneName.equalsIgnoreCase(preferredBone)) {
                if (!("body".equalsIgnoreCase(preferredBone) || "chassis".equalsIgnoreCase(preferredBone)) ||
                    !("body".equalsIgnoreCase(face.boneName) || "chassis".equalsIgnoreCase(face.boneName))) {
                    continue;
                }
            }

            // 面がデカール側を向いているか（法線の内積 > 0.25）
            float dot = face.localNormal.dot(decalNormal);
            if (dot < 0.25f) continue;

            Float t1 = rayIntersectsTriangle(rayOrigin, rayDir, face.vertices[0], face.vertices[1], face.vertices[2]);
            Float t2 = rayIntersectsTriangle(rayOrigin, rayDir, face.vertices[0], face.vertices[2], face.vertices[3]);

            Float hitT = null;
            if (t1 != null && t2 != null) hitT = Math.min(t1, t2);
            else if (t1 != null) hitT = t1;
            else if (t2 != null) hitT = t2;

            if (hitT != null && hitT > 0 && hitT < closestDist) {
                closestDist = hitT;
                closestFace = face;
                closestHitPoint.set(rayDir).mul(hitT).add(rayOrigin);
            }
        }

        if (closestFace == null) return null;

        Vector3f angles = computeFaceAngles(closestFace);
        return new RayHitResult(closestFace, closestHitPoint, closestDist, angles.y, angles.x, angles.z);
    }

    /**
     * 【第2段階：面の法線と基準軸からオイラー角（rotX: Pitch, rotY: Yaw, rotZ: Roll）を正確に計算】
     * 12.5°傾いたシルや27.5°傾いたボンネットなど、あらゆる傾斜・回転キューブにピタリと追従
     */
    public static Vector3f computeFaceAngles(ModelCubeFace face) {
        Vector3f zAxis = new Vector3f(face.localNormal).normalize();
        Vector3f yAxis = new Vector3f(face.vAxis).negate().normalize(); // 面の上方向
        Vector3f xAxis = new Vector3f(yAxis).cross(zAxis).normalize();
        // yAxis を直交補正
        yAxis.set(zAxis).cross(xAxis).normalize();

        // 回転行列 R = [xAxis, yAxis, zAxis] から R = Ry(rotY) * Rx(rotX) * Rz(rotZ) を分解
        // r00 = xAxis.x, r10 = xAxis.y, r20 = xAxis.z
        // r01 = yAxis.x, r11 = yAxis.y, r21 = yAxis.z
        // r02 = zAxis.x, r12 = zAxis.y, r22 = zAxis.z

        float r12 = zAxis.y;
        float rotX = (float) Math.toDegrees(-Math.asin(Mth.clamp(r12, -1.0f, 1.0f)));

        float rotY;
        float rotZ;
        float cosPitch = (float) Math.cos(Math.toRadians(rotX));

        if (Math.abs(cosPitch) > 0.005f) {
            rotY = (float) Math.toDegrees(Math.atan2(zAxis.x, zAxis.z));
            rotZ = (float) Math.toDegrees(Math.atan2(xAxis.y, yAxis.y));
        } else {
            // ジンバルロック（真上または真下向き）
            rotY = (float) Math.toDegrees(Math.atan2(-xAxis.z, xAxis.x));
            rotZ = 0.0f;
        }

        return new Vector3f(rotX, rotY, rotZ);
    }

    public static Float rayIntersectsTriangle(Vector3f rayOrigin, Vector3f rayDir,
                                               Vector3f v0, Vector3f v1, Vector3f v2) {
        final float EPSILON = 0.000001f;
        Vector3f edge1 = new Vector3f(v1).sub(v0);
        Vector3f edge2 = new Vector3f(v2).sub(v0);
        Vector3f h = new Vector3f(rayDir).cross(edge2);
        float a = edge1.dot(h);
        if (a > -EPSILON && a < EPSILON) return null;

        float f = 1.0f / a;
        Vector3f s = new Vector3f(rayOrigin).sub(v0);
        float u = f * s.dot(h);
        if (u < 0.0f || u > 1.0f) return null;

        Vector3f q = new Vector3f(s).cross(edge1);
        float v = f * rayDir.dot(q);
        if (v < 0.0f || u + v > 1.0f) return null;

        float t = f * edge2.dot(q);
        if (t > EPSILON) return t;
        return null;
    }

    public static class ClippedQuadResult {
        public final List<ClippedVertex> vertices;
        public final String rejectReason;
        public final float minU, maxU, minV, maxV;
        public final float cMinU, cMaxU, cMinV, cMaxV;
        public final float denom;

        public ClippedQuadResult(List<ClippedVertex> vertices, String rejectReason,
                                 float minU, float maxU, float minV, float maxV,
                                 float cMinU, float cMaxU, float cMinV, float cMaxV, float denom) {
            this.vertices = vertices;
            this.rejectReason = rejectReason;
            this.minU = minU;
            this.maxU = maxU;
            this.minV = minV;
            this.maxV = maxV;
            this.cMinU = cMinU;
            this.cMaxU = cMaxU;
            this.cMinV = cMinV;
            this.cMaxV = cMaxV;
            this.denom = denom;
        }

        public boolean isAccepted() {
            return rejectReason == null && !vertices.isEmpty();
        }
    }

    /**
     * 【第4段階：複数面またぎ対応・対象面範囲外のクリッピングメッシュ生成】
     * デカールの回転・姿勢を考慮して面の境界で正確にクリッピングし、
     * 3D空間プロジェクションによってUVを連続一致させる
     */
    public static List<ClippedVertex> getClippedDecalQuad(CarDecalData decal, ModelCubeFace face) {
        return getClippedDecalQuadDetailed(decal, face).vertices;
    }

    /**
     * 面に対するデカール投影・クリッピングの詳細計算（診断用・詳細理由付き）
     */
    public static ClippedQuadResult getClippedDecalQuadDetailed(CarDecalData decal, ModelCubeFace face) {
        // ステッカーの基本サイズ
        float aspect = decal.getAspect() > 0 ? decal.getAspect() : 1.0f;
        float halfW = (decal.getScale() * 0.5f) * aspect;
        float halfH = (decal.getScale() * 0.5f);

        Vector3f decalPos = new Vector3f(decal.getPosX(), decal.getPosY(), decal.getPosZ());

        // デカールの3Dローカル軸を計算
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

        // デカール裏面から車体に向かう垂直プロジェクション光線の向き
        Vector3f rayDir = new Vector3f(decalNormal).negate().normalize();
        float denom = rayDir.dot(face.localNormal);
        if (Math.abs(denom) < 0.0001f) {
            return new ClippedQuadResult(Collections.emptyList(), "denom ~ 0 (ray parallel to face plane)", 0, 0, 0, 0, 0, 0, 0, 0, denom);
        }

        // デカール矩形の4隅の3Dワールド座標（ボーンローカル座標系）
        Vector3f c0 = new Vector3f(decalPos).add(new Vector3f(localXAxis).mul(-halfW)).add(new Vector3f(localYAxis).mul(halfH));
        Vector3f c1 = new Vector3f(decalPos).add(new Vector3f(localXAxis).mul(halfW)).add(new Vector3f(localYAxis).mul(halfH));
        Vector3f c2 = new Vector3f(decalPos).add(new Vector3f(localXAxis).mul(halfW)).add(new Vector3f(localYAxis).mul(-halfH));
        Vector3f c3 = new Vector3f(decalPos).add(new Vector3f(localXAxis).mul(-halfW)).add(new Vector3f(localYAxis).mul(-halfH));

        // 4隅から画像の裏面に向かって直角にレイを飛ばし、面の無限平面との正確な交点を算出
        Vector3f[] corners = {c0, c1, c2, c3};
        float minU = Float.MAX_VALUE;
        float maxU = -Float.MAX_VALUE;
        float minV = Float.MAX_VALUE;
        float maxV = -Float.MAX_VALUE;

        for (Vector3f corner : corners) {
            Vector3f v0ToCorner = new Vector3f(face.vertices[0]).sub(corner);
            float t = v0ToCorner.dot(face.localNormal) / denom;
            Vector3f hitPointOnPlane = new Vector3f(corner).add(new Vector3f(rayDir).mul(t));

            Vector3f rel = new Vector3f(hitPointOnPlane).sub(face.vertices[0]);
            float u = rel.dot(face.uAxis);
            float v = rel.dot(face.vAxis);
            if (u < minU) minU = u;
            if (u > maxU) maxU = u;
            if (v < minV) minV = v;
            if (v > maxV) maxV = v;
        }

        // 面の境界 [0, face.width] x [0, face.height] との交差（クリッピング）範囲
        float cMinU = Math.max(0.0f, minU);
        float cMaxU = Math.min(face.width, maxU);
        float cMinV = Math.max(0.0f, minV);
        float cMaxV = Math.min(face.height, maxV);

        // 完全に面の外側にある場合は描画しない
        if (cMinU >= cMaxU) {
            return new ClippedQuadResult(Collections.emptyList(),
                    String.format("U outside bounds: cMinU(%.2f) >= cMaxU(%.2f) [face.w=%.2f, U=[%.2f, %.2f]]", cMinU, cMaxU, face.width, minU, maxU),
                    minU, maxU, minV, maxV, cMinU, cMaxU, cMinV, cMaxV, denom);
        }
        if (cMinV >= cMaxV) {
            return new ClippedQuadResult(Collections.emptyList(),
                    String.format("V outside bounds: cMinV(%.2f) >= cMaxV(%.2f) [face.h=%.2f, V=[%.2f, %.2f]]", cMinV, cMaxV, face.height, minV, maxV),
                    minU, maxU, minV, maxV, cMinU, cMaxU, cMinV, cMaxV, denom);
        }

        // 面上のクリッピングされた4頂点を計算
        Vector3f p0 = new Vector3f(face.vertices[0]).add(new Vector3f(face.uAxis).mul(cMinU)).add(new Vector3f(face.vAxis).mul(cMinV));
        Vector3f p1 = new Vector3f(face.vertices[0]).add(new Vector3f(face.uAxis).mul(cMaxU)).add(new Vector3f(face.vAxis).mul(cMinV));
        Vector3f p2 = new Vector3f(face.vertices[0]).add(new Vector3f(face.uAxis).mul(cMaxU)).add(new Vector3f(face.vAxis).mul(cMaxV));
        Vector3f p3 = new Vector3f(face.vertices[0]).add(new Vector3f(face.uAxis).mul(cMinU)).add(new Vector3f(face.vAxis).mul(cMaxV));

        float uvU0 = computeProjectionU(p0, decalPos, localXAxis, halfW, decal.isMirrorX());
        float uvV0 = computeProjectionV(p0, decalPos, localYAxis, halfH);

        float uvU1 = computeProjectionU(p1, decalPos, localXAxis, halfW, decal.isMirrorX());
        float uvV1 = computeProjectionV(p1, decalPos, localYAxis, halfH);

        float uvU2 = computeProjectionU(p2, decalPos, localXAxis, halfW, decal.isMirrorX());
        float uvV2 = computeProjectionV(p2, decalPos, localYAxis, halfH);

        float uvU3 = computeProjectionU(p3, decalPos, localXAxis, halfW, decal.isMirrorX());
        float uvV3 = computeProjectionV(p3, decalPos, localYAxis, halfH);

        List<ClippedVertex> result = new ArrayList<>(4);
        result.add(new ClippedVertex(p0, uvU0, uvV0));
        result.add(new ClippedVertex(p1, uvU1, uvV1));
        result.add(new ClippedVertex(p2, uvU2, uvV2));
        result.add(new ClippedVertex(p3, uvU3, uvV3));
        return new ClippedQuadResult(result, null, minU, maxU, minV, maxV, cMinU, cMaxU, cMinV, cMaxV, denom);
    }

    private static float computeProjectionU(Vector3f p, Vector3f center, Vector3f xAxis, float halfW, boolean mirrorX) {
        Vector3f delta = new Vector3f(p).sub(center);
        float projX = delta.dot(xAxis);
        float u = (projX / (2.0f * halfW)) + 0.5f;
        return mirrorX ? (1.0f - u) : u;
    }

    private static float computeProjectionV(Vector3f p, Vector3f center, Vector3f yAxis, float halfH) {
        Vector3f delta = new Vector3f(p).sub(center);
        float projY = delta.dot(yAxis);
        return 0.5f - (projY / (2.0f * halfH));
    }
}
