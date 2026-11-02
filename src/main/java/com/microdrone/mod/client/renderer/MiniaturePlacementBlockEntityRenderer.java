package com.microdrone.mod.client.renderer;

import com.microdrone.mod.block.miniature.MiniaturePlacementBlockEntity;
import com.microdrone.mod.block.miniature.MiniaturePlacementData;
import com.microdrone.mod.block.miniature.TrafficConeItem;
import com.microdrone.mod.client.model.TrafficConeModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 1ブロック内の全スロットのコース小物を描画するレンダラー
 * 手持ちカラーコーン時のゴーストプレビュー（半透明配置ガイド）にも対応
 */
public class MiniaturePlacementBlockEntityRenderer extends GeoBlockRenderer<MiniaturePlacementBlockEntity> {

    public MiniaturePlacementBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new TrafficConeModel());
    }

    /**
     * GeoBlockRenderer デフォルトの (0.5, 0, 0.5) への自動移動をオーバーライドしてキャンセル。
     * これにより、スロットごとのローカルオフセットと当たり判定枠（VoxelShape）が 100% 狂いなく一致する。
     */
    @Override
    public void preRender(PoseStack poseStack, MiniaturePlacementBlockEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        this.blockRenderTranslations = new org.joml.Matrix4f(poseStack.last().pose());
        scaleModelForRender(this.scaleWidth, this.scaleHeight, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay);
    }

    @Override
    public void render(MiniaturePlacementBlockEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        this.animatable = entity;

        // 1. 設置済みの小物を全スロット描画
        for (MiniaturePlacementData slot : entity.getAllSlots()) {
            poseStack.pushPose();

            // スロット位置（X, Z）へ移動。Y=0.0f で地面に完全フラット接地
            poseStack.translate(slot.getXOffset(), 0.0f, slot.getZOffset());

            // 向き（Y軸回転）
            poseStack.mulPose(Axis.YP.rotationDegrees(180f - slot.getYaw()));

            // スケール
            float scale = slot.getScale();
            poseStack.scale(scale, scale, scale);

            // 通常描画
            defaultRender(poseStack, entity, bufferSource, null, null, 0, partialTick, packedLight);

            poseStack.popPose();
        }

        // 2. ゴーストプレビュー描画（プレイヤーがカラーコーンを持っている場合）
        renderGhostPreview(entity, partialTick, poseStack, bufferSource, packedLight);
    }

    /**
     * 手持ちアイテムがカラーコーンの際、視線が指している空きスロットに半透明プレビューを表示
     */
    private void renderGhostPreview(MiniaturePlacementBlockEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        // 手持ちアイテムが TrafficConeItem かチェック
        boolean holdingCone = player.getMainHandItem().getItem() instanceof TrafficConeItem ||
                player.getOffhandItem().getItem() instanceof TrafficConeItem;
        if (!holdingCone) return;

        // 視線が当たっているブロックがこのBlockEntityかチェック
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(entity.getBlockPos())) {
            double localX = blockHit.getLocation().x - entity.getBlockPos().getX();
            double localZ = blockHit.getLocation().z - entity.getBlockPos().getZ();
            int hoverSlotId = MiniaturePlacementData.getSlotFromHit(localX, localZ);

            // 空きスロットであれば半透明ゴーストを描画
            if (!entity.hasSlot(hoverSlotId)) {
                poseStack.pushPose();

                float gx = MiniaturePlacementData.getSlotCenterX(hoverSlotId);
                float gz = MiniaturePlacementData.getSlotCenterZ(hoverSlotId);
                poseStack.translate(gx, 0.0f, gz);

                // プレイヤーの視線角度にスナップ
                float yaw = player.getYRot();
                float snappedYaw = Math.round(yaw / 45.0f) * 45.0f;
                poseStack.mulPose(Axis.YP.rotationDegrees(180f - snappedYaw));

                // スケール (1/2サイズ)
                float ghostScale = MiniaturePlacementData.DEFAULT_CONE_SCALE;
                poseStack.scale(ghostScale, ghostScale, ghostScale);

                // 半透明 RenderType でゴースト描画
                RenderType ghostRenderType = RenderType.entityTranslucentCull(getTextureLocation(entity));
                defaultRender(poseStack, entity, bufferSource, ghostRenderType, null, 0, partialTick, packedLight);

                poseStack.popPose();
            }
        }
    }
}

