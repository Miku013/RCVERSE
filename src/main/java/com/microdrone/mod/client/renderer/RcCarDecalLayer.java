package com.microdrone.mod.client.renderer;

import com.microdrone.mod.client.decal.DecalTextureManager;
import com.microdrone.mod.decal.CarDecalData;
import com.microdrone.mod.entity.RcCarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.List;

/**
 * RCカーの車体表面に透過PNGステッカーを描画するGeckoLibレンダーレイヤー
 */
public class RcCarDecalLayer extends GeoRenderLayer<RcCarEntity> {

    public RcCarDecalLayer(GeoEntityRenderer<RcCarEntity> entityRenderer) {
        super(entityRenderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, RcCarEntity animatable, GeoBone bone,
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

            // 指定されたボーン（デフォルト: body）の描画タイミングで投影
            if (!boneName.equalsIgnoreCase(decal.getTargetBone())) {
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

            // 2. 姿勢回転 (オイラー角: X -> Y -> Z)
            if (decal.getRotX() != 0) poseStack.mulPose(Axis.XP.rotationDegrees(decal.getRotX()));
            if (decal.getRotY() != 0) poseStack.mulPose(Axis.YP.rotationDegrees(decal.getRotY()));
            if (decal.getRotZ() != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(decal.getRotZ()));

            // 3. サイズ（クアッドの幅と高さの半サイズ：アスペクト比対応）
            float aspect = decal.getAspect() > 0.01f ? decal.getAspect() : 1.0f;
            float halfH = (decal.getScale() * unitScale) * 0.5f;
            float halfW = halfH * aspect;

            // UV 反転対応
            float u0 = decal.isMirrorX() ? 1.0f : 0.0f;
            float u1 = decal.isMirrorX() ? 0.0f : 1.0f;
            float v0 = 0.0f;
            float v1 = 1.0f;

            // Zファイト防止のための微小オフセット (1/5スケール下で確実に表面から浮かせるため 0.012m オフセット)
            float zOffset = 0.012f;

            PoseStack.Pose lastPose = poseStack.last();
            Matrix4f mat = lastPose.pose();

            // 4. クアッドポリゴン (XY平面、法線 +Z、反時計回り Front-face)
            // 左下 -> 右下 -> 右上 -> 左上
            decalConsumer.addVertex(mat, -halfW, -halfH, zOffset)
                    .setColor(255, 255, 255, 255)
                    .setUv(u0, v1)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(lastPose, 0.0f, 0.0f, 1.0f);

            decalConsumer.addVertex(mat, halfW, -halfH, zOffset)
                    .setColor(255, 255, 255, 255)
                    .setUv(u1, v1)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(lastPose, 0.0f, 0.0f, 1.0f);

            decalConsumer.addVertex(mat, halfW, halfH, zOffset)
                    .setColor(255, 255, 255, 255)
                    .setUv(u1, v0)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(lastPose, 0.0f, 0.0f, 1.0f);

            decalConsumer.addVertex(mat, -halfW, halfH, zOffset)
                    .setColor(255, 255, 255, 255)
                    .setUv(u0, v0)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(lastPose, 0.0f, 0.0f, 1.0f);

            poseStack.popPose();
        }
    }
}

