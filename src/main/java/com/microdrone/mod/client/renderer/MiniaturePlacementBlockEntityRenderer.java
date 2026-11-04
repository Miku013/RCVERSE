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

    private static final int GUARDRAIL_POST_INTERVAL = 3; // 3ピースごとに中間支柱ポールを1本表示

    @Override
    public void render(MiniaturePlacementBlockEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        this.animatable = entity;
        GenericMiniatureModel genericModel = (GenericMiniatureModel) this.model;

        // 1. 設置済みの小物を全スロット描画（スロットごとにモデルとテクスチャを動的切り替え）
        for (MiniaturePlacementData slot : entity.getAllSlots()) {
            MiniatureType type = MiniatureType.getById(slot.getMiniatureId());
            if (type == null) continue;

            genericModel.setMiniatureType(type);

            // ガードレールのシームレス連結制御 (両端にポールを表示し、中間は一定間隔ごとに1本ポールを表示)
            if ("guardrail".equals(type.getId())) {
                NeighborResult leftNeighbor = findMiniatureNeighborResult(entity, slot, "guardrail", false);
                NeighborResult rightNeighbor = findMiniatureNeighborResult(entity, slot, "guardrail", true);
                boolean hasLeft = leftNeighbor != null;
                boolean hasRight = rightNeighbor != null;

                if (!hasLeft && !hasRight) {
                    // 単独配置: 両端にポールを表示 (自立)
                    genericModel.setGuardrailConnection(false, false);
                } else if (!hasLeft && hasRight) {
                    // 左端ピース: 左ポールのみ表示
                    genericModel.setGuardrailConnection(false, true);
                } else if (hasLeft && !hasRight) {
                    // 右端ピース: 右ポールのみ表示
                    genericModel.setGuardrailConnection(true, false);
                } else {
                    // 中間ピース: 一定間隔 (3ピースごと) に右ポールを1本表示 (右端直前のピースは除外)
                    int chainIndex = getMiniatureChainIndex(entity, slot, "guardrail");
                    int remaining = getMiniatureRemainingSteps(entity, slot, "guardrail");

                    if (chainIndex % GUARDRAIL_POST_INTERVAL == 0 && remaining >= 2) {
                        genericModel.setGuardrailConnection(true, false); // 右ポール表示
                    } else {
                        genericModel.setGuardrailConnection(true, true);  // 両方非表示 (レールのみ)
                    }
                }
            } else if ("goal_gate".equals(type.getId())) {
                // ゴールゲートのシームレス連結制御 (両端に柱を表示、中央ピースにのみワイドバナーを表示)
                NeighborResult leftNeighbor = findMiniatureNeighborResult(entity, slot, "goal_gate", false);
                NeighborResult rightNeighbor = findMiniatureNeighborResult(entity, slot, "goal_gate", true);
                boolean hasLeft = leftNeighbor != null;
                boolean hasRight = rightNeighbor != null;

                int chainIndex = getMiniatureChainIndex(entity, slot, "goal_gate");
                int remaining = getMiniatureRemainingSteps(entity, slot, "goal_gate");
                int totalLength = chainIndex + remaining + 1;

                // 柱の表示: 左端なら左柱のみ、右端なら右柱のみ、中間は柱非表示、単独なら両方表示
                boolean hideLeft = hasLeft;
                boolean hideRight = hasRight;

                // バナー看板の表示: 全長の中央のピースでのみワイドバナーを表示
                boolean showBanner;
                if (totalLength == 1) {
                    showBanner = true;
                } else {
                    showBanner = (chainIndex == totalLength / 2);
                }

                genericModel.setGoalGateConnection(hideLeft, hideRight, showBanner);
            } else {
                genericModel.setGuardrailConnection(false, false);
                genericModel.setGoalGateConnection(false, false, true);
            }

            poseStack.pushPose();

            // スロット位置（X, Z）へ移動。Y=0.0f で地面に完全フラット接地
            poseStack.translate(slot.getXOffset(), 0.0f, slot.getZOffset());

            // 向き（Y軸回転）
            poseStack.mulPose(Axis.YP.rotationDegrees(180f - slot.getYaw()));

            // スケール
            float scale = slot.getScale();
            poseStack.scale(scale, scale, scale);

            int renderLight = packedLight;
            RenderType customRenderType = null;

            // チェックポイントプレート: 色付きガラス風半透明 ＆ 暗闇でも鮮やかに光り輝くフルブライト発光
            if ("checkpoint_plate".equals(type.getId())) {
                renderLight = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
                customRenderType = RenderType.entityTranslucent(type.getTextureLocation());
            }

            // 通常描画
            defaultRender(poseStack, entity, bufferSource, customRenderType, null, 0, partialTick, renderLight);

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

    private record NeighborResult(MiniaturePlacementBlockEntity entity, MiniaturePlacementData slot) {}

    /**
     * 指定スロットのガードレールの長手方向（左端 post_left / 右端 post_right）に接続している別のガードレールを検索
     *
     * @param checkRight true の場合、右端 (post_right) 方向の隣接を判定。false の場合、左端 (post_left) 方向の隣接を判定。
     */
    private NeighborResult findMiniatureNeighborResult(MiniaturePlacementBlockEntity entity, MiniaturePlacementData slot, String targetId, boolean checkRight) {
        net.minecraft.world.level.Level level = entity.getLevel();
        net.minecraft.core.BlockPos centerPos = entity.getBlockPos();

        double myWorldX = centerPos.getX() + slot.getXOffset();
        double myWorldZ = centerPos.getZ() + slot.getZOffset();

        // モデルの右方向単位ベクトル (Axis.YP.rotationDegrees(180f - yaw) に合致)
        double rotRad = Math.toRadians(180.0 - slot.getYaw());
        double rightDirX = Math.cos(rotRad);
        double rightDirZ = -Math.sin(rotRad);

        // 法線（直交）方向単位ベクトル
        double normalDirX = -rightDirZ;
        double normalDirZ = rightDirX;

        NeighborResult bestMatch = null;
        double minDistance = Double.MAX_VALUE;

        // 周囲3x3ブロック（同一ブロック含む）を走査
        for (int bdx = -1; bdx <= 1; bdx++) {
            for (int bdz = -1; bdz <= 1; bdz++) {
                net.minecraft.core.BlockPos targetPos = centerPos.offset(bdx, 0, bdz);
                MiniaturePlacementBlockEntity targetEntity;
                if (bdx == 0 && bdz == 0) {
                    targetEntity = entity;
                } else if (level != null && level.getBlockEntity(targetPos) instanceof MiniaturePlacementBlockEntity be) {
                    targetEntity = be;
                } else {
                    continue;
                }

                for (MiniaturePlacementData other : targetEntity.getAllSlots()) {
                    if (bdx == 0 && bdz == 0 && other.getSlotId() == slot.getSlotId()) continue;
                    if (!targetId.equals(other.getMiniatureId())) continue;

                    double otherWorldX = targetPos.getX() + other.getXOffset();
                    double otherWorldZ = targetPos.getZ() + other.getZOffset();

                    double diffX = otherWorldX - myWorldX;
                    double diffZ = otherWorldZ - myWorldZ;
                    double distSq = diffX * diffX + diffZ * diffZ;

                    // 探索距離: 最大1.35ブロック以内 (隣接スロット0.2m〜隣接ブロック1.0m、斜め1.35m)
                    if (distSq > 0.01 && distSq <= 1.82) {
                        // 右方向への射影（内積）
                        double projRight = diffX * rightDirX + diffZ * rightDirZ;
                        // 法線方向へのズレ
                        double projNormal = Math.abs(diffX * normalDirX + diffZ * normalDirZ);

                        // レールの長手方向軸から大きくズレていないか (0.40ブロック以内)
                        if (projNormal < 0.40) {
                            if (checkRight && projRight > 0.12) {
                                if (distSq < minDistance) {
                                    minDistance = distSq;
                                    bestMatch = new NeighborResult(targetEntity, other);
                                }
                            }
                            if (!checkRight && projRight < -0.12) {
                                if (distSq < minDistance) {
                                    minDistance = distSq;
                                    bestMatch = new NeighborResult(targetEntity, other);
                                }
                            }
                        }
                    }
                }
            }
        }
        return bestMatch;
    }

    /**
     * チェーンの左端（始点）から現在のスロットまでのステップ数を取得
     */
    private int getMiniatureChainIndex(MiniaturePlacementBlockEntity entity, MiniaturePlacementData slot, String targetId) {
        int index = 0;
        MiniaturePlacementData current = slot;
        MiniaturePlacementBlockEntity currentEntity = entity;

        // 左方向（手前）へ最大30ステップ遡る
        while (index < 30) {
            NeighborResult prev = findMiniatureNeighborResult(currentEntity, current, targetId, false);
            if (prev == null) {
                break; // 左端に到達
            }
            index++;
            current = prev.slot();
            currentEntity = prev.entity();
        }
        return index;
    }

    /**
     * チェーンの現在のスロットから右端（終点）までの残りステップ数を取得
     */
    private int getMiniatureRemainingSteps(MiniaturePlacementBlockEntity entity, MiniaturePlacementData slot, String targetId) {
        int remaining = 0;
        MiniaturePlacementData current = slot;
        MiniaturePlacementBlockEntity currentEntity = entity;

        // 右方向（奥）へ最大30ステップ進む
        while (remaining < 30) {
            NeighborResult next = findMiniatureNeighborResult(currentEntity, current, targetId, true);
            if (next == null) {
                break; // 右端に到達
            }
            remaining++;
            current = next.slot();
            currentEntity = next.entity();
        }
        return remaining;
    }
}
