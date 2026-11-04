package com.microdrone.mod.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RacingSlopeBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<RacingSlopeBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    propertiesCodec(),
                    com.mojang.serialization.Codec.BOOL.fieldOf("is_boost").forGetter(RacingSlopeBlock::isBoost)
            ).apply(instance, RacingSlopeBlock::new)
    );

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<SlopeShape> SHAPE = EnumProperty.create("shape", SlopeShape.class);

    private final boolean isBoost;

    public enum SlopeShape implements StringRepresentable {
        STEEP("steep"), // 単体急坂 (0 -> 8px)
        START("start"), // 2連結の手前 (0 -> 4px)
        END("end");     // 2連結の奥 (4 -> 8px)

        private final String name;

        SlopeShape(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public RacingSlopeBlock(Properties properties) {
        this(properties, false);
    }

    public RacingSlopeBlock(Properties properties, boolean isBoost) {
        super(properties);
        this.isBoost = isBoost;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SHAPE, SlopeShape.STEEP));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public boolean isBoost() {
        return this.isBoost;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // プレイヤーが向いている方向に登るように設置
        Direction facing = context.getHorizontalDirection();
        BlockPos pos = context.getClickedPos();
        LevelAccessor level = context.getLevel();

        SlopeShape shape = calculateShape(level, pos, facing);
        return this.defaultBlockState().setValue(FACING, facing).setValue(SHAPE, shape);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        if (direction == facing || direction == facing.getOpposite()) {
            SlopeShape shape = calculateShape(level, pos, facing);
            return state.setValue(SHAPE, shape);
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /**
     * 前後のスロープ接続から形状を自動判定
     */
    private SlopeShape calculateShape(LevelAccessor level, BlockPos pos, Direction facing) {
        BlockPos aheadPos = pos.relative(facing);
        BlockPos behindPos = pos.relative(facing.getOpposite());

        boolean hasAhead = isCompatibleSlope(level.getBlockState(aheadPos), facing);
        boolean hasBehind = isCompatibleSlope(level.getBlockState(behindPos), facing);

        if (hasAhead && !hasBehind) {
            return SlopeShape.START; // 2連結の手前
        } else if (hasBehind && !hasAhead) {
            return SlopeShape.END;   // 2連結の奥
        }
        return SlopeShape.STEEP;     // 単体
    }

    private boolean isCompatibleSlope(BlockState state, Direction facing) {
        return state.getBlock() instanceof RacingSlopeBlock && state.getValue(FACING) == facing;
    }

    // === 当たり判定 (VoxelShape) ===
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        SlopeShape shape = state.getValue(SHAPE);
        // ハーフブロックの高さ (0〜8px) をベースとした形状
        double maxH = (shape == SlopeShape.START) ? 4.0 / 16.0 : 8.0 / 16.0;
        return Block.box(0, 0, 0, 16, maxH * 16.0, 16);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    /**
     * 車両などの物理サンプリング用: スロープ上の精密高さを計算 (ブロック下面からの高さ 0.0〜0.5m)
     */
    public static double sampleSlopeHeight(BlockState state, double relX, double relZ) {
        Direction facing = state.getValue(FACING);
        SlopeShape shape = state.getValue(SHAPE);

        // 登り方向 (progress: 0.0=手前/低い, 1.0=奥/高い)
        double progress = switch (facing) {
            case SOUTH -> relZ;
            case NORTH -> 1.0 - relZ;
            case EAST  -> relX;
            case WEST  -> 1.0 - relX;
            default    -> 0.0;
        };
        progress = Math.max(0.0, Math.min(1.0, progress));

        // 形状に応じた高さ (0.0〜0.5m)
        return switch (shape) {
            case STEEP -> progress * 0.50;                     // 0 -> 8px (0.5m)
            case START -> progress * 0.25;                     // 0 -> 4px (0.25m)
            case END   -> 0.25 + (progress * 0.25);            // 4 -> 8px (0.25m -> 0.5m)
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        String key = this.isBoost ? "tooltip.microdrone.boost_ramp_desc" : "tooltip.microdrone.racing_slope_desc";
        tooltipComponents.add(Component.translatable(key).withStyle(net.minecraft.ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
