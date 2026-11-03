package com.microdrone.mod.block.miniature;

import com.microdrone.mod.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * コース小物（ミニチュア）の共通アイテムクラス
 * 地面や土台ブロック（MiniaturePlacementBlock）を右クリックして5x5グリッド内に配置
 */
public class MiniatureItem extends Item {
    private final String miniatureId;

    public MiniatureItem(Properties properties, String miniatureId) {
        super(properties);
        this.miniatureId = miniatureId;
    }

    public String getMiniatureId() {
        return miniatureId;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        Player player = context.getPlayer();
        ItemStack held = context.getItemInHand();

        BlockState clickedState = level.getBlockState(clickedPos);

        // ケース1: 既に MiniaturePlacementBlock をクリックした場合
        if (clickedState.is(ModBlocks.MINIATURE_PLACEMENT.get())) {
            BlockEntity be = level.getBlockEntity(clickedPos);
            if (be instanceof MiniaturePlacementBlockEntity placementBe) {
                double localX = context.getClickLocation().x - clickedPos.getX();
                double localZ = context.getClickLocation().z - clickedPos.getZ();
                int slotId = MiniaturePlacementData.getSlotFromHit(localX, localZ);

                if (!placementBe.hasSlot(slotId)) {
                    if (!level.isClientSide) {
                        float yaw = calculatePlacementYaw(placementBe, slotId, player);
                        placementBe.placeMiniature(slotId, this.miniatureId, yaw);
                        level.playSound(null, clickedPos, SoundEvents.LANTERN_PLACE, SoundSource.BLOCKS, 1.0f, 1.2f);
                        if (player != null && !player.getAbilities().instabuild) {
                            held.shrink(1);
                        }
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            }
            return InteractionResult.PASS;
        }

        // ケース2: 通常ブロックの上面をクリックした場合 -> 上の空気に土台ブロックを新規設置して即座に小物配置
        if (clickedFace == Direction.UP) {
            BlockPos targetPos = clickedPos.above();
            BlockState targetState = level.getBlockState(targetPos);

            if (targetState.canBeReplaced()) {
                if (!level.isClientSide) {
                    // 土台ブロックを設置
                    level.setBlock(targetPos, ModBlocks.MINIATURE_PLACEMENT.get().defaultBlockState(), 3);
                    BlockEntity be = level.getBlockEntity(targetPos);
                    if (be instanceof MiniaturePlacementBlockEntity placementBe) {
                        double localX = context.getClickLocation().x - targetPos.getX();
                        double localZ = context.getClickLocation().z - targetPos.getZ();
                        int slotId = MiniaturePlacementData.getSlotFromHit(localX, localZ);

                        float yaw = calculatePlacementYaw(placementBe, slotId, player);
                        placementBe.placeMiniature(slotId, this.miniatureId, yaw);
                    }
                    level.playSound(null, targetPos, SoundEvents.LANTERN_PLACE, SoundSource.BLOCKS, 1.0f, 1.2f);
                    if (player != null && !player.getAbilities().instabuild) {
                        held.shrink(1);
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        return super.useOn(context);
    }

    /**
     * 配置時の向き（Yaw）を算出
     * autoAlign 有効時は、周囲の同種小物の位置から接続ベクトルを自動計算
     */
    protected float calculatePlacementYaw(MiniaturePlacementBlockEntity placementBe, int slotId, Player player) {
        MiniatureType type = MiniatureType.getById(this.miniatureId);
        if (type != null && type.isAutoAlign()) {
            Float autoYaw = placementBe.calculateAutoAlignYaw(slotId, this.miniatureId);
            if (autoYaw != null) {
                com.microdrone.mod.MicroDroneMod.LOGGER.info("[ITEM_PLACE] AutoYaw applied: {} for slot {}", autoYaw, slotId);
                return autoYaw;
            }
        }

        // 周囲に同種がない場合はプレイヤーの向き（45度スナップ）
        float yaw = player != null ? player.getYRot() : 0.0f;
        float snapped = Math.round(yaw / 45.0f) * 45.0f;
        com.microdrone.mod.MicroDroneMod.LOGGER.info("[ITEM_PLACE] Initial player yaw applied: {} for slot {}", snapped, slotId);
        return snapped;
    }
}

