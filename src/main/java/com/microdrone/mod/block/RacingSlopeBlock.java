package com.microdrone.mod.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 1枚の薄い板を斜めに傾けたキッカーランプ（ジャンプ台）
 */
public class RacingSlopeBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<RacingSlopeBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    propertiesCodec(),
                    com.mojang.serialization.Codec.BOOL.fieldOf("is_boost").forGetter(RacingSlopeBlock::isBoost)
            ).apply(instance, RacingSlopeBlock::new)
    );

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final double RAMP_MAX_HEIGHT = 0.24; // 先端の高さ (約3.8ピクセル = 24cm)
    public static final double RAMP_LENGTH = 0.58;     // 板の水平長さ (約9.2ピクセル = 58cm)

    // 視線選択用のアウトライン (高さ約4ピクセル)
    private static final VoxelShape OUTLINE_SHAPE = Block.box(0, 0, 0, 16, 4.0, 16);

    // 向き別の斜面物理コライダー (8分割スライスで滑らかに車輪・AABBを支える)
    private static final Map<Direction, VoxelShape> COLLISION_SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            COLLISION_SHAPES.put(dir, buildRampCollisionShape(dir));
        }
    }

    private static VoxelShape buildRampCollisionShape(Direction facing) {
        VoxelShape shape = Shapes.empty();
        int steps = 8;
        double maxDist = 9.2; // 0.58ブロック相当 (9.2px)
        double maxHeight = 3.8; // 0.24ブロック相当 (3.8px)

        for (int i = 0; i < steps; i++) {
            double dStart = (i * maxDist) / steps;
            double dEnd = ((i + 1) * maxDist) / steps;
            double h = Math.max(0.5, ((i + 1) * maxHeight) / steps);

            VoxelShape slice = switch (facing) {
                case SOUTH -> Block.box(0, 0, dStart, 16, h, dEnd);
                case NORTH -> Block.box(0, 0, 16.0 - dEnd, 16, h, 16.0 - dStart);
                case EAST  -> Block.box(dStart, 0, 0, dEnd, h, 16);
                case WEST  -> Block.box(16.0 - dEnd, 0, 0, 16.0 - dStart, h, 16);
                default    -> Block.box(0, 0, 0, 16, h, 16);
            };
            shape = Shapes.or(shape, slice);
        }
        return shape;
    }

    private final boolean isBoost;

    public RacingSlopeBlock(Properties properties) {
        this(properties, false);
    }

    public RacingSlopeBlock(Properties properties, boolean isBoost) {
        super(properties);
        this.isBoost = isBoost;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
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
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // プレイヤーが向いている方向に登るように設置
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION_SHAPES.getOrDefault(state.getValue(FACING), OUTLINE_SHAPE);
    }

    /**
     * 車両サスペンション用: 1枚の斜め板上の高さを精密サンプリング (0.0m 〜 0.24m)
     */
    public static double sampleSlopeHeight(BlockState state, double relX, double relZ) {
        Direction facing = state.getValue(FACING);

        // 登り方向の進行距離 (0.0m=手前地面 〜 0.58m=先端リップ)
        double dist = switch (facing) {
            case SOUTH -> relZ;
            case NORTH -> 1.0 - relZ;
            case EAST  -> relX;
            case WEST  -> 1.0 - relX;
            default    -> 0.0;
        };

        if (dist <= 0.0) return 0.0;
        if (dist >= RAMP_LENGTH) return RAMP_MAX_HEIGHT;

        double progress = dist / RAMP_LENGTH;
        return progress * RAMP_MAX_HEIGHT;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        String key = this.isBoost ? "tooltip.microdrone.boost_ramp_desc" : "tooltip.microdrone.racing_slope_desc";
        tooltipComponents.add(Component.translatable(key).withStyle(net.minecraft.ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
