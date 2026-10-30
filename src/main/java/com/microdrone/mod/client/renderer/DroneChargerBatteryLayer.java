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
                // 1. GeckoLib本体が prepMatrixForBone で適用した回転とオフセットを pop してリセット
                //    （これにより二重回転バグを根本から完全に解消し、親ボーンの基準空間に戻す）
                poseStack.popPose();

                // 2. バッテリー描画用のクリーンなマトリクスをプッシュ
                poseStack.pushPose();

                // 3. battery_slot ボーンのピボット座標と回転を【正確に1回だけ】適用！
                RenderUtil.translateAndRotateMatrixForBone(poseStack, bone);

                // 4. ItemDisplayContext.FIXED を指定してスロットにジャストフィット描画
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

                // 5. バッテリー描画用マトリクスをポップ
                poseStack.popPose();

                // 6. 呼び出し元（GeckoLib renderRecursively）の popPose と整合性を保つため再プッシュ
                poseStack.pushPose();
            }
        }
    }
}
