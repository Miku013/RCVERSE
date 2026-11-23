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

        // 法線から最適な配置回転角（Yaw, Pitch, Roll）を逆算
        Vector3f n = closestFace.localNormal;
        float rotY = 0.0f;
        float rotX = 0.0f;
        float rotZ = 0.0f;

        if (Math.abs(n.y) > 0.85f) {
            // 上面 (ルーフ/ボンネット) または下面
            rotX = n.y > 0 ? -90.0f : 90.0f;
            rotY = 0.0f;
        } else if (Math.abs(n.x) > 0.85f) {
            // 左右ドア面
            rotY = n.x > 0 ? 90.0f : -90.0f;
            rotX = 0.0f;
        } else if (Math.abs(n.z) > 0.85f) {
            // 前後面
            rotY = n.z > 0 ? 0.0f : 180.0f;
            rotX = 0.0f;
        } else {
            // 斜めブロック（シル等）の法線に沿った回転角
            rotY = (float) Math.toDegrees(Math.atan2(n.x, n.z));
            rotX = (float) Math.toDegrees(-Math.asin(Mth.clamp(n.y, -1.0f, 1.0f)));
        }

        return new RayHitResult(closestFace, closestHitPoint, closestDist, rotY, rotX, rotZ);
    }

    private static Float rayIntersectsTriangle(Vector3f rayOrigin, Vector3f rayDir,
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

    /**
     * 【第1段階：対象面範囲外のクリッピングメッシュ生成】
     * ステッカーがキューブ面からはみ出た場合、面の矩形境界で綺麗に切り落とす
     */
    public static List<ClippedVertex> getClippedDecalQuad(CarDecalData decal, ModelCubeFace face) {
        // ステッカーの基本サイズ
        float aspect = decal.getAspect() > 0 ? decal.getAspect() : 1.0f;
        float halfW = (decal.getScale() * 0.5f) * aspect;
        float halfH = (decal.getScale() * 0.5f);

        // 面のローカル2D空間において、ステッカー中心位置を投影
        Vector3f decalPos = new Vector3f(decal.getPosX(), decal.getPosY(), decal.getPosZ());
        Vector3f relPos = new Vector3f(decalPos).sub(face.vertices[0]);
        float centerU = relPos.dot(face.uAxis);
        float centerV = relPos.dot(face.vAxis);

        // ステッカーの面ローカル2DでのAABB
        float sMinU = centerU - halfW;
        float sMaxU = centerU + halfW;
        float sMinV = centerV - halfH;
        float sMaxV = centerV + halfH;

        // 面のAABB: [0, face.width] x [0, face.height]
        float cMinU = Math.max(0.0f, sMinU);
        float cMaxU = Math.min(face.width, sMaxU);
        float cMinV = Math.max(0.0f, sMinV);
        float cMaxV = Math.min(face.height, sMaxV);

        // 完全に面の外側にある場合は描画しない
        if (cMinU >= cMaxU || cMinV >= cMaxV) {
            return Collections.emptyList();
        }

        // クリッピング後の4頂点におけるステッカーUVを逆算
        // u = (curU - sMinU) / (sMaxU - sMinU)
        float uRange = sMaxU - sMinU;
        float vRange = sMaxV - sMinV;
        if (uRange <= 0.001f || vRange <= 0.001f) return Collections.emptyList();

        float uvU0 = (cMinU - sMinU) / uRange;
        float uvU1 = (cMaxU - sMinU) / uRange;
        float uvV0 = (cMinV - sMinV) / vRange;
        float uvV1 = (cMaxV - sMinV) / vRange;

        if (decal.isMirrorX()) {
            float temp = uvU0;
            uvU0 = 1.0f - uvU1;
            uvU1 = 1.0f - temp;
        }

        // 面上の3D頂点座標を復元
        Vector3f p0 = new Vector3f(face.vertices[0]).add(new Vector3f(face.uAxis).mul(cMinU)).add(new Vector3f(face.vAxis).mul(cMinV));
        Vector3f p1 = new Vector3f(face.vertices[0]).add(new Vector3f(face.uAxis).mul(cMaxU)).add(new Vector3f(face.vAxis).mul(cMinV));
        Vector3f p2 = new Vector3f(face.vertices[0]).add(new Vector3f(face.uAxis).mul(cMaxU)).add(new Vector3f(face.vAxis).mul(cMaxV));
        Vector3f p3 = new Vector3f(face.vertices[0]).add(new Vector3f(face.uAxis).mul(cMinU)).add(new Vector3f(face.vAxis).mul(cMaxV));

        List<ClippedVertex> result = new ArrayList<>(4);
        result.add(new ClippedVertex(p0, uvU0, uvV0));
        result.add(new ClippedVertex(p1, uvU1, uvV0));
        result.add(new ClippedVertex(p2, uvU1, uvV1));
        result.add(new ClippedVertex(p3, uvU0, uvV1));
        return result;
    }
}
