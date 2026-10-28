package com.microdrone.mod.client.renderer;

import com.microdrone.mod.block.entity.DroneChargerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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

/**
 * 急速充電ステーションの battery_slot ボーンに、セットされたバッテリーを描画する GeckoLib レンダーレイヤー
 */
public class DroneChargerBatteryLayer extends GeoRenderLayer<DroneChargerBlockEntity> {
    private final ItemRenderer itemRenderer;

    public DroneChargerBatteryLayer(GeoBlockRenderer<DroneChargerBlockEntity> blockRenderer) {
        super(blockRenderer);
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void renderForBone(PoseStack poseStack, DroneChargerBlockEntity animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if ("battery_slot".equals(bone.getName())) {
            ItemStack battery = animatable.getStoredBattery();
            if (!battery.isEmpty() && animatable.getLevel() != null) {
                poseStack.pushPose();

                // 1. ボーンのピボット位置へ正確に移動 (prepMatrixForBone の translateAwayFromPivotPoint を相殺)
                RenderUtil.translateToPivotPoint(poseStack, bone);

                // 2. バッテリーモデルのケース幾何中心 [1.0, 1.5, 0.0] をピボットへ完全合致
                poseStack.translate(-1.0D / 16.0D, -1.5D / 16.0D, 0.0D);

                // 3. GeoItemRenderer内部で自動適用される translate(0.5, 0.51, 0.5) を相殺
                poseStack.translate(-0.5D, -0.51D, -0.5D);

                // 4. バッテリーを描画 (ブロックの設置方角・傾斜・ピボットはGeckoLibが完全自動同期)
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
}
