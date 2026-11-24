package com.microdrone.mod.client.renderer;

import com.microdrone.mod.client.decal.DecalTextureManager;
import com.microdrone.mod.client.decal.GeoCubeFaceRaycaster;
import com.microdrone.mod.decal.CarDecalData;
import com.microdrone.mod.decal.RcDecalHolder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Collections;
import java.util.List;

/**
 * 全RC乗り物（カー、飛行機、ボート、潜水艦、戦車、ドローン）対応の透過ステッカー描画レイヤー
 * 【新設計：専用デカールメッシュ方式 第1段階（単一キューブ面への吸着配置＆境界クリッピング）】
 */
public class RcVehicleDecalLayer<T extends Entity & GeoAnimatable & RcDecalHolder> extends GeoRenderLayer<T> {

    // デカール投影・形状診断ログ出力要求フラグ（エディタからのF4キーやボタン押下でトリガー）
    public static volatile boolean requestDiagnosticsLog = false;

    // 【B方式：車体表面メッシュ化デカール投影システムの有効化フラグ】
    public static boolean USE_SURFACE_MESH_PROJECTOR = true;

    public RcVehicleDecalLayer(GeoEntityRenderer<T> entityRenderer) {
        super(entityRenderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone,
                              RenderType renderType, MultiBufferSource bufferSource,
                              VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {

        List<CarDecalData> decals = animatable.getDecals();
        if (decals == null || decals.isEmpty()) {
            return;
        }

        com.microdrone.mod.client.gui.DecalEditorScreen editor = com.microdrone.mod.client.gui.DecalEditorScreen.activeScreen;
        int selectedIndex = editor != null ? editor.getSelectedDecalIndex() : -1;

        ResourceLocation modelLoc = resolveModelLocation(animatable);
        String boneName = bone.getName();
        List<GeoCubeFaceRaycaster.ModelCubeFace> faces = GeoCubeFaceRaycaster.getModelFaces(modelLoc);

        for (int i = 0; i < decals.size(); i++) {
            CarDecalData decal = decals.get(i);
            if (decal == null || decal.getTextureName() == null || decal.getTextureName().isBlank()) {
                continue;
            }

            boolean isEditorPreview = (editor != null && editor.getPreviewEntity() == animatable);
            boolean isSelected = isEditorPreview && (i == selectedIndex || editor.getCurrentSelectedDecal() == decal);

            // 診断ログ要求のチェック（F4キーでトリガー）
            boolean doDiag = requestDiagnosticsLog && (editor == null || isSelected);
            String repBone = getRepresentativeDecalBone(animatable, faces);
            String target = decal.getTargetBone();
            boolean isResponsible = isBoneResponsibleForDecal(animatable, modelLoc, faces, boneName, target);

            if (doDiag) {
                // モデル内に代表ボーンが存在するか確認
                boolean repBoneExists = faces.stream().anyMatch(f -> f.boneName.equalsIgnoreCase(repBone));
                com.microdrone.mod.MicroDroneMod.LOGGER.info("[RC DECAL DIAG] Bone: '{}', RepBone: '{}' (Exists in model: {}), IsResponsible: {}, Target: '{}'",
                        boneName, repBone, repBoneExists, isResponsible, target);

                if (!repBoneExists) {
                    String warn = String.format("§c[デカール警告] 代表ボーン '%s' がモデル内に存在しません！(車体ボーン一覧: %s)",
                            repBone, faces.stream().map(f -> f.boneName).distinct().toList());
                    com.microdrone.mod.MicroDroneMod.LOGGER.error(warn);
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.gui != null && mc.gui.getChat() != null) {
                        mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(warn));
                    }
                    requestDiagnosticsLog = false;
                }
            }

            // 指定された代表ボーンの描画タイミングでのみ投影（二重描画・重複描画を完全に防止）
            if (!isResponsible) {
                continue;
            }

            ResourceLocation textureLoc = DecalTextureManager.getStickerTexture(decal.getTextureName());
            if (textureLoc == null) {
                if (doDiag) {
                    com.microdrone.mod.MicroDroneMod.LOGGER.warn("[RC DECAL DIAG] Texture not found: {}", decal.getTextureName());
                }
                continue;
            }

            VertexConsumer decalConsumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(textureLoc));

            // エディターで選択中のステッカーの場合、ボーン座標系（デカール配置オフセット直前）の姿勢行列をエディターに共有
            // ※ワールド内の他エンティティがエディタの選択状態を誤認識・横取りしないよう、エディタのプレビュー実体のみに限定
            if (isSelected) {
                editor.updateDecalPoseMatrix(poseStack.last().pose());
            }

            float unitScale = 0.0625f; // Blockbench (1/16 block) -> Minecraft block
            Vector3f decalPos = new Vector3f(decal.getPosX(), decal.getPosY(), decal.getPosZ());

            // デカールの向きから法線を計算
            Quaternionf decalRot = new Quaternionf()
                    .rotateY(decal.getRotY() * Mth.DEG_TO_RAD)
                    .rotateX(decal.getRotX() * Mth.DEG_TO_RAD)
                    .rotateZ(decal.getRotZ() * Mth.DEG_TO_RAD);
            Vector3f decalNormal = new Vector3f(0, 0, 1);
            decalRot.transform(decalNormal);

            Vector3f rayDir = new Vector3f(decalNormal).negate(); // デカールから車体へ向かう垂直プロジェクションレイ

            // =================================================================================
            // 【B方式：車体表面メッシュ化デカール投影処理】
            // =================================================================================
            if (USE_SURFACE_MESH_PROJECTOR) {
                com.microdrone.mod.client.decal.VehicleSurfaceMeshBuilder.VehicleSurfaceMesh mesh =
                        com.microdrone.mod.client.decal.VehicleSurfaceMeshBuilder.getSurfaceMesh(modelLoc);
                List<com.microdrone.mod.client.decal.VehicleSurfaceMeshBuilder.SurfacePolygon> bonePolygons =
                        mesh != null ? mesh.getPolygonsForBone(boneName) : Collections.emptyList();

                List<com.microdrone.mod.client.decal.DecalMeshProjector.ProjectedDecalPolygon> projectedPolys =
                        com.microdrone.mod.client.decal.DecalMeshProjector.projectDecal(decal, bonePolygons);

                if (doDiag) {
                    String diagSummary = String.format("§a[デカール診断] 車種: %s, ボーン: '%s', メッシュポリゴン数: %d, 投影成功面数: %d",
                            animatable.getClass().getSimpleName(), boneName, bonePolygons.size(), projectedPolys.size());
                    com.microdrone.mod.MicroDroneMod.LOGGER.info(diagSummary);
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.gui != null && mc.gui.getChat() != null) {
                        mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(diagSummary));
                        if (projectedPolys.isEmpty()) {
                            mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(
                                    String.format("§c => 警告: 投影面が0面です！(Pos: %.1f, %.1f, %.1f, Rot: %.1f, %.1f, %.1f)",
                                            decal.getPosX(), decal.getPosY(), decal.getPosZ(),
                                            decal.getRotX(), decal.getRotY(), decal.getRotZ())));
                        }
                    }
                    requestDiagnosticsLog = false;
                }



                if (!projectedPolys.isEmpty()) {
                    PoseStack.Pose currentPose = poseStack.last();
                    Matrix4f matrix = currentPose.pose();

                    for (com.microdrone.mod.client.decal.DecalMeshProjector.ProjectedDecalPolygon pp : projectedPolys) {
                        Vector3f fn = pp.normal;
                        // 車体表面に自然に密着しつつ、Z-Fightingを防ぐ最適な法線オフセット（約1.5mm浮きでピタッと吸着）
                        float ox = fn.x * 0.025f;
                        float oy = fn.y * 0.025f;
                        float oz = fn.z * 0.025f;

                        List<com.microdrone.mod.client.decal.DecalMeshProjector.DecalVertex> vList = pp.vertices;

                        if (vList.size() == 4) {
                            com.microdrone.mod.client.decal.DecalMeshProjector.DecalVertex v0 = vList.get(0);
                            com.microdrone.mod.client.decal.DecalMeshProjector.DecalVertex v1 = vList.get(1);
                            com.microdrone.mod.client.decal.DecalMeshProjector.DecalVertex v2 = vList.get(2);
                            com.microdrone.mod.client.decal.DecalMeshProjector.DecalVertex v3 = vList.get(3);

                            // 表向き Quad (CCW: 0 -> 3 -> 2 -> 1)
                            addDecalVertex(decalConsumer, matrix, currentPose, v0, ox, oy, oz, unitScale, packedOverlay, packedLight, fn);
                            addDecalVertex(decalConsumer, matrix, currentPose, v3, ox, oy, oz, unitScale, packedOverlay, packedLight, fn);
                            addDecalVertex(decalConsumer, matrix, currentPose, v2, ox, oy, oz, unitScale, packedOverlay, packedLight, fn);
                            addDecalVertex(decalConsumer, matrix, currentPose, v1, ox, oy, oz, unitScale, packedOverlay, packedLight, fn);

                            // 裏向き Quad (CW: 0 -> 1 -> 2 -> 3)
                            addDecalVertex(decalConsumer, matrix, currentPose, v0, ox, oy, oz, unitScale, packedOverlay, packedLight, fn);
                            addDecalVertex(decalConsumer, matrix, currentPose, v1, ox, oy, oz, unitScale, packedOverlay, packedLight, fn);
                            addDecalVertex(decalConsumer, matrix, currentPose, v2, ox, oy, oz, unitScale, packedOverlay, packedLight, fn);
                            addDecalVertex(decalConsumer, matrix, currentPose, v3, ox, oy, oz, unitScale, packedOverlay, packedLight, fn);
                        } else {
                            // N角形（5角形以上や三角形）のファン分割
                            for (int k = 1; k < vList.size() - 1; k++) {
                                com.microdrone.mod.client.decal.DecalMeshProjector.DecalVertex va = vList.get(0);
                                com.microdrone.mod.client.decal.DecalMeshProjector.DecalVertex vb = vList.get(k);
                                com.microdrone.mod.client.decal.DecalMeshProjector.DecalVertex vc = vList.get(k + 1);

                                // 表面
                                addDecalVertex(decalConsumer, matrix, currentPose, va, ox, oy, oz, unitScale, packedOverlay, packedLight, fn);
                                addDecalVertex(decalConsumer, matrix, currentPose, vb, ox, oy, oz, unitScale, packedOverlay, packedLight, fn);
                                addDecalVertex(decalConsumer, matrix, currentPose, vc, ox, oy, oz, unitScale, packedOverlay, packedLight, fn);
                                addDecalVertex(decalConsumer, matrix, currentPose, vc, ox, oy, oz, unitScale, packedOverlay, packedLight, fn);

                                // 裏面
                                addDecalVertex(decalConsumer, matrix, currentPose, va, ox, oy, oz, unitScale, packedOverlay, packedLight, fn);
                                addDecalVertex(decalConsumer, matrix, currentPose, vc, ox, oy, oz, unitScale, packedOverlay, packedLight, fn);
                                addDecalVertex(decalConsumer, matrix, currentPose, vb, ox, oy, oz, unitScale, packedOverlay, packedLight, fn);
                                addDecalVertex(decalConsumer, matrix, currentPose, vb, ox, oy, oz, unitScale, packedOverlay, packedLight, fn);
                            }
                        }
                    }

                    // 選択枠（セレクションフレーム）の描画
                    renderSelectionFrame(poseStack, bufferSource, decal, unitScale, isSelected, packedOverlay);
                    continue; // B方式で描画完了したため、旧方式をスキップして次のデカールへ
                }

                // 投影ポリゴンが0面頭（車体から大きく離れている等）の場合でも、旧方式（空中の板）へは絶対にフォールスルーせず、選択枠のみ描画
                renderSelectionFrame(poseStack, bufferSource, decal, unitScale, isSelected, packedOverlay);
                continue;
            }

            class RenderableFaceQuad {
                final GeoCubeFaceRaycaster.ModelCubeFace face;
                final List<GeoCubeFaceRaycaster.ClippedVertex> quad;
                final float depth;

                RenderableFaceQuad(GeoCubeFaceRaycaster.ModelCubeFace face, List<GeoCubeFaceRaycaster.ClippedVertex> quad, float depth) {
                    this.face = face;
                    this.quad = quad;
                    this.depth = depth;
                }
            }

            List<RenderableFaceQuad> candidates = new java.util.ArrayList<>();

            for (GeoCubeFaceRaycaster.ModelCubeFace face : faces) {
                if (!face.boneName.equalsIgnoreCase(boneName)) continue;

                float dot = face.localNormal.dot(decalNormal);
                Vector3f toFace = new Vector3f(face.localCenter).sub(decalPos);
                float depthAlongRay = toFace.dot(rayDir);
                float distSq = face.localCenter.distanceSquared(decalPos);

                // 面の法線とおおむね同方向か判定（デカールを向いている面: cos 75° ≒ 0.25）
                if (dot < 0.25f) {
                    continue;
                }

                // プロジェクションレイ照射判定
                if (depthAlongRay < -3.0f || depthAlongRay > 40.0f) {
                    continue;
                }

                GeoCubeFaceRaycaster.ClippedQuadResult clipRes =
                        GeoCubeFaceRaycaster.getClippedDecalQuadDetailed(decal, face);

                if (!clipRes.isAccepted()) {
                    continue;
                }

                float depth = face.localCenter.dot(decalNormal);
                candidates.add(new RenderableFaceQuad(face, clipRes.vertices, depth));
            }

            // 最前面ソート（手前・外側にある面を優先）
            candidates.sort((a, b) -> Float.compare(b.depth, a.depth));

            // 遮蔽カリング（車体内部の内装や反対側パネルなど、3.0ブロック以上奥にある面の突き抜け描画のみを除外）
            List<RenderableFaceQuad> visibleQuads = new java.util.ArrayList<>();
            for (RenderableFaceQuad cand : candidates) {
                boolean occluded = false;
                for (RenderableFaceQuad front : visibleQuads) {
                    if (front.depth - cand.depth > 3.0f) { // front が明らかに外装で、cand が奥の内装や裏面
                        Vector3f frontCenter = new Vector3f(front.quad.get(0).pos).add(front.quad.get(2).pos).mul(0.5f);
                        Vector3f candCenter = new Vector3f(cand.quad.get(0).pos).add(cand.quad.get(2).pos).mul(0.5f);
                        Vector3f diff = new Vector3f(candCenter).sub(frontCenter);
                        float dotNorm = diff.dot(decalNormal);
                        float dist2D = (float) Math.sqrt(Math.max(0.0f, diff.lengthSquared() - dotNorm * dotNorm));
                        if (dist2D < 2.5f) { // 2D平面上で重なり合っている
                            occluded = true;
                            break;
                        }
                    }
                }
                if (!occluded) {
                    visibleQuads.add(cand);
                }
            }



            int renderedFaces = 0;
            for (RenderableFaceQuad rq : visibleQuads) {
                renderedFaces++;
                Vector3f fn = rq.face.localNormal;
                // 車体ポリゴンとのZ-fighting（特に垂直パネルやGUI内縮小描画での深度衝突）を完全に防止するため
                // 法線方向に実寸約9.4mm（Blockbench単位0.15f）浮かせます
                float ox = fn.x * 0.15f;
                float oy = fn.y * 0.15f;
                float oz = fn.z * 0.15f;

                PoseStack.Pose currentPose = poseStack.last();
                Matrix4f matrix = currentPose.pose();

                GeoCubeFaceRaycaster.ClippedVertex v0 = rq.quad.get(0);
                GeoCubeFaceRaycaster.ClippedVertex v1 = rq.quad.get(1);
                GeoCubeFaceRaycaster.ClippedVertex v2 = rq.quad.get(2);
                GeoCubeFaceRaycaster.ClippedVertex v3 = rq.quad.get(3);

                // 【表面 Quad: 反時計回り (CCW) - Minecraft標準の表面ワインディング】
                decalConsumer.addVertex(matrix, (v0.pos.x + ox) * unitScale, (v0.pos.y + oy) * unitScale, (v0.pos.z + oz) * unitScale)
                        .setColor(255, 255, 255, 255)
                        .setUv(v0.u, v0.v)
                        .setOverlay(packedOverlay)
                        .setLight(packedLight)
                        .setNormal(currentPose, fn.x, fn.y, fn.z);

                decalConsumer.addVertex(matrix, (v3.pos.x + ox) * unitScale, (v3.pos.y + oy) * unitScale, (v3.pos.z + oz) * unitScale)
                        .setColor(255, 255, 255, 255)
                        .setUv(v3.u, v3.v)
                        .setOverlay(packedOverlay)
                        .setLight(packedLight)
                        .setNormal(currentPose, fn.x, fn.y, fn.z);

                decalConsumer.addVertex(matrix, (v2.pos.x + ox) * unitScale, (v2.pos.y + oy) * unitScale, (v2.pos.z + oz) * unitScale)
                        .setColor(255, 255, 255, 255)
                        .setUv(v2.u, v2.v)
                        .setOverlay(packedOverlay)
                        .setLight(packedLight)
                        .setNormal(currentPose, fn.x, fn.y, fn.z);

                decalConsumer.addVertex(matrix, (v1.pos.x + ox) * unitScale, (v1.pos.y + oy) * unitScale, (v1.pos.z + oz) * unitScale)
                        .setColor(255, 255, 255, 255)
                        .setUv(v1.u, v1.v)
                        .setOverlay(packedOverlay)
                        .setLight(packedLight)
                        .setNormal(currentPose, fn.x, fn.y, fn.z);

                // 【裏面 Quad: 時計回り (CW) - GUIのY軸反転スケールやシェーダーバックフェイスでも100%描画保証】
                decalConsumer.addVertex(matrix, (v0.pos.x + ox) * unitScale, (v0.pos.y + oy) * unitScale, (v0.pos.z + oz) * unitScale)
                        .setColor(255, 255, 255, 255)
                        .setUv(v0.u, v0.v)
                        .setOverlay(packedOverlay)
                        .setLight(packedLight)
                        .setNormal(currentPose, fn.x, fn.y, fn.z);

                decalConsumer.addVertex(matrix, (v1.pos.x + ox) * unitScale, (v1.pos.y + oy) * unitScale, (v1.pos.z + oz) * unitScale)
                        .setColor(255, 255, 255, 255)
                        .setUv(v1.u, v1.v)
                        .setOverlay(packedOverlay)
                        .setLight(packedLight)
                        .setNormal(currentPose, fn.x, fn.y, fn.z);

                decalConsumer.addVertex(matrix, (v2.pos.x + ox) * unitScale, (v2.pos.y + oy) * unitScale, (v2.pos.z + oz) * unitScale)
                        .setColor(255, 255, 255, 255)
                        .setUv(v2.u, v2.v)
                        .setOverlay(packedOverlay)
                        .setLight(packedLight)
                        .setNormal(currentPose, fn.x, fn.y, fn.z);

                decalConsumer.addVertex(matrix, (v3.pos.x + ox) * unitScale, (v3.pos.y + oy) * unitScale, (v3.pos.z + oz) * unitScale)
                        .setColor(255, 255, 255, 255)
                        .setUv(v3.u, v3.v)
                        .setOverlay(packedOverlay)
                        .setLight(packedLight)
                        .setNormal(currentPose, fn.x, fn.y, fn.z);
            }

            if (renderedFaces == 0) {
                // 面が1つも検出されない場合のフォールバック（通常の単一Quad描画）
                poseStack.pushPose();
                poseStack.translate(decal.getPosX() * unitScale, decal.getPosY() * unitScale, decal.getPosZ() * unitScale);
                poseStack.mulPose(Axis.YP.rotationDegrees(decal.getRotY()));
                poseStack.mulPose(Axis.XP.rotationDegrees(decal.getRotX()));
                poseStack.mulPose(Axis.ZP.rotationDegrees(decal.getRotZ()));

                float zOffset = 0.015f;
                poseStack.translate(0, 0, zOffset);

                float aspect = decal.getAspect() > 0.0f ? decal.getAspect() : 1.0f;
                float baseHalfSize = (decal.getScale() * 0.5f) * unitScale;
                float halfWidth = baseHalfSize * aspect;
                float halfHeight = baseHalfSize;

                float u0 = decal.isMirrorX() ? 1.0f : 0.0f;
                float u1 = decal.isMirrorX() ? 0.0f : 1.0f;
                float v0 = 0.0f;
                float v1 = 1.0f;

                PoseStack.Pose currentPose = poseStack.last();
                Matrix4f matrix = currentPose.pose();

                decalConsumer.addVertex(matrix, -halfWidth,  halfHeight, 0.0f)
                        .setColor(255, 255, 255, 255)
                        .setUv(u0, v0)
                        .setOverlay(packedOverlay)
                        .setLight(packedLight)
                        .setNormal(currentPose, 0.0f, 0.0f, 1.0f);

                decalConsumer.addVertex(matrix, -halfWidth, -halfHeight, 0.0f)
                        .setColor(255, 255, 255, 255)
                        .setUv(u0, v1)
                        .setOverlay(packedOverlay)
                        .setLight(packedLight)
                        .setNormal(currentPose, 0.0f, 0.0f, 1.0f);

                decalConsumer.addVertex(matrix,  halfWidth, -halfHeight, 0.0f)
                        .setColor(255, 255, 255, 255)
                        .setUv(u1, v1)
                        .setOverlay(packedOverlay)
                        .setLight(packedLight)
                        .setNormal(currentPose, 0.0f, 0.0f, 1.0f);

                decalConsumer.addVertex(matrix,  halfWidth,  halfHeight, 0.0f)
                        .setColor(255, 255, 255, 255)
                        .setUv(u1, v0)
                        .setOverlay(packedOverlay)
                        .setLight(packedLight)
                        .setNormal(currentPose, 0.0f, 0.0f, 1.0f);

                poseStack.popPose();
            }

            // エディターで選択中のステッカーには、ステッカー表面にピッタリ密着するハイライト枠を3Dで直接描画
            if (isSelected) {
                poseStack.pushPose();
                poseStack.translate(decal.getPosX() * unitScale, decal.getPosY() * unitScale, decal.getPosZ() * unitScale);
                poseStack.mulPose(Axis.YP.rotationDegrees(decal.getRotY()));
                poseStack.mulPose(Axis.XP.rotationDegrees(decal.getRotX()));
                poseStack.mulPose(Axis.ZP.rotationDegrees(decal.getRotZ()));

                float zOffset = 0.005f;
                poseStack.translate(0, 0, zOffset);

                float aspect = decal.getAspect() > 0.0f ? decal.getAspect() : 1.0f;
                float baseHalfSize = (decal.getScale() * 0.5f) * unitScale;
                float halfWidth = baseHalfSize * aspect;
                float halfHeight = baseHalfSize;

                PoseStack.Pose currentPose = poseStack.last();
                Matrix4f matrix = currentPose.pose();

                ResourceLocation frameLoc = DecalTextureManager.getSelectionFrameTexture(editor.isDecalDragging());
                VertexConsumer frameConsumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(frameLoc));

                float pad = baseHalfSize * 0.08f;
                float fw = halfWidth + pad;
                float fh = halfHeight + pad;
                float zFrame = 0.003f;

                frameConsumer.addVertex(matrix, -fw,  fh, zFrame)
                        .setColor(255, 255, 255, 255)
                        .setUv(0.0f, 0.0f)
                        .setOverlay(packedOverlay)
                        .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                        .setNormal(currentPose, 0.0f, 0.0f, 1.0f);

                frameConsumer.addVertex(matrix, -fw, -fh, zFrame)
                        .setColor(255, 255, 255, 255)
                        .setUv(0.0f, 1.0f)
                        .setOverlay(packedOverlay)
                        .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                        .setNormal(currentPose, 0.0f, 0.0f, 1.0f);

                frameConsumer.addVertex(matrix,  fw, -fh, zFrame)
                        .setColor(255, 255, 255, 255)
                        .setUv(1.0f, 1.0f)
                        .setOverlay(packedOverlay)
                        .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                        .setNormal(currentPose, 0.0f, 0.0f, 1.0f);

                frameConsumer.addVertex(matrix,  fw,  fh, zFrame)
                        .setColor(255, 255, 255, 255)
                        .setUv(1.0f, 0.0f)
                        .setOverlay(packedOverlay)
                        .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                        .setNormal(currentPose, 0.0f, 0.0f, 1.0f);

                poseStack.popPose();
            }
        }
    }

    private ResourceLocation resolveModelLocation(T animatable) {
        String variantId = animatable.getVariantId();
        if (variantId == null || variantId.isBlank()) variantId = "default";

        if (animatable instanceof com.microdrone.mod.entity.RcCarEntity) {
            var cfg = com.microdrone.mod.config.car.CarConfigManager.getConfig(variantId);
            return cfg != null ? cfg.getModelResource() : ResourceLocation.fromNamespaceAndPath(com.microdrone.mod.MicroDroneMod.MODID, "geo/rc_car.geo.json");
        } else if (animatable instanceof com.microdrone.mod.entity.RcPlaneEntity) {
            var cfg = com.microdrone.mod.config.plane.PlaneConfigManager.getConfig(variantId);
            return cfg != null ? cfg.getModelResource() : ResourceLocation.fromNamespaceAndPath(com.microdrone.mod.MicroDroneMod.MODID, "geo/rc_plane.geo.json");
        } else if (animatable instanceof com.microdrone.mod.entity.RcBoatEntity) {
            var cfg = com.microdrone.mod.config.boat.BoatConfigManager.getConfig(variantId);
            return cfg != null ? cfg.getModelResource() : ResourceLocation.fromNamespaceAndPath(com.microdrone.mod.MicroDroneMod.MODID, "geo/rc_boat.geo.json");
        } else if (animatable instanceof com.microdrone.mod.entity.RcSubmarineEntity) {
            var cfg = com.microdrone.mod.config.submarine.SubmarineConfigManager.getConfig(variantId);
            return cfg != null ? cfg.getModelResource() : ResourceLocation.fromNamespaceAndPath(com.microdrone.mod.MicroDroneMod.MODID, "geo/rc_submarine.geo.json");
        } else if (animatable instanceof com.microdrone.mod.entity.RcTankEntity) {
            var cfg = com.microdrone.mod.config.tank.TankConfigManager.getConfig(variantId);
            return cfg != null ? cfg.getModelResource() : ResourceLocation.fromNamespaceAndPath(com.microdrone.mod.MicroDroneMod.MODID, "geo/rc_tank.geo.json");
        } else if (animatable instanceof com.microdrone.mod.entity.DroneEntity) {
            var cfg = com.microdrone.mod.config.drone.DroneConfigManager.getConfig(variantId);
            return cfg != null ? cfg.getModelResource() : ResourceLocation.fromNamespaceAndPath(com.microdrone.mod.MicroDroneMod.MODID, "geo/drone.geo.json");
        }
        var cfg = com.microdrone.mod.config.car.CarConfigManager.getConfig(variantId);
        return cfg != null ? cfg.getModelResource() : ResourceLocation.fromNamespaceAndPath(com.microdrone.mod.MicroDroneMod.MODID, "geo/rc_car.geo.json");
    }

    /**
     * 車種ごとのデカール描画責任代表ボーンの判定。
     * 1モデルにつき必ず1つのルート代表ボーン（body または chassis）のみが描画を受け持ち、
     * 子ボーンや他ボーンでの二重描画・重複描画・誤吸着を物理的に根絶する。
     */
    private static <T extends Entity & GeoAnimatable & RcDecalHolder> boolean isBoneResponsibleForDecal(
            T animatable, ResourceLocation modelLoc, List<GeoCubeFaceRaycaster.ModelCubeFace> faces, String boneName, String target) {

        // 明示的に個別パーツボーン（例: turret等）が指定されている場合
        if (target != null && !target.isBlank() && !"body".equalsIgnoreCase(target) && !"chassis".equalsIgnoreCase(target) && !"root".equalsIgnoreCase(target)) {
            return boneName.equalsIgnoreCase(target);
        }

        // 通常のボディデカールは、車種ごとの唯一の代表ボーンでのみ描画
        String repBone = getRepresentativeDecalBone(animatable, faces);
        return boneName.equalsIgnoreCase(repBone);
    }

    private static <T extends Entity & GeoAnimatable & RcDecalHolder> String getRepresentativeDecalBone(
            T animatable, List<GeoCubeFaceRaycaster.ModelCubeFace> faces) {
        if (animatable instanceof com.microdrone.mod.entity.RcPlaneEntity) return "body";
        if (animatable instanceof com.microdrone.mod.entity.RcBoatEntity) return "body";
        if (animatable instanceof com.microdrone.mod.entity.RcSubmarineEntity) return "body";
        if (animatable instanceof com.microdrone.mod.entity.RcTankEntity) return "chassis";
        if (animatable instanceof com.microdrone.mod.entity.DroneEntity) return "chassis";
        if (animatable instanceof com.microdrone.mod.entity.RcCarEntity car) {
            String variant = car.getVariantId();
            if ("86".equalsIgnoreCase(variant) || "truck".equalsIgnoreCase(variant) || "bicycle".equalsIgnoreCase(variant)) {
                return "body";
            }
            // モデル内に chassis がなく body がある場合は body を優先
            boolean hasBody = faces.stream().anyMatch(f -> "body".equalsIgnoreCase(f.boneName));
            boolean hasChassis = faces.stream().anyMatch(f -> "chassis".equalsIgnoreCase(f.boneName));
            if (hasBody && !hasChassis) {
                return "body";
            }
            return "chassis";
        }

        // 汎用判定: "body" があれば body、なければ chassis
        for (GeoCubeFaceRaycaster.ModelCubeFace f : faces) {
            if ("body".equalsIgnoreCase(f.boneName)) return "body";
        }
        return "chassis";
    }

    private static void addDecalVertex(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
                                       com.microdrone.mod.client.decal.DecalMeshProjector.DecalVertex v,
                                       float ox, float oy, float oz, float unitScale,
                                       int packedOverlay, int packedLight, Vector3f normal) {
        consumer.addVertex(matrix, (v.pos.x + ox) * unitScale, (v.pos.y + oy) * unitScale, (v.pos.z + oz) * unitScale)
                .setColor(255, 255, 255, 255)
                .setUv(v.u, v.v)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, normal.x, normal.y, normal.z);
    }

    private static void renderSelectionFrame(PoseStack poseStack, MultiBufferSource bufferSource,
                                             CarDecalData decal, float unitScale,
                                             boolean isSelected, int packedOverlay) {
        if (!isSelected) return;
        com.microdrone.mod.client.gui.DecalEditorScreen editor = com.microdrone.mod.client.gui.DecalEditorScreen.activeScreen;
        if (editor == null) return;

        poseStack.pushPose();
        poseStack.translate(decal.getPosX() * unitScale, decal.getPosY() * unitScale, decal.getPosZ() * unitScale);
        poseStack.mulPose(Axis.YP.rotationDegrees(decal.getRotY()));
        poseStack.mulPose(Axis.XP.rotationDegrees(decal.getRotX()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(decal.getRotZ()));

        float zOffset = 0.005f;
        poseStack.translate(0, 0, zOffset);

        float aspect = decal.getAspect() > 0.0f ? decal.getAspect() : 1.0f;
        float baseHalfSize = (decal.getScale() * 0.5f) * unitScale;
        float halfWidth = baseHalfSize * aspect;
        float halfHeight = baseHalfSize;

        PoseStack.Pose currentPose = poseStack.last();
        Matrix4f matrix = currentPose.pose();

        ResourceLocation frameLoc = DecalTextureManager.getSelectionFrameTexture(editor.isDecalDragging());
        VertexConsumer frameConsumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(frameLoc));

        float pad = baseHalfSize * 0.08f;
        float fw = halfWidth + pad;
        float fh = halfHeight + pad;
        float zFrame = 0.003f;

        frameConsumer.addVertex(matrix, -fw,  fh, zFrame)
                .setColor(255, 255, 255, 255)
                .setUv(0.0f, 0.0f)
                .setOverlay(packedOverlay)
                .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                .setNormal(currentPose, 0.0f, 0.0f, 1.0f);

        frameConsumer.addVertex(matrix, -fw, -fh, zFrame)
                .setColor(255, 255, 255, 255)
                .setUv(0.0f, 1.0f)
                .setOverlay(packedOverlay)
                .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                .setNormal(currentPose, 0.0f, 0.0f, 1.0f);

        frameConsumer.addVertex(matrix,  fw, -fh, zFrame)
                .setColor(255, 255, 255, 255)
                .setUv(1.0f, 1.0f)
                .setOverlay(packedOverlay)
                .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                .setNormal(currentPose, 0.0f, 0.0f, 1.0f);

        frameConsumer.addVertex(matrix,  fw,  fh, zFrame)
                .setColor(255, 255, 255, 255)
                .setUv(1.0f, 0.0f)
                .setOverlay(packedOverlay)
                .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                .setNormal(currentPose, 0.0f, 0.0f, 1.0f);

        poseStack.popPose();
    }
}
