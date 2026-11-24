package com.microdrone.mod.block;

import com.microdrone.mod.block.entity.ModBlockEntities;
import com.microdrone.mod.block.entity.RcPitStationBlockEntity;
import com.microdrone.mod.item.*;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * RCピット作業台ブロック。
 * 設置してRCマシンを載せることで、3Dビューで360度自由回転しながら鑑賞・セッティングを行えます。
 */
public class RcPitStationBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<RcPitStationBlock> CODEC = simpleCodec(RcPitStationBlock::new);

    // 作業台の形状（厚さ14ドットの台座天板）
    protected static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.875, 1.0);

    public RcPitStationBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RcPitStationBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RcPitStationBlockEntity station)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // 手持ちがRCビークルアイテムの場合: 作業台にセット（既存マシンと交換）してGUIを開く
        if (isVehicleItem(stack)) {
            ItemStack vehicleToStore = stack.copyWithCount(1);
            if (!level.isClientSide) {
                ItemStack oldVehicle = station.getStoredVehicle();
                station.setStoredVehicle(stack.split(1));
                level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.BLOCKS, 0.8F, 1.2F);
                if (!oldVehicle.isEmpty()) {
                    if (!player.getInventory().add(oldVehicle)) {
                        player.drop(oldVehicle, false);
                    }
                }
            } else {
                station.setStoredVehicle(vehicleToStore);
                openPitScreen(station, pos, vehicleToStore);
            }
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RcPitStationBlockEntity station)) {
            return InteractionResult.PASS;
        }

        // スニーク素手クリック: 載っているマシンを取り出す
        if (player.isShiftKeyDown() && !station.getStoredVehicle().isEmpty()) {
            if (!level.isClientSide) {
                ItemStack vehicle = station.getStoredVehicle();
                station.setStoredVehicle(ItemStack.EMPTY);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.8F, 1.0F);
                if (!player.getInventory().add(vehicle)) {
                    player.drop(vehicle, false);
                }
            } else {
                station.setStoredVehicle(ItemStack.EMPTY);
            }
            return InteractionResult.SUCCESS;
        }

        // 通常素手クリック: マシンが載っていればピット画面を開く
        if (!station.getStoredVehicle().isEmpty()) {
            if (level.isClientSide) {
                openPitScreen(station, pos, station.getStoredVehicle());
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RcPitStationBlockEntity station) {
                if (!station.getStoredVehicle().isEmpty()) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), station.getStoredVehicle());
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    public static boolean isVehicleItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var item = stack.getItem();
        return item instanceof RcCarItem
                || item instanceof RcPlaneItem
                || item instanceof RcBoatItem
                || item instanceof RcSubmarineItem
                || item instanceof RcTankItem
                || item instanceof DroneSpawnItem;
    }

    private void openPitScreen(RcPitStationBlockEntity station, BlockPos pos, ItemStack vehicleStack) {
        com.microdrone.mod.client.gui.RcPitStationOpener.open(pos, vehicleStack);
    }
}
