package com.microdrone.mod.block;

import com.microdrone.mod.block.entity.RcRackBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * 4段マルチRC展示ラックブロック (2ブロック高マルチブロック)
 * 上下2ブロック分の当たり判定を持ち、各棚板 (slot_0 〜 slot_3) に直感的にマシンを展示・回収可能
 */
public class RcRackBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<RcRackBlock> CODEC = simpleCodec(RcRackBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    // 各半身の当たり判定（1ブロック分の立方体）
    protected static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

    public RcRackBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(context)) {
            return this.defaultBlockState()
                    .setValue(FACING, context.getHorizontalDirection().getOpposite())
                    .setValue(HALF, DoubleBlockHalf.LOWER);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return true;
        } else {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y && half == DoubleBlockHalf.LOWER == (direction == Direction.UP)) {
            return neighborState.is(this) && neighborState.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            DoubleBlockHalf half = state.getValue(HALF);
            BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(otherPos);
            if (otherState.is(this) && otherState.getValue(HALF) != half) {
                level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, otherPos, Block.getId(otherState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // 下段（LOWER）のみがGeckoLibモデルを2ブロック高で描画。上段（UPPER）は当たり判定・クリック判定のみ担当
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return new RcRackBlockEntity(pos, state);
        }
        return null;
    }

    /**
     * クリックした位置とブロック（上段/下段）から対応する段（0〜3）を判定
     */
    private int getSlotIndexFromHit(BlockState state, BlockHitResult hitResult, BlockPos pos) {
        double hitY = hitResult.getLocation().y - pos.getY(); // 該当ブロック内での 0.0〜1.0
        DoubleBlockHalf half = state.getValue(HALF);
        if (half == DoubleBlockHalf.LOWER) {
            return hitY < 0.5 ? 0 : 1; // 0: 1段目 (slot_0), 1: 2段目 (slot_1)
        } else {
            return hitY < 0.5 ? 2 : 3; // 2: 3段目 (slot_2), 3: 4段目 (slot_3 最上段)
        }
    }

    private BlockPos getEntityPos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockPos entityPos = getEntityPos(state, pos);
        BlockEntity be = level.getBlockEntity(entityPos);
        if (!(be instanceof RcRackBlockEntity rack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        int slot = getSlotIndexFromHit(state, hitResult, pos);
        ItemStack current = rack.getItem(slot);

        if (!stack.isEmpty()) {
            if (!level.isClientSide) {
                if (current.isEmpty()) {
                    // スロットが空の場合：手に持っているアイテムから1個を展示
                    ItemStack toPlace = stack.split(1);
                    rack.setItem(slot, toPlace);
                    level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.BLOCKS, 0.8F, 1.2F);
                } else {
                    // スロットに既にある場合：交換
                    ItemStack toPlace = stack.split(1);
                    rack.setItem(slot, toPlace);
                    if (!player.getInventory().add(current)) {
                        player.drop(current, false);
                    }
                    level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.BLOCKS, 0.8F, 1.2F);
                }
            }
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockPos entityPos = getEntityPos(state, pos);
        BlockEntity be = level.getBlockEntity(entityPos);
        if (!(be instanceof RcRackBlockEntity rack)) {
            return InteractionResult.PASS;
        }

        int slot = getSlotIndexFromHit(state, hitResult, pos);
        ItemStack current = rack.getItem(slot);

        // 素手右クリック：狙った段のアイテムを回収
        if (!current.isEmpty()) {
            if (!level.isClientSide) {
                rack.setItem(slot, ItemStack.EMPTY);
                if (!player.getInventory().add(current)) {
                    player.drop(current, false);
                }
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof RcRackBlockEntity rack) {
                    for (int i = 0; i < 4; i++) {
                        ItemStack st = rack.getItem(i);
                        if (!st.isEmpty()) {
                            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), st);
                        }
                    }
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
