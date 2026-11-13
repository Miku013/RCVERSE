package com.microdrone.mod.block.road;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 道路生成・ヘアピンカーブの始点ブロック（Road Start Marker）
 * 進入方向（FACING）と始点位置を決定し、右クリックで設定画面を開きます。
 */
public class RoadStartBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<RoadStartBlock> CODEC = simpleCodec(RoadStartBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    // 薄いプレート型の選択判定（当たり判定は無しにして走行時の乗り上げ・引っかかりを防止）
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0);

    public RoadStartBlock(Properties properties) {
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
        // プレイヤーの向いている方向（道路の進入方向）
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RoadStartBlockEntity(pos, state);
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
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof RoadStartBlockEntity startBe) {
            if (level.isClientSide()) {
                // クライアント側で設定GUIを開く
                com.microdrone.mod.client.gui.RoadConfigScreen.open(pos);
            } else {
                // サーバー側: 終点が未リンクなら周囲32ブロックを自動探索
                if (startBe.getLinkedEndPos() == null) {
                    boolean found = startBe.autoFindNearestEndBlock(32);
                    if (found && startBe.getLinkedEndPos() != null) {
                        player.displayClientMessage(Component.translatable("chat.microdrone.road_linked_auto",
                                startBe.getLinkedEndPos().toShortString()), true);
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof RoadStartBlockEntity startBe) {
                BlockPos endPos = startBe.getLinkedEndPos();
                if (endPos != null && level.getBlockEntity(endPos) instanceof RoadEndBlockEntity endBe) {
                    endBe.setLinkedStartPos(null);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.microdrone.road_start_desc").withStyle(net.minecraft.ChatFormatting.GOLD));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}

