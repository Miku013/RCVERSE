package com.microdrone.mod.block;

import com.microdrone.mod.block.entity.RcWorkbenchBlockEntity;
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
 * RC工作台ブロック
 * RCマシン本体や各種パーツの専用組み立て（3x3クラフト）および天板上での展示機能（3Dプレビュー）を併せ持つ作業台
 */
public class RcWorkbenchBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<RcWorkbenchBlock> CODEC = simpleCodec(RcWorkbenchBlock::new);

    // 作業台の当たり判定（標準1ブロック高）
    protected static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

    public RcWorkbenchBlock(Properties properties) {
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
        // GeckoLib による 3D モデル描画
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RcWorkbenchBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RcWorkbenchBlockEntity table)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // スニーク右クリックで手にアイテムを持っている場合: 天板への展示アイテム設置・交換
        if (player.isShiftKeyDown() && !stack.isEmpty()) {
            if (!level.isClientSide) {
                ItemStack old = table.getDisplayedItem();
                ItemStack toDisplay = stack.split(1);
                table.setDisplayedItem(toDisplay);
                level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.BLOCKS, 0.8F, 1.2F);
                if (!old.isEmpty()) {
                    if (!player.getInventory().add(old)) {
                        player.drop(old, false);
                    }
                }
            }
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RcWorkbenchBlockEntity table)) {
            return InteractionResult.PASS;
        }

        // スニーク素手右クリック: 天板に展示されているアイテムを回収
        if (player.isShiftKeyDown() && player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            if (!level.isClientSide) {
                ItemStack stored = table.getDisplayedItem();
                if (!stored.isEmpty()) {
                    table.setDisplayedItem(ItemStack.EMPTY);
                    if (!player.getInventory().add(stored)) {
                        player.drop(stored, false);
                    }
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.8F, 1.0F);
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.SUCCESS;
        }

        // 通常右クリック: RC工作台GUI (3x3クラフト ＆ 展示スロット) を開く
        if (!level.isClientSide) {
            player.openMenu(table, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RcWorkbenchBlockEntity table) {
                if (!table.getDisplayedItem().isEmpty()) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), table.getDisplayedItem());
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
