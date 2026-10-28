package com.microdrone.mod.client.renderer;

import com.microdrone.mod.block.DroneChargerBlock;
import com.microdrone.mod.block.entity.DroneChargerBlockEntity;
import com.microdrone.mod.client.model.DroneChargerModel;
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

public class DroneChargerBlockEntityRenderer extends GeoBlockRenderer<DroneChargerBlockEntity> {
    private final ItemRenderer itemRenderer;

    public DroneChargerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new DroneChargerModel());
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void render(DroneChargerBlockEntity animatable, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // 1. 充電ステーション本体の GeckoLib 3Dモデルを描画
        super.render(animatable, partialTick, poseStack, bufferSource, packedLight, packedOverlay);

        // 2. セットされているバッテリーアイテムがある場合、Blockbench指定座標・角度でスロットに差し込んで描画
        ItemStack battery = animatable.getStoredBattery();
        if (!battery.isEmpty() && animatable.getLevel() != null) {
            poseStack.pushPose();

            // ブロック水平中心（X: 0.5, Z: 0.5）へ移動
            poseStack.translate(0.5D, 0.0D, 0.5D);

            // ブロックの設置向き（FACING）に合わせて回転
            Direction facing = animatable.getBlockState().getValue(DroneChargerBlock.FACING);
            float rotationAngle = -facing.toYRot();
            poseStack.mulPose(Axis.YP.rotationDegrees(rotationAngle));

            // Blockbenchでの配置座標: Position [0.6, 9.0, -1.2]
            double bbX = 0.6D / 16.0D;
            double bbY = 9.0D / 16.0D;
            double bbZ = -1.2D / 16.0D;
            poseStack.translate(bbX, bbY, bbZ);

            // Blockbenchでの回転: Rotation [X: 0, Y: -45, Z: 90]
            poseStack.mulPose(Axis.YP.rotationDegrees(-45.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));

            // GeoItemRenderer内部で自動適用される translate(0.5, 0.51, 0.5) を相殺
            poseStack.translate(-0.5D, -0.51D, -0.5D);

            // ItemDisplayContext.FIXED を指定して描画
            this.itemRenderer.renderStatic(
                    battery,
                    ItemDisplayContext.FIXED,
                    packedLight,
                    packedOverlay,
                    poseStack,
                    bufferSource,
                    animatable.getLevel(),
                    (int) animatable.getBlockPos().asLong()
            );

            poseStack.popPose();
        }
    }
}
