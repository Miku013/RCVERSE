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
 * 4段マルチRC展示ラックブロック
 * 各棚板にRCビークルやアイテムを直接配置・展示可能
 */
public class RcRackBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<RcRackBlock> CODEC = simpleCodec(RcRackBlock::new);

    // 2ブロック分の高さを持つラックの当たり判定
    protected static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 2.0, 1.0);

    public RcRackBlock(Properties properties) {
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
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RcRackBlockEntity(pos, state);
    }

    /**
     * クリックした高さ（Y座標）から該当する段（slot_0〜slot_3）を判定
     */
    private int getSlotIndexFromHit(BlockHitResult hitResult, BlockPos pos) {
        double hitY = hitResult.getLocation().y - pos.getY();
        if (hitY < 0.55) return 0;       // slot_0 (下段, Y=2)
        if (hitY < 1.05) return 1;       // slot_1 (中下段, Y=12)
        if (hitY < 1.55) return 2;       // slot_2 (中上段, Y=21)
        return 3;                        // slot_3 (最上段, Y=29)
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RcRackBlockEntity rack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        int slot = getSlotIndexFromHit(hitResult, pos);
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
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RcRackBlockEntity rack)) {
            return InteractionResult.PASS;
        }

        int slot = getSlotIndexFromHit(hitResult, pos);
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
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RcRackBlockEntity rack) {
                for (int i = 0; i < 4; i++) {
                    ItemStack st = rack.getItem(i);
                    if (!st.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), st);
                    }
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
