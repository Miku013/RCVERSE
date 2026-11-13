package com.microdrone.mod.client.renderer;

import com.microdrone.mod.block.entity.RcShowcaseBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

public class RcShowcaseDisplayLayer extends GeoRenderLayer<RcShowcaseBlockEntity> {
    private final ItemRenderer itemRenderer;

    public RcShowcaseDisplayLayer(GeoBlockRenderer<RcShowcaseBlockEntity> blockRenderer) {
        super(blockRenderer);
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void renderForBone(PoseStack poseStack, RcShowcaseBlockEntity animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        String boneName = bone.getName();
        if (boneName.startsWith("slot_")) {
            try {
                int slot = Integer.parseInt(boneName.substring(5));
                ItemStack stack = animatable.getItem(slot);
                if (!stack.isEmpty() && animatable.getLevel() != null) {
                    poseStack.popPose();
                    poseStack.pushPose();

                    RenderUtil.translateAndRotateMatrixForBone(poseStack, bone);

                    // 1. RCビークルアイテムであれば、3D実物モデル（GeckoLib）を描画（正面が少し斜め左を向くよう25度回転）
                    boolean rendered3D = RcVehicleDisplayRenderer.renderVehicle3D(stack, poseStack, bufferSource, packedLight, animatable.getLevel(), 0.58F, 25.0F);

                    // 2. ビークル以外の通常アイテムやプロポの場合、ItemRendererで描画
                    if (!rendered3D) {
                        poseStack.translate(0.0, 0.06, 0.0);
                        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - 25.0F));
                        poseStack.scale(0.65F, 0.65F, 0.65F);

                        this.itemRenderer.renderStatic(
                                stack,
                                ItemDisplayContext.FIXED,
                                packedLight,
                                packedOverlay,
                                poseStack,
                                bufferSource,
                                animatable.getLevel(),
                                (int) animatable.getBlockPos().asLong() + slot
                        );
                    }

                    poseStack.popPose();
                    poseStack.pushPose();
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
