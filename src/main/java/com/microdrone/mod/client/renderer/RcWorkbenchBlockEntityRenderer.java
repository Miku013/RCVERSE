package com.microdrone.mod.client.renderer;

import com.microdrone.mod.block.RcWorkbenchBlock;
import com.microdrone.mod.block.entity.RcWorkbenchBlockEntity;
import com.microdrone.mod.client.model.RcWorkbenchModel;
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

/**
 * RC工作台の3Dレンダーパイプライン
 * リアルなホビーワークベンチモデルを描画し、天板上にマシン・アイテムを展示描画
 */
public class RcWorkbenchBlockEntityRenderer extends GeoBlockRenderer<RcWorkbenchBlockEntity> {
    private final ItemRenderer itemRenderer;

    public RcWorkbenchBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new RcWorkbenchModel());
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void render(RcWorkbenchBlockEntity animatable, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Direction facing = animatable.getBlockState().hasProperty(RcWorkbenchBlock.FACING)
                ? animatable.getBlockState().getValue(RcWorkbenchBlock.FACING)
                : Direction.NORTH;

        poseStack.pushPose();

        // ブロックの中心に原点を移動して水平回転 (FACING)
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(-0.5, 0.0, -0.5);

        // 1. RC工作台自体のGeckoLibモデル描画
        super.render(animatable, partialTick, poseStack, bufferSource, packedLight, packedOverlay);

        // 2. 天板上に展示アイテムがセットされている場合、中央に展示描画
        ItemStack displayed = animatable.getDisplayedItem();
        if (!displayed.isEmpty()) {
            poseStack.pushPose();
            // 天板中央 (Y=1.02m) 、奥のツールボード手前に綺麗に配置
            poseStack.translate(0.5, 1.02, 0.45);
            poseStack.scale(0.85F, 0.85F, 0.85F);

            this.itemRenderer.renderStatic(
                    displayed,
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
