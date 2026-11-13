package com.microdrone.mod.client.renderer;

import com.microdrone.mod.client.decal.DecalTextureManager;
import com.microdrone.mod.decal.CarDecalData;
import com.microdrone.mod.decal.RcDecalHolder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.List;

/**
 * 全RC乗り物（カー、飛行機、ボート、潜水艦、戦車、ドローン）対応の透過ステッカー描画レイヤー
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

        String boneName = bone.getName();
        for (CarDecalData decal : decals) {
            if (decal == null || decal.getTextureName() == null || decal.getTextureName().isBlank()) {
                continue;
            }

            // 指定されたボーンの描画タイミングで投影
            String target = decal.getTargetBone();
            boolean match = boneName.equalsIgnoreCase(target);
            // 互換フォールバック: targetが "body" でモデル主要ボーンが "chassis" の場合、またはその逆
            if (!match) {
                if (("body".equalsIgnoreCase(target) || "chassis".equalsIgnoreCase(target)) &&
                    ("body".equalsIgnoreCase(boneName) || "chassis".equalsIgnoreCase(boneName))) {
                    match = true;
                }
            }
            if (!match) {
                continue;
            }

            ResourceLocation textureLoc = DecalTextureManager.getStickerTexture(decal.getTextureName());
            if (textureLoc == null) {
                continue;
            }

            VertexConsumer decalConsumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(textureLoc));

            poseStack.pushPose();

            // 1. デカールの配置オフセット (Blockbench unit: 1/16 block)
            float unitScale = 0.0625f;
            poseStack.translate(decal.getPosX() * unitScale, decal.getPosY() * unitScale, decal.getPosZ() * unitScale);

            // 2. デカールの向き・回転（YPR: Yaw/Pitch/Roll）
            poseStack.mulPose(Axis.YP.rotationDegrees(decal.getRotY()));
            poseStack.mulPose(Axis.XP.rotationDegrees(decal.getRotX()));
            poseStack.mulPose(Axis.ZP.rotationDegrees(decal.getRotZ()));

            // 3. Z-fighting防止用微小法線オフセット
            float zOffset = 0.012f;
            poseStack.translate(0, 0, zOffset);

            // 4. スケール計算（アスペクト比対応）
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

            // 反時計回り (CCW) で四角形ポリゴン頂点を追加
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

            // エディターで選択中のステッカーには、ステッカー表面にピッタリ密着するハイライト枠を3D描画
            com.microdrone.mod.client.gui.DecalEditorScreen editor = com.microdrone.mod.client.gui.DecalEditorScreen.activeScreen;
            if (editor != null && editor.getCurrentSelectedDecal() == decal) {
                // 3Dモデル変換後の正確な画面GUI座標 (X, Y) をエディタへ通知
                float sx = matrix.m30();
                float sy = matrix.m31();
                float r = Math.max(14.0f, (halfWidth + halfHeight) * 0.5f / unitScale * editor.getFactor() * 0.8f);
                editor.setProjectedDecalPos(sx, sy, r);

                // ステッカーの四辺にピッタリ密着するハイライト枠（3D描画）
                VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.gui());
                float zFrame = 0.003f; // ステッカー表面の直上にわずかに浮かせてZ-fighting防止
                float border = Math.max(0.004f, halfWidth * 0.04f);

                int cr = editor.isDecalDragging() ? 255 : 0;
                int cg = editor.isDecalDragging() ? 238 : 238;
                int cb = editor.isDecalDragging() ? 68 : 255;
                int ca = 255;

                // 上辺枠
                lineConsumer.addVertex(matrix, -halfWidth - border,  halfHeight + border, zFrame).setColor(cr, cg, cb, ca);
                lineConsumer.addVertex(matrix, -halfWidth - border,  halfHeight,          zFrame).setColor(cr, cg, cb, ca);
                lineConsumer.addVertex(matrix,  halfWidth + border,  halfHeight,          zFrame).setColor(cr, cg, cb, ca);
                lineConsumer.addVertex(matrix,  halfWidth + border,  halfHeight + border, zFrame).setColor(cr, cg, cb, ca);

                // 下辺枠
                lineConsumer.addVertex(matrix, -halfWidth - border, -halfHeight,          zFrame).setColor(cr, cg, cb, ca);
                lineConsumer.addVertex(matrix, -halfWidth - border, -halfHeight - border, zFrame).setColor(cr, cg, cb, ca);
                lineConsumer.addVertex(matrix,  halfWidth + border, -halfHeight - border, zFrame).setColor(cr, cg, cb, ca);
                lineConsumer.addVertex(matrix,  halfWidth + border, -halfHeight,          zFrame).setColor(cr, cg, cb, ca);

                // 左辺枠
                lineConsumer.addVertex(matrix, -halfWidth - border,  halfHeight,          zFrame).setColor(cr, cg, cb, ca);
                lineConsumer.addVertex(matrix, -halfWidth - border, -halfHeight,          zFrame).setColor(cr, cg, cb, ca);
                lineConsumer.addVertex(matrix, -halfWidth,          -halfHeight,          zFrame).setColor(cr, cg, cb, ca);
                lineConsumer.addVertex(matrix, -halfWidth,           halfHeight,          zFrame).setColor(cr, cg, cb, ca);

                // 右辺枠
                lineConsumer.addVertex(matrix,  halfWidth,           halfHeight,          zFrame).setColor(cr, cg, cb, ca);
                lineConsumer.addVertex(matrix,  halfWidth,          -halfHeight,          zFrame).setColor(cr, cg, cb, ca);
                lineConsumer.addVertex(matrix,  halfWidth + border, -halfHeight,          zFrame).setColor(cr, cg, cb, ca);
                lineConsumer.addVertex(matrix,  halfWidth + border,  halfHeight,          zFrame).setColor(cr, cg, cb, ca);

                // 面内ハイライトティント（半透明）
                int fillA = editor.isDecalDragging() ? 60 : 35;
                lineConsumer.addVertex(matrix, -halfWidth,  halfHeight, zFrame).setColor(cr, cg, cb, fillA);
                lineConsumer.addVertex(matrix, -halfWidth, -halfHeight, zFrame).setColor(cr, cg, cb, fillA);
                lineConsumer.addVertex(matrix,  halfWidth, -halfHeight, zFrame).setColor(cr, cg, cb, fillA);
                lineConsumer.addVertex(matrix,  halfWidth,  halfHeight, zFrame).setColor(cr, cg, cb, fillA);
            }

            poseStack.popPose();
        }
    }
}
