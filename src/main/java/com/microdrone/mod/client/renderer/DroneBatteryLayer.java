package com.microdrone.mod.client.renderer;

import com.microdrone.mod.entity.DroneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

public class DroneBatteryLayer extends GeoRenderLayer<DroneEntity> {
    private final ItemRenderer itemRenderer;

    public DroneBatteryLayer(GeoEntityRenderer<DroneEntity> entityRenderer) {
        super(entityRenderer);
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void renderForBone(PoseStack poseStack, DroneEntity animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        // ドローンの battery_slot ボーンに装着中バッテリーを描画
        if ("battery_slot".equals(bone.getName())) {
            ItemStack battery = animatable.getInstalledBattery();
            if (!battery.isEmpty()) {
                poseStack.pushPose();

                // ボーンのピボット座標および回転マトリクスを適用
                RenderUtil.translateAndRotateMatrixForBone(poseStack, bone);

                // バッテリーのサイズをドローンスロットに合わせてスケーリング
                poseStack.scale(0.55F, 0.55F, 0.55F);

                // ItemDisplayContext.FIXED を指定することで、ボーン中心にジャストフィットして描画
                this.itemRenderer.renderStatic(
                        battery,
                        ItemDisplayContext.FIXED,
                        packedLight,
                        packedOverlay,
                        poseStack,
                        bufferSource,
                        animatable.level(),
                        animatable.getId()
                );

                poseStack.popPose();
            }
        }
    }
}

