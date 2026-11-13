package com.microdrone.mod.client.renderer;

import com.microdrone.mod.entity.RcCarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * RCカーのガラス系ボーン（名前に glass / window を含むボーン）を
 * 半透明（entityTranslucent）で描画するレンダーレイヤー。
 */
public class RcCarGlassLayer extends GeoRenderLayer<RcCarEntity> {

    private final RcCarGeoRenderer carRenderer;

    public RcCarGlassLayer(RcCarGeoRenderer entityRenderer) {
        super(entityRenderer);
        this.carRenderer = entityRenderer;
    }

    @Override
    public void render(PoseStack poseStack, RcCarEntity animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        RenderType glassRenderType = RenderType.entityTranslucent(getTextureResource(animatable));
        VertexConsumer glassBuffer = bufferSource.getBuffer(glassRenderType);

        this.carRenderer.setRenderPass(RcCarGeoRenderer.RenderPass.GLASS);
        try {
            getRenderer().reRender(
                    bakedModel,
                    poseStack,
                    bufferSource,
                    animatable,
                    glassRenderType,
                    glassBuffer,
                    partialTick,
                    packedLight,
                    packedOverlay,
                    0xFFFFFFFF
            );
        } finally {
            this.carRenderer.setRenderPass(RcCarGeoRenderer.RenderPass.BASE);
        }
    }
}

