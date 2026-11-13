package com.microdrone.mod.client.renderer;

import com.microdrone.mod.block.RcRackBlock;
import com.microdrone.mod.block.entity.RcRackBlockEntity;
import com.microdrone.mod.client.model.RcRackModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class RcRackBlockEntityRenderer extends GeoBlockRenderer<RcRackBlockEntity> {
    public RcRackBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new RcRackModel());
        this.addRenderLayer(new RcRackDisplayLayer(this));
    }

    @Override
    public void render(RcRackBlockEntity animatable, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Direction facing = animatable.getBlockState().hasProperty(RcRackBlock.FACING)
                ? animatable.getBlockState().getValue(RcRackBlock.FACING)
                : Direction.NORTH;

        poseStack.pushPose();

        // ブロックの中心に原点を移動して水平回転 (FACING)
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(-0.5, 0.0, -0.5);

        super.render(animatable, partialTick, poseStack, bufferSource, packedLight, packedOverlay);

        poseStack.popPose();
    }
}
