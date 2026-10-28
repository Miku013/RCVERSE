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

        // 2. セットされているバッテリーアイテムがある場合、充電台の上に水平に寝かせて描画
        ItemStack battery = animatable.getStoredBattery();
        if (!battery.isEmpty() && animatable.getLevel() != null) {
            poseStack.pushPose();

            // 中心（X: 0.5, Z: 0.5）かつ充電パッド上面（Y: 0.28）へ移動
            poseStack.translate(0.5D, 0.28D, 0.5D);

            // ブロックの設置向き（FACING）に合わせて回転
            Direction facing = animatable.getBlockState().getValue(DroneChargerBlock.FACING);
            float rotationAngle = -facing.toYRot();
            poseStack.mulPose(Axis.YP.rotationDegrees(rotationAngle));

            // 水平に寝かせる（X軸に90度回転）
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));

            // バッテリーのサイズ感に合わせてスケール調整
            poseStack.scale(0.55F, 0.55F, 0.55F);

            // ItemDisplayContext.FIXED を指定して原点ズレを防ぎ、ボーン/ブロックにジャストフィット
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
