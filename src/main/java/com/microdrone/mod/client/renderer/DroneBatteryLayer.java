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
                // 1. GeckoLib本体が prepMatrixForBone で適用した回転とオフセットを pop してリセット
                poseStack.popPose();
                poseStack.pushPose();

                // 2. battery_slot ボーンのピボット座標と回転マトリクスを【正確に1回だけ】適用
                RenderUtil.translateAndRotateMatrixForBone(poseStack, bone);

                // 3. バッテリーのサイズをドローンスロットに合わせてスケーリング
                poseStack.scale(0.55F, 0.55F, 0.55F);

                // 4. ItemDisplayContext.FIXED を指定して描画
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

                // 5. 自分のポーズをポップし、呼び出し元のために再プッシュ
                poseStack.popPose();
                poseStack.pushPose();
            }
        }
    }
}
