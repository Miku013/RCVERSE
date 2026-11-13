package com.microdrone.mod.client.renderer;

import com.microdrone.mod.block.entity.RcRackBlockEntity;
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

public class RcRackDisplayLayer extends GeoRenderLayer<RcRackBlockEntity> {
    private final ItemRenderer itemRenderer;

    public RcRackDisplayLayer(GeoBlockRenderer<RcRackBlockEntity> blockRenderer) {
        super(blockRenderer);
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void renderForBone(PoseStack poseStack, RcRackBlockEntity animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        String boneName = bone.getName();
        if (boneName.startsWith("slot_")) {
            try {
                int slot = Integer.parseInt(boneName.substring(5));
                ItemStack stack = animatable.getItem(slot);
                if (!stack.isEmpty() && animatable.getLevel() != null) {
                    poseStack.popPose();
                    poseStack.pushPose();

                    RenderUtil.translateAndRotateMatrixForBone(poseStack, bone);

                    // 各段のボーン原点（ピボット）からマシンの位置・向きを調整
                    // 棚板の天板面よりわずかに上 (Y=0.06m) に配置し、手前（Z-方向）を向くよう調整
                    poseStack.translate(0.0, 0.06, 0.0);
                    poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
                    poseStack.scale(0.85F, 0.85F, 0.85F);

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

                    poseStack.popPose();
                    poseStack.pushPose();
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
