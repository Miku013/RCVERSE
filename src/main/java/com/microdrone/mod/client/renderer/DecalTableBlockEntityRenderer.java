package com.microdrone.mod.client.renderer;

import com.microdrone.mod.block.DecalTableBlock;
import com.microdrone.mod.block.entity.DecalTableBlockEntity;
import com.microdrone.mod.client.model.DecalTableModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class DecalTableBlockEntityRenderer extends GeoBlockRenderer<DecalTableBlockEntity> {
    private final ItemRenderer itemRenderer;

    public DecalTableBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new DecalTableModel());
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void render(DecalTableBlockEntity animatable, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Direction facing = animatable.getBlockState().hasProperty(DecalTableBlock.FACING)
                ? animatable.getBlockState().getValue(DecalTableBlock.FACING)
                : Direction.NORTH;

        poseStack.pushPose();

        // ブロックの中心に原点を移動
        poseStack.translate(0.5, 0.0, 0.5);
        // 水平回転 (FACING)
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(-0.5, 0.0, -0.5);

        // 1. 作業台自体のGeckoLibモデル描画
        super.render(animatable, partialTick, poseStack, bufferSource, packedLight, packedOverlay);

        // 2. 作業台の上にRC乗り物が載っている場合、天板の上にモデルを展示描画
        if (!animatable.getStoredCar().isEmpty()) {
            ItemStack car = animatable.getStoredCar();
            poseStack.pushPose();
            // 天板中央 (Y=1.02m) に水平に綺麗に展示
            poseStack.translate(0.5, 1.02, 0.5);
            poseStack.scale(0.9F, 0.9F, 0.9F);

            this.itemRenderer.renderStatic(
                    car,
                    ItemDisplayContext.GROUND,
                    packedLight,
                    packedOverlay,
                    poseStack,
                    bufferSource,
                    animatable.getLevel(),
                    0
            );
            poseStack.popPose();
        }

        poseStack.popPose();
    }
}

