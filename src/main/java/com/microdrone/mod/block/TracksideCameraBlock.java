package com.microdrone.mod.block;

import com.microdrone.mod.block.entity.ModBlockEntities;
import com.microdrone.mod.block.entity.TracksideCameraBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
 * コース中継カメラブロック (Trackside Camera)
 * - サーキットや峠道のコーナー・ジャンプ台に設置できるCCTV中継カメラ
 * - 周囲56ブロックのラジコンを検知し、電光掲示板へ中継映像ソースを提供する
 */
public class TracksideCameraBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<TracksideCameraBlock> CODEC = simpleCodec(TracksideCameraBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private static final VoxelShape BASE_SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 10.0, 13.0);
    private static final VoxelShape HEAD_SHAPE = Block.box(4.0, 10.0, 4.0, 12.0, 16.0, 12.0);
    private static final VoxelShape CAMERA_SHAPE = Shapes.or(BASE_SHAPE, HEAD_SHAPE);

    public TracksideCameraBlock(Properties properties) {
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CAMERA_SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TracksideCameraBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type == ModBlockEntities.TRACKSIDE_CAMERA_BE.get()) {
            return (lvl, pos, st, be) -> {
                if (be instanceof TracksideCameraBlockEntity cameraBe) {
                    cameraBe.tick(lvl, pos, st);
                }
            };
        }
        return null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TracksideCameraBlockEntity cameraBe) {
                // 名前札（Name Tag）を持っている場合はカメラ名を変更
                if (stack.is(net.minecraft.world.item.Items.NAME_TAG) && stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
                    String customName = stack.getHoverName().getString();
                    cameraBe.setCameraName(customName);
                    player.displayClientMessage(Component.literal("§a[中継カメラ] §fカメラ名を §e「" + customName + "」§f に設定しました！"), true);
                    return ItemInteractionResult.SUCCESS;
                }

                // 通常の右クリック: カメラ状態を表示
                String name = cameraBe.getCameraName();
                int carId = cameraBe.getNearestCarId();
                if (carId >= 0) {
                    player.displayClientMessage(Component.literal("§a[中継カメラ " + name + "] §fラジコンを自動追尾中 🔴 (角度: " + (int)cameraBe.getPanYaw() + "°)"), true);
                } else {
                    player.displayClientMessage(Component.literal("§7[中継カメラ " + name + "] §f待機中... (周囲56mのラジコンを探索中)"), true);
                }
            }
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.microdrone.trackside_camera_desc").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.microdrone.trackside_camera_usage").withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}

