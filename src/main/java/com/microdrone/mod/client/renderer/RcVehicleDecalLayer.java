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

            // 指定されたボーンの描画タイミングで投影（body/chassisの重複描画を厳密に防止）
            String target = decal.getTargetBone();
            if (!isBoneResponsibleForDecal(faces, boneName, target)) {
                continue;
            }

            ResourceLocation textureLoc = DecalTextureManager.getStickerTexture(decal.getTextureName());
            if (textureLoc == null) {
                continue;
            }

            VertexConsumer decalConsumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(textureLoc));

            // エディターで選択中のステッカーの場合、ボーン座標系（デカール配置オフセット直前）の姿勢行列をエディターに共有
            boolean isSelected = (editor != null) && (i == selectedIndex || editor.getCurrentSelectedDecal() == decal);
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
            Vector3f rayDir = new Vector3f(decalNormal).negate(); // デカールから車体へ向かう垂直プロジェクションレイ

            for (GeoCubeFaceRaycaster.ModelCubeFace face : faces) {
                if (!face.boneName.equalsIgnoreCase(boneName)) continue;

                // 面の法線とおおむね同方向か判定（デカールを向いている面: cos 75° ≒ 0.25）
                float dot = face.localNormal.dot(decalNormal);
                if (dot < 0.25f) continue;

                // プロジェクションレイ照射判定：
                // デカールから垂直に車体へ飛ばしたレイの進行方向（rayDir）にある面を捕捉
                // デカールが車体から離れていても（手前にある限り）、レイが届く深さ（最大40ブロック）まで確実に投影
                Vector3f toFace = new Vector3f(face.localCenter).sub(decalPos);
                float depthAlongRay = toFace.dot(rayDir);
                if (depthAlongRay < -3.0f || depthAlongRay > 40.0f) {
                    continue; // デカールの背後にある面、または遠すぎる面は除外
                }

                List<GeoCubeFaceRaycaster.ClippedVertex> clippedQuad =
                        GeoCubeFaceRaycaster.getClippedDecalQuad(decal, face);

                if (!clippedQuad.isEmpty()) {
                    float depth = face.localCenter.dot(decalNormal);
                    candidates.add(new RenderableFaceQuad(face, clippedQuad, depth));
                }
            }

            // 最前面ソート（手前・外側にある面を優先）
            candidates.sort((a, b) -> Float.compare(b.depth, a.depth));

            // 遮蔽カリング（車体内部の内装や反対側パネルなど、3.0ブロック以上奥にある面の突き抜け描画のみを除外）
            // ※モール等の突起（0.4ブロック程度の段差）で下部のドアパネルが消えてしまわないよう閾値を3.0fに設定
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
                float ox = fn.x * 0.015f;
                float oy = fn.y * 0.015f;
                float oz = fn.z * 0.015f;

                PoseStack.Pose currentPose = poseStack.last();
                Matrix4f matrix = currentPose.pose();

                GeoCubeFaceRaycaster.ClippedVertex v0 = rq.quad.get(0);
                GeoCubeFaceRaycaster.ClippedVertex v1 = rq.quad.get(1);
                GeoCubeFaceRaycaster.ClippedVertex v2 = rq.quad.get(2);
                GeoCubeFaceRaycaster.ClippedVertex v3 = rq.quad.get(3);

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
     * デカール貼り付け対象ボーンの判定。
     * モデル内に "body" ボーンが存在する場合は必ず "body" 描画時のみ受け持ち、
     * "chassis" との重複描画・二重描画を完全に排除する。
     */
    private static boolean isBoneResponsibleForDecal(List<GeoCubeFaceRaycaster.ModelCubeFace> faces, String boneName, String target) {
        if (target == null || target.isBlank()) target = "body";
        if (boneName.equalsIgnoreCase(target)) {
            return true;
        }
        if ("body".equalsIgnoreCase(target) || "chassis".equalsIgnoreCase(target)) {
            boolean hasBodyFaces = false;
            for (GeoCubeFaceRaycaster.ModelCubeFace f : faces) {
                if ("body".equalsIgnoreCase(f.boneName)) {
                    hasBodyFaces = true;
                    break;
                }
            }
            if (hasBodyFaces) {
                return "body".equalsIgnoreCase(boneName);
            }
            boolean hasChassisFaces = false;
            for (GeoCubeFaceRaycaster.ModelCubeFace f : faces) {
                if ("chassis".equalsIgnoreCase(f.boneName)) {
                    hasChassisFaces = true;
                    break;
                }
            }
            if (hasChassisFaces) {
                return "chassis".equalsIgnoreCase(boneName);
            }
        }
        return false;
    }
}
