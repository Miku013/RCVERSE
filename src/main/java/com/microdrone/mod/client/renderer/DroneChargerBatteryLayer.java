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

                // 1. ボーンのピボット座標および回転マトリクス（drone_charger.geo.json の battery_slot で指定された [0, -90, 90]）を忠実に適用
                RenderUtil.translateAndRotateMatrixForBone(poseStack, bone);

                // 2. ItemDisplayContext.FIXED を指定してスロットにジャストフィット描画
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
