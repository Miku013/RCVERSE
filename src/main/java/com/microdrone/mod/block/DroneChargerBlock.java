package com.microdrone.mod.block;

import com.microdrone.mod.block.entity.DroneChargerBlockEntity;
import com.microdrone.mod.block.entity.ModBlockEntities;
import com.microdrone.mod.item.DroneBatteryItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class DroneChargerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<DroneChargerBlock> CODEC = simpleCodec(DroneChargerBlock::new);
    public static final BooleanProperty CHARGING = BooleanProperty.create("charging");

    // 充電ステーションの形状: 高さ 4ドット（0.25ブロック）の充電パッド
    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 4.0D, 15.0D);

    public DroneChargerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CHARGING, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CHARGING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(CHARGING, false);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // GeckoLib による 3D モデルアニメーション描画
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DroneChargerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide) {
            return null;
        }
        return blockEntityType == ModBlockEntities.DRONE_CHARGER_BE.get()
                ? (lvl, p, st, be) -> DroneChargerBlockEntity.serverTick(lvl, p, st, (DroneChargerBlockEntity) be)
                : null;
    }

    /**
     * 手にアイテムを持っている際のインタラクション（バッテリーの装着・交換）
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof DroneBatteryItem) {
            if (!level.isClientSide) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof DroneChargerBlockEntity charger) {
                    ItemStack existing = charger.getStoredBattery();
                    if (!existing.isEmpty()) {
                        // 既存のバッテリーを取り出してプレイヤーへ渡す
                        if (!player.getInventory().add(existing)) {
                            player.drop(existing, false);
                        }
                    }

                    // 新しいバッテリーを1つセット
                    ItemStack toInsert = stack.split(1);
                    charger.setStoredBattery(toInsert);

                    level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.2F);

                    DroneBatteryItem batItem = (DroneBatteryItem) toInsert.getItem();
                    int maxDist = batItem.getMaxFlightBlocks();
                    int remainDist = batItem.getRemainingBlocks(toInsert);
                    int percent = (int) Math.round((double) remainDist / maxDist * 100.0);

                    player.displayClientMessage(
                            Component.translatable("message.microdrone.charger.inserted", toInsert.getHoverName(), percent, remainDist, maxDist)
                                    .withStyle(ChatFormatting.AQUA),
                            true
                    );
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        // バッテリー以外のアイテム所持時は通常のブロック操作（取り出し処理）へフォールバック
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * 素手または未処理アイテム時のインタラクション（バッテリーの取り出し、または残量確認）
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DroneChargerBlockEntity charger) {
                ItemStack stored = charger.getStoredBattery();
                if (!stored.isEmpty()) {
                    // バッテリーを取り出し
                    charger.setStoredBattery(ItemStack.EMPTY);
                    if (!player.getInventory().add(stored)) {
                        player.drop(stored, false);
                    }

                    level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);

                    if (stored.getItem() instanceof DroneBatteryItem batItem) {
                        int maxDist = batItem.getMaxFlightBlocks();
                        int remainDist = batItem.getRemainingBlocks(stored);
                        int percent = (int) Math.round((double) remainDist / maxDist * 100.0);

                        player.displayClientMessage(
                                Component.translatable("message.microdrone.charger.removed", stored.getHoverName(), percent, remainDist, maxDist)
                                        .withStyle(ChatFormatting.YELLOW),
                                true
                        );
                    }
                } else {
                    // 空の状態を通知
                    player.displayClientMessage(
                            Component.translatable("message.microdrone.charger.empty")
                                    .withStyle(ChatFormatting.GRAY),
                            true
                    );
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * ブロック破壊時にセットされていたバッテリーを安全にドロップ
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DroneChargerBlockEntity charger) {
                ItemStack stored = charger.getStoredBattery();
                if (!stored.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, stored);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
