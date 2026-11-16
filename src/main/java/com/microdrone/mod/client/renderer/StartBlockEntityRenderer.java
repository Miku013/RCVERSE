package com.microdrone.mod.client.renderer;

import com.microdrone.mod.block.StartBlock;
import com.microdrone.mod.block.entity.StartBlockEntity;
import com.microdrone.mod.client.model.StartBlockModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * スタートブロックの3Dレンダーパイプライン
 * 設置方角（FACING）に合わせた回転と、点灯時のフルブライト自発光描画
 */
public class StartBlockEntityRenderer extends GeoBlockRenderer<StartBlockEntity> {
    public StartBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new StartBlockModel());
    }

    @Override
    public void render(StartBlockEntity animatable, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Direction facing = animatable.getBlockState().hasProperty(StartBlock.FACING)
                ? animatable.getBlockState().getValue(StartBlock.FACING)
                : Direction.NORTH;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(-0.5, 0.0, -0.5);

        // LED点灯中はモデル全体をフルブライト(0xF000F0)で鮮やかに発光
        int light = animatable.getLightState() > 0 ? 0xF000F0 : packedLight;
        super.render(animatable, partialTick, poseStack, bufferSource, light, packedOverlay);

        poseStack.popPose();
    }
}
