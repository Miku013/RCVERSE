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

        com.microdrone.mod.client.gui.DecalEditorScreen editor = com.microdrone.mod.client.gui.DecalEditorScreen.activeScreen;
        int selectedIndex = editor != null ? editor.getSelectedDecalIndex() : -1;

        String boneName = bone.getName();
        for (int i = 0; i < decals.size(); i++) {
            CarDecalData decal = decals.get(i);
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

            // エディターで選択中のステッカーの場合、ボーン座標系（デカール配置オフセット直前）の姿勢行列をエディターに共有
            boolean isSelected = (editor != null) && (i == selectedIndex || editor.getCurrentSelectedDecal() == decal);
            if (isSelected) {
                editor.updateDecalPoseMatrix(poseStack.last().pose());
            }

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

            // エディターで選択中のステッカーには、ステッカー表面にピッタリ密着するハイライト枠をステッカーと同一パイプライン（3D）で直接描画
            if (isSelected) {
                ResourceLocation frameLoc = DecalTextureManager.getSelectionFrameTexture(editor.isDecalDragging());
                VertexConsumer frameConsumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(frameLoc));

                float pad = baseHalfSize * 0.08f;
                float fw = halfWidth + pad;
                float fh = halfHeight + pad;
                float zFrame = 0.003f; // ステッカー直上

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
            }

            poseStack.popPose();
        }
    }
}
