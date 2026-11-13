package com.microdrone.mod.client.renderer;

import com.microdrone.mod.block.RcShowcaseBlock;
import com.microdrone.mod.block.entity.RcShowcaseBlockEntity;
import com.microdrone.mod.client.model.RcShowcaseModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class RcShowcaseBlockEntityRenderer extends GeoBlockRenderer<RcShowcaseBlockEntity> {
    public RcShowcaseBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new RcShowcaseModel());
        this.addRenderLayer(new RcShowcaseDisplayLayer(this));
    }

    @Override
    public void render(RcShowcaseBlockEntity animatable, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Direction facing = animatable.getBlockState().hasProperty(RcShowcaseBlock.FACING)
                ? animatable.getBlockState().getValue(RcShowcaseBlock.FACING)
                : Direction.NORTH;

        poseStack.pushPose();

        // 中央下段ブロックの中心に原点を移動して水平回転 (FACING)
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(-0.5, 0.0, -0.5);

        super.render(animatable, partialTick, poseStack, bufferSource, packedLight, packedOverlay);

        poseStack.popPose();
    }
}
