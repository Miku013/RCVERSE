package com.microdrone.mod.client.renderer;

import com.microdrone.mod.entity.RcCarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * RCカーのライト用ボーンをエミッシブ（自己発光）描画するレンダーレイヤー。
 * - Hキー（ヘッドライトON）で前後ライトボーンをエミッシブ発光
 * - ブレーキ／ハンドブレーキ入力時はブレーキ／テールボーンをより強く発光
 */
public class RcCarEmissiveLayer extends GeoRenderLayer<RcCarEntity> {

    private final RcCarGeoRenderer carRenderer;

    public RcCarEmissiveLayer(RcCarGeoRenderer entityRenderer) {
        super(entityRenderer);
        this.carRenderer = entityRenderer;
    }

    @Override
    public void render(PoseStack poseStack, RcCarEntity animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        if (!animatable.isHeadlightOn() && !animatable.isBrakeLightOn()) {
            return;
        }

        RenderType emissiveRenderType = RenderType.eyes(getTextureResource(animatable));
        VertexConsumer emissiveBuffer = bufferSource.getBuffer(emissiveRenderType);

        this.carRenderer.setRenderPass(RcCarGeoRenderer.RenderPass.EMISSIVE);
        try {
            getRenderer().reRender(
                    bakedModel,
                    poseStack,
                    bufferSource,
                    animatable,
                    emissiveRenderType,
                    emissiveBuffer,
                    partialTick,
                    LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    0xFFFFFFFF
            );
        } finally {
            this.carRenderer.setRenderPass(RcCarGeoRenderer.RenderPass.BASE);
        }
    }
}

