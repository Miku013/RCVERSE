package com.microdrone.mod.client.renderer;

import com.microdrone.mod.block.miniature.MiniatureItem;
import com.microdrone.mod.block.miniature.MiniaturePlacementBlockEntity;
import com.microdrone.mod.block.miniature.MiniaturePlacementData;
import com.microdrone.mod.block.miniature.MiniatureType;
import com.microdrone.mod.client.model.GenericMiniatureModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 1ブロック内の全スロットのコース小物を動的描画するレンダラー
 * カラーコーン、縁石、ガードレール、タイヤバリアなど複数種の同時混在描画に対応
 * 手持ちアイテムに応じた半透明ゴーストプレビュー（オートアングル角度対応）
 */
public class MiniaturePlacementBlockEntityRenderer extends GeoBlockRenderer<MiniaturePlacementBlockEntity> {

    public MiniaturePlacementBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new GenericMiniatureModel());
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
        GenericMiniatureModel genericModel = (GenericMiniatureModel) this.model;

        // 1. 設置済みの小物を全スロット描画（スロットごとにモデルとテクスチャを動的切り替え）
        for (MiniaturePlacementData slot : entity.getAllSlots()) {
            MiniatureType type = MiniatureType.getById(slot.getMiniatureId());
            if (type == null) continue;

            genericModel.setMiniatureType(type);

            // ガードレールのシームレス連結制御 (隣り合うレールの重複ポールを自動非表示)
            if ("guardrail".equals(type.getId())) {
                boolean hasRightNeighbor = checkHasGuardrailNeighbor(entity, slot, true);
                genericModel.setGuardrailConnection(false, hasRightNeighbor);
            } else {
                genericModel.setGuardrailConnection(false, false);
            }

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

        // 2. ゴーストプレビュー描画（プレイヤーがコース小物を持っている場合）
        renderGhostPreview(entity, partialTick, poseStack, bufferSource, packedLight);
    }

    /**
     * 手持ちアイテムがコース小物の際、視線が指している空きスロットに半透明プレビューを表示
     */
    private void renderGhostPreview(MiniaturePlacementBlockEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        // 手持ちアイテムから MiniatureItem を特定
        ItemStack heldStack = player.getMainHandItem();
        if (!(heldStack.getItem() instanceof MiniatureItem)) {
            heldStack = player.getOffhandItem();
            if (!(heldStack.getItem() instanceof MiniatureItem)) {
                return;
            }
        }
        MiniatureItem minItem = (MiniatureItem) heldStack.getItem();
        MiniatureType type = MiniatureType.getById(minItem.getMiniatureId());
        if (type == null) return;

        // 視線が当たっているブロックがこのBlockEntityかチェック
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(entity.getBlockPos())) {
            double localX = blockHit.getLocation().x - entity.getBlockPos().getX();
            double localZ = blockHit.getLocation().z - entity.getBlockPos().getZ();
            int hoverSlotId = MiniaturePlacementData.getSlotFromHit(localX, localZ);

            // 空きスロットであれば半透明ゴーストを描画
            if (!entity.hasSlot(hoverSlotId)) {
                GenericMiniatureModel genericModel = (GenericMiniatureModel) this.model;
                genericModel.setMiniatureType(type);

                poseStack.pushPose();

                float gx = MiniaturePlacementData.getSlotCenterX(hoverSlotId);
                float gz = MiniaturePlacementData.getSlotCenterZ(hoverSlotId);
                poseStack.translate(gx, 0.0f, gz);

                // 向きの算出: autoAlign 対応の場合は接続角度をリアルタイムプレビュー
                float yaw;
                Float autoYaw = type.isAutoAlign() ? entity.calculateAutoAlignYaw(hoverSlotId, type.getId()) : null;
                if (autoYaw != null) {
                    yaw = autoYaw;
                } else {
                    float playerYaw = player.getYRot();
                    yaw = Math.round(playerYaw / 45.0f) * 45.0f;
                }
                poseStack.mulPose(Axis.YP.rotationDegrees(180f - yaw));

                // スケール
                float ghostScale = type.getDefaultScale();
                poseStack.scale(ghostScale, ghostScale, ghostScale);

                // 半透明 RenderType でゴースト描画
                genericModel.setGuardrailConnection(false, false);
                RenderType ghostRenderType = RenderType.entityTranslucentCull(type.getTextureLocation());
                defaultRender(poseStack, entity, bufferSource, ghostRenderType, null, 0, partialTick, packedLight);

                poseStack.popPose();
            }
        }
    }

    /**
     * 指定スロットのガードレールの進行方向（接続先）に別のガードレールが連結しているか判定
     */
    private boolean checkHasGuardrailNeighbor(MiniaturePlacementBlockEntity entity, MiniaturePlacementData slot, boolean checkRight) {
        int curCol = slot.getSlotId() % MiniaturePlacementData.GRID_SIZE;
        int curRow = slot.getSlotId() / MiniaturePlacementData.GRID_SIZE;
        double yawRad = Math.toRadians(slot.getYaw());

        for (MiniaturePlacementData other : entity.getAllSlots()) {
            if (other.getSlotId() == slot.getSlotId()) continue;
            if (!"guardrail".equals(other.getMiniatureId())) continue;

            int oCol = other.getSlotId() % MiniaturePlacementData.GRID_SIZE;
            int oRow = other.getSlotId() / MiniaturePlacementData.GRID_SIZE;
            double dx = oCol - curCol;
            double dz = oRow - curRow;
            double distSq = dx * dx + dz * dz;

            // 探索距離: 2.5マス以内
            if (distSq <= 5.0) {
                // 進行方向ベクトル (cos(yaw), sin(yaw)) との内積
                double dot = dx * Math.cos(yawRad) + dz * Math.sin(yawRad);
                if (checkRight && dot > 0.3) {
                    return true;
                }
            }
        }
        return false;
    }
}
