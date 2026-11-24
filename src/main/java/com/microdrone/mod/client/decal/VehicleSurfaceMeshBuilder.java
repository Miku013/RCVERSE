package com.microdrone.mod.client.decal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 【B方式：車体表面メッシュ化システム】
 * GeckoLibモデルの全キューブから内部面・接触面を除去し、
 * 外側に露出している表面メッシュ（SurfacePolygon）を統合構築・キャッシュするビルダークラス。
 */
public class VehicleSurfaceMeshBuilder {

    /**
     * 車体表面の1ポリゴン（四角形または三角形）
     */
    public static class SurfacePolygon {
        public final String boneName;
        public final int cubeId;
        public final Direction originalDir;
        public final Vector3f[] vertices; // 3または4頂点（反時計回りまたは時計回り）
        public final Vector3f normal;
        public final Vector3f center;
        public final float width;
        public final float height;
        public final float minDimension; // キューブの最小厚み（薄い装甲板判定用）
        public final float minX, maxX, minY, maxY, minZ, maxZ;

        public SurfacePolygon(String boneName, int cubeId, Direction originalDir,
                              Vector3f[] vertices, Vector3f normal, float width, float height, float minDimension) {
            this.boneName = boneName;
            this.cubeId = cubeId;
            this.originalDir = originalDir;
            this.vertices = vertices;
            this.normal = normal;
            this.width = width;
            this.height = height;
            this.minDimension = minDimension;

            // 中心とAABBバウンディングボックスの計算
            float cx = 0, cy = 0, cz = 0;
            float mx = Float.MAX_VALUE, Mx = -Float.MAX_VALUE;
            float my = Float.MAX_VALUE, My = -Float.MAX_VALUE;
            float mz = Float.MAX_VALUE, Mz = -Float.MAX_VALUE;
            for (Vector3f v : vertices) {
                cx += v.x; cy += v.y; cz += v.z;
                if (v.x < mx) mx = v.x; if (v.x > Mx) Mx = v.x;
                if (v.y < my) my = v.y; if (v.y > My) My = v.y;
                if (v.z < mz) mz = v.z; if (v.z > Mz) Mz = v.z;
            }
            this.center = new Vector3f(cx / vertices.length, cy / vertices.length, cz / vertices.length);
            this.minX = mx; this.maxX = Mx;
            this.minY = my; this.maxY = My;
            this.minZ = mz; this.maxZ = Mz;
        }
    }

    /**
     * 車体モデル全体の表面メッシュコンテナ
     */
    public static class VehicleSurfaceMesh {
        public final ResourceLocation modelLocation;
        public final List<SurfacePolygon> allPolygons;
        public final List<SurfacePolygon> bodyPolygons; // 可動パーツ以外の全車体表面ポリゴン
        private final Map<String, List<SurfacePolygon>> bonePolygonMap = new HashMap<>();

        public VehicleSurfaceMesh(ResourceLocation modelLocation, List<SurfacePolygon> polygons) {
            this.modelLocation = modelLocation;
            this.allPolygons = Collections.unmodifiableList(polygons);
            List<SurfacePolygon> bodyList = new ArrayList<>();
            for (SurfacePolygon p : polygons) {
                bonePolygonMap.computeIfAbsent(p.boneName.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(p);
                if (isBodyPartBone(p.boneName)) {
                    bodyList.add(p);
                }
            }
            this.bodyPolygons = Collections.unmodifiableList(bodyList.isEmpty() ? polygons : bodyList);
        }

        public List<SurfacePolygon> getPolygonsForBone(String boneName) {
            if (boneName == null) return bodyPolygons;
            String lower = boneName.toLowerCase(Locale.ROOT);
            if ("body".equals(lower) || "chassis".equals(lower) || "root".equals(lower)) {
                return bodyPolygons;
            }
            List<SurfacePolygon> list = bonePolygonMap.get(lower);
            if (list != null && !list.isEmpty()) {
                return list;
            }
            return bodyPolygons;
        }
    }

    /**
     * 可動パーツ（タイヤ・プロペラ・動翼・砲身・履帯輪等）以外の車体構成ボーンかどうかを判定
     */
    public static boolean isBodyPartBone(String boneName) {
        if (boneName == null) return true;
        String lower = boneName.toLowerCase(Locale.ROOT);
        if (lower.contains("wheel") || lower.contains("steer") || lower.contains("propeller") ||
            lower.contains("rudder") || lower.contains("aileron") || lower.contains("elevator") ||
            lower.contains("cannon") || lower.contains("recoil") || lower.contains("muzzle") ||
            lower.contains("sprocket") || lower.contains("idler") || lower.contains("shock") ||
            lower.contains("arm_") || lower.contains("knuckle") || lower.contains("dive_plane") ||
            lower.contains("motor")) {
            return false;
        }
        return true;
    }

    // モデルごとのメッシュキャッシュ
    private static final Map<ResourceLocation, VehicleSurfaceMesh> MESH_CACHE = new ConcurrentHashMap<>();

    /**
     * キャッシュから車体表面メッシュを取得（未ロード時は自動ビルド）
     */
    public static VehicleSurfaceMesh getSurfaceMesh(ResourceLocation modelLoc) {
        if (modelLoc == null) return null;
        return MESH_CACHE.computeIfAbsent(modelLoc, VehicleSurfaceMeshBuilder::buildSurfaceMesh);
    }

    /**
     * キャッシュの全クリア（モデル再読み込み時用）
     */
    public static void clearCache() {
        MESH_CACHE.clear();
    }

    /**
     * モデルJSONから表面メッシュを構築
     */
    private static VehicleSurfaceMesh buildSurfaceMesh(ResourceLocation modelLoc) {
        List<SurfacePolygon> rawFaces = new ArrayList<>();
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(modelLoc);
            if (res.isEmpty()) {
                com.microdrone.mod.MicroDroneMod.LOGGER.warn("[VehicleSurfaceMeshBuilder] Model not found: {}", modelLoc);
                return new VehicleSurfaceMesh(modelLoc, Collections.emptyList());
            }

            try (InputStreamReader reader = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray geoArray = json.getAsJsonArray("minecraft:geometry");
                if (geoArray == null || geoArray.isEmpty()) {
                    return new VehicleSurfaceMesh(modelLoc, Collections.emptyList());
                }

                JsonObject geo = geoArray.get(0).getAsJsonObject();
                JsonArray bones = geo.getAsJsonArray("bones");
                if (bones == null) {
                    return new VehicleSurfaceMesh(modelLoc, Collections.emptyList());
                }

                // ボーン階層マップの構築とボーン変換行列（累積回転・ピボット）の事前計算
                Map<String, JsonObject> boneMap = new HashMap<>();
                for (JsonElement bElem : bones) {
                    JsonObject b = bElem.getAsJsonObject();
                    if (b.has("name")) {
                        boneMap.put(b.get("name").getAsString(), b);
                    }
                }
                Map<String, Matrix4f> boneMatrixMap = new HashMap<>();
                for (String bName : boneMap.keySet()) {
                    computeBoneMatrix(bName, boneMap, boneMatrixMap);
                }

                for (JsonElement bElem : bones) {
                    JsonObject bone = bElem.getAsJsonObject();
                    String bName = bone.has("name") ? bone.get("name").getAsString() : "unknown";
                    Matrix4f boneMat = boneMatrixMap.getOrDefault(bName, new Matrix4f().identity());
                    parseBoneCubes(bone, boneMat, rawFaces);
                }
            }
        } catch (Exception e) {
            com.microdrone.mod.MicroDroneMod.LOGGER.error("[VehicleSurfaceMeshBuilder] Failed to build surface mesh for {}", modelLoc, e);
            return new VehicleSurfaceMesh(modelLoc, Collections.emptyList());
        }

        // 内部面・接触面（接しているキューブ同士の隠れた面）を判定・除去
        List<SurfacePolygon> filteredFaces = filterInternalFaces(rawFaces);

        com.microdrone.mod.MicroDroneMod.LOGGER.info("[VehicleSurfaceMeshBuilder] Built surface mesh for {}: raw faces={}, visible surface faces={}",
                modelLoc, rawFaces.size(), filteredFaces.size());

        return new VehicleSurfaceMesh(modelLoc, filteredFaces);
    }

    private static void parseBoneCubes(JsonObject bone, Matrix4f boneMat, List<SurfacePolygon> outList) {
        String boneName = bone.has("name") ? bone.get("name").getAsString() : "unknown";
        JsonArray cubes = bone.getAsJsonArray("cubes");
        if (cubes == null || cubes.isEmpty()) return;

        int cubeIndex = 0;
        for (JsonElement cElem : cubes) {
            JsonObject cube = cElem.getAsJsonObject();
            cubeIndex++;

            JsonArray originArr = cube.getAsJsonArray("origin");
            JsonArray sizeArr = cube.getAsJsonArray("size");
            if (originArr == null || sizeArr == null || originArr.size() < 3 || sizeArr.size() < 3) continue;

            float ox = originArr.get(0).getAsFloat();
            float oy = originArr.get(1).getAsFloat();
            float oz = originArr.get(2).getAsFloat();
            float sx = sizeArr.get(0).getAsFloat();
            float sy = sizeArr.get(1).getAsFloat();
            float sz = sizeArr.get(2).getAsFloat();
            float minDim = Math.min(sx, Math.min(sy, sz));

            // キューブローカル変換行列
            Matrix4f cubeLocal = new Matrix4f().identity();
            if (cube.has("pivot") && cube.has("rotation")) {
                JsonArray piv = cube.getAsJsonArray("pivot");
                JsonArray rot = cube.getAsJsonArray("rotation");
                if (piv.size() >= 3 && rot.size() >= 3) {
                    float px = piv.get(0).getAsFloat();
                    float py = piv.get(1).getAsFloat();
                    float pz = piv.get(2).getAsFloat();
                    // Blockbench のオイラー角を JOML rotateZYX に適合:
                    // Pitch(X) と Roll(Z) は符号反転(-)、Yaw(Y) は同一回転方向(+)
                    float rx = (float) Math.toRadians(-rot.get(0).getAsFloat());
                    float ry = (float) Math.toRadians(rot.get(1).getAsFloat());
                    float rz = (float) Math.toRadians(-rot.get(2).getAsFloat());

                    cubeLocal.translate(px, py, pz);
                    cubeLocal.rotateZYX(rz, ry, rx);
                    cubeLocal.translate(-px, -py, -pz);
                }
            }

            // ボーン変換（累積回転・ピボット）と合成
            Matrix4f totalCubeMat = new Matrix4f(boneMat).mul(cubeLocal);

            // キューブの8頂点
            Vector3f c000 = new Vector3f(ox, oy, oz);
            Vector3f c100 = new Vector3f(ox + sx, oy, oz);
            Vector3f c110 = new Vector3f(ox + sx, oy + sy, oz);
            Vector3f c010 = new Vector3f(ox, oy + sy, oz);
            Vector3f c001 = new Vector3f(ox, oy, oz + sz);
            Vector3f c101 = new Vector3f(ox + sx, oy, oz + sz);
            Vector3f c111 = new Vector3f(ox + sx, oy + sy, oz + sz);
            Vector3f c011 = new Vector3f(ox, oy + sy, oz + sz);

            totalCubeMat.transformPosition(c000);
            totalCubeMat.transformPosition(c100);
            totalCubeMat.transformPosition(c110);
            totalCubeMat.transformPosition(c010);
            totalCubeMat.transformPosition(c001);
            totalCubeMat.transformPosition(c101);
            totalCubeMat.transformPosition(c111);
            totalCubeMat.transformPosition(c011);

            // 6面をポリゴンとして追加
            addFace(outList, boneName, cubeIndex, Direction.UP,    c010, c110, c111, c011, sx, sz, minDim, totalCubeMat, Direction.UP.step());
            addFace(outList, boneName, cubeIndex, Direction.DOWN,  c001, c101, c100, c000, sx, sz, minDim, totalCubeMat, Direction.DOWN.step());
            addFace(outList, boneName, cubeIndex, Direction.NORTH, c110, c010, c000, c100, sx, sy, minDim, totalCubeMat, Direction.NORTH.step());
            addFace(outList, boneName, cubeIndex, Direction.SOUTH, c011, c111, c101, c001, sx, sy, minDim, totalCubeMat, Direction.SOUTH.step());
            addFace(outList, boneName, cubeIndex, Direction.WEST,  c010, c011, c001, c000, sz, sy, minDim, totalCubeMat, Direction.WEST.step());
            addFace(outList, boneName, cubeIndex, Direction.EAST,  c111, c110, c100, c101, sz, sy, minDim, totalCubeMat, Direction.EAST.step());
        }
    }

    private static Matrix4f computeBoneMatrix(String name, Map<String, JsonObject> boneMap, Map<String, Matrix4f> cache) {
        if (cache.containsKey(name)) return cache.get(name);
        JsonObject bone = boneMap.get(name);
        if (bone == null) {
            Matrix4f id = new Matrix4f().identity();
            cache.put(name, id);
            return id;
        }

        Matrix4f parentMat = new Matrix4f().identity();
        if (bone.has("parent")) {
            String parentName = bone.get("parent").getAsString();
            if (!parentName.equalsIgnoreCase(name)) {
                parentMat = new Matrix4f(computeBoneMatrix(parentName, boneMap, cache));
            }
        }

        Matrix4f boneMat = new Matrix4f(parentMat);
        if (bone.has("pivot") && bone.has("rotation")) {
            JsonArray piv = bone.getAsJsonArray("pivot");
            JsonArray rot = bone.getAsJsonArray("rotation");
            if (piv.size() >= 3 && rot.size() >= 3) {
                float px = piv.get(0).getAsFloat();
                float py = piv.get(1).getAsFloat();
                float pz = piv.get(2).getAsFloat();
                float rx = (float) Math.toRadians(-rot.get(0).getAsFloat());
                float ry = (float) Math.toRadians(rot.get(1).getAsFloat());
                float rz = (float) Math.toRadians(-rot.get(2).getAsFloat());

                boneMat.translate(px, py, pz);
                boneMat.rotateZYX(rz, ry, rx);
                boneMat.translate(-px, -py, -pz);
            }
        } else if (bone.has("rotation")) {
            JsonArray rot = bone.getAsJsonArray("rotation");
            if (rot.size() >= 3) {
                float rx = (float) Math.toRadians(-rot.get(0).getAsFloat());
                float ry = (float) Math.toRadians(rot.get(1).getAsFloat());
                float rz = (float) Math.toRadians(-rot.get(2).getAsFloat());
                boneMat.rotateZYX(rz, ry, rx);
            }
        }
        cache.put(name, boneMat);
        return boneMat;
    }

    private static void addFace(List<SurfacePolygon> list, String boneName, int cubeId, Direction dir,
                                Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3,
                                float w, float h, float minDim, Matrix4f cubeMat, org.joml.Vector3fc defaultNormal) {
        Vector3f normal = new Vector3f();
        cubeMat.transformDirection(defaultNormal, normal).normalize();
        Vector3f[] verts = new Vector3f[]{p0, p1, p2, p3};
        list.add(new SurfacePolygon(boneName, cubeId, dir, verts, normal, w, h, minDim));
    }

    /**
     * 【内部面・接触面の除去】
     * 完全に接合しているキューブ同士の内面（同じ位置で逆向きの法線を持つ面同士）を除去
     */
    private static List<SurfacePolygon> filterInternalFaces(List<SurfacePolygon> faces) {
        if (faces.size() <= 1) return faces;

        boolean[] isInternal = new boolean[faces.size()];
        float posEpsilon = 0.05f; // 接合判定許容誤差

        for (int i = 0; i < faces.size(); i++) {
            if (isInternal[i]) continue;
            SurfacePolygon a = faces.get(i);

            for (int j = i + 1; j < faces.size(); j++) {
                if (isInternal[j]) continue;
                SurfacePolygon b = faces.get(j);

                // 同一ボーン内かつ法線が真逆（dot ≈ -1.0）
                if (!a.boneName.equalsIgnoreCase(b.boneName)) continue;
                float normalDot = a.normal.dot(b.normal);
                if (normalDot > -0.95f) continue;

                // 中心間の距離が極小
                float centerDistSq = a.center.distanceSquared(b.center);
                if (centerDistSq > 0.1f) continue;

                // AABBがほぼ一致しているかチェック（完全接触面）
                if (Math.abs(a.minX - b.minX) < posEpsilon && Math.abs(a.maxX - b.maxX) < posEpsilon &&
                    Math.abs(a.minY - b.minY) < posEpsilon && Math.abs(a.maxY - b.maxY) < posEpsilon &&
                    Math.abs(a.minZ - b.minZ) < posEpsilon && Math.abs(a.maxZ - b.maxZ) < posEpsilon) {
                    // 両方とも内部面として除外
                    isInternal[i] = true;
                    isInternal[j] = true;
                    break;
                }
            }
        }

        List<SurfacePolygon> result = new ArrayList<>(faces.size());
        for (int i = 0; i < faces.size(); i++) {
            if (!isInternal[i]) {
                result.add(faces.get(i));
            }
        }
        return result;
    }
}
