package com.microdrone.mod.block;

import com.microdrone.mod.block.entity.ModBlockEntities;
import com.microdrone.mod.block.entity.RaceDisplayBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RaceDisplayBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<RaceDisplayBlock> CODEC = simpleCodec(RaceDisplayBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public RaceDisplayBlock(Properties properties) {
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

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // プレイヤーに向き合うように設置
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RaceDisplayBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type == ModBlockEntities.RACE_DISPLAY_BE.get()) {
            return (lvl, pos, st, be) -> {
                if (be instanceof RaceDisplayBlockEntity displayBe) {
                    displayBe.tick(lvl, pos, st);
                }
            };
        }
        return null;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            RaceDisplayBlockEntity.recalculateNearbyMultiblocks(level, pos, state.getValue(FACING));
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!state.is(oldState.getBlock())) {
            Direction facing = state.getValue(FACING);
            super.onRemove(state, level, pos, oldState, isMoving);
            if (!level.isClientSide) {
                RaceDisplayBlockEntity.recalculateNearbyMultiblocks(level, pos, facing);
            }
        } else {
            super.onRemove(state, level, pos, oldState, isMoving);
        }
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hitResult) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RaceDisplayBlockEntity displayBe) {
                BlockPos masterPos = displayBe.getMasterPos();
                BlockEntity mBe = level.getBlockEntity(masterPos);
                if (mBe instanceof RaceDisplayBlockEntity masterDisplay) {
                    String pilot = masterDisplay.getPilotName();
                    long bestTime = masterDisplay.getBestLapTimeMs();
                    String timeStr = bestTime > 0 ? String.format("%02d'%02d\"%03d", (bestTime / 60000), (bestTime % 60000) / 1000, bestTime % 1000) : "--'--\"---";
                    player.displayClientMessage(Component.translatable("chat.microdrone.race_display.record", pilot, timeStr), true);
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.2f);
                }
            }
        }
        return net.minecraft.world.ItemInteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.microdrone.race_display_desc").withStyle(net.minecraft.ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
