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
        int steps = 10;
        double maxDist = 9.2; // 0.58ブロック相当 (9.2px)
        double maxHeight = 3.8; // 0.24ブロック相当 (3.8px)

        // 登り斜面スライス (0.0px 〜 9.2px)
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

        // 先端リップの背後 (9.2px 〜 16.0px) の中空落とし込み防止台座 (高さ3.8px)
        VoxelShape backPlate = switch (facing) {
            case SOUTH -> Block.box(0, 0, maxDist, 16, maxHeight, 16);
            case NORTH -> Block.box(0, 0, 0, 16, maxHeight, 16.0 - maxDist);
            case EAST  -> Block.box(maxDist, 0, 0, 16, maxHeight, 16);
            case WEST  -> Block.box(0, 0, 0, 16.0 - maxDist, maxHeight, 16);
            default    -> Block.box(0, 0, 0, 16, maxHeight, 16);
        };
        shape = Shapes.or(shape, backPlate);

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
     * 三角形メッシュ物理衝突結果
     */
    public static class RampHitResult {
        public final boolean hit;
        public final double height;
        public final net.minecraft.world.phys.Vec3 normal;
        public final double progress; // 0.0=手前, 1.0=先端リップ
        public final boolean isLip;   // 先端ジャンプ離陸ゾーン (progress >= 0.85)
        public final boolean isBoost; // ブーストジャンプ台か

        public RampHitResult(boolean hit, double height, net.minecraft.world.phys.Vec3 normal, double progress, boolean isLip, boolean isBoost) {
            this.hit = hit;
            this.height = height;
            this.normal = normal;
            this.progress = progress;
            this.isLip = isLip;
            this.isBoost = isBoost;
        }

        public static final RampHitResult MISS = new RampHitResult(false, 0.0, new net.minecraft.world.phys.Vec3(0, 1, 0), 0.0, false, false);
    }

    /**
     * 三角形メッシュ衝突方式: タイヤ位置からのレイキャストで正確な路面高さと法線ベクトルを算出
     */
    public static RampHitResult raycastSlopeMesh(BlockState state, double relX, double relZ) {
        Direction facing = state.getValue(FACING);
        boolean isBoost = (state.getBlock() instanceof RacingSlopeBlock rsb) && rsb.isBoost();

        // 登り方向 (localZ) と 幅方向 (localX) への変換
        double localZ = switch (facing) {
            case SOUTH -> relZ;
            case NORTH -> 1.0 - relZ;
            case EAST  -> relX;
            case WEST  -> 1.0 - relX;
            default    -> 0.0;
        };
        double localX = switch (facing) {
            case SOUTH -> relX;
            case NORTH -> 1.0 - relX;
            case EAST  -> 1.0 - relZ;
            case WEST  -> relZ;
            default    -> 0.0;
        };

        // 板のポリゴン境界内にあるかチェック (幅 0.0〜1.0, 進行方向 0.0〜1.0)
        if (localX < 0.0 || localX > 1.0 || localZ < 0.0 || localZ > 1.0) {
            return RampHitResult.MISS;
        }

        // 斜面の法線ベクトル (22.5度の垂直上向き)
        double sinTheta = 0.3827; // sin(22.5°)
        double cosTheta = 0.9239; // cos(22.5°)

        net.minecraft.world.phys.Vec3 worldNormal = switch (facing) {
            case SOUTH -> new net.minecraft.world.phys.Vec3(0.0, cosTheta, -sinTheta);
            case NORTH -> new net.minecraft.world.phys.Vec3(0.0, cosTheta, sinTheta);
            case EAST  -> new net.minecraft.world.phys.Vec3(-sinTheta, cosTheta, 0.0);
            case WEST  -> new net.minecraft.world.phys.Vec3(sinTheta, cosTheta, 0.0);
            default    -> new net.minecraft.world.phys.Vec3(0, 1, 0);
        };

        // 先端リップ以降 (localZ >= RAMP_LENGTH): 先端高さを維持して空中に飛び出させる
        if (localZ >= RAMP_LENGTH) {
            return new RampHitResult(true, RAMP_MAX_HEIGHT, worldNormal, 1.0, true, isBoost);
        }

        // 三角形メッシュの高さ関数 (完全な一次関数)
        double progress = Math.max(0.0, localZ / RAMP_LENGTH);
        double height = progress * RAMP_MAX_HEIGHT;
        boolean isLip = progress >= 0.82; // 先端付近 (約0.48m以降)

        return new RampHitResult(true, height, worldNormal, progress, isLip, isBoost);
    }

    /**
     * 車両サスペンション用: 1枚の斜め板上の高さを精密サンプリング (0.0m 〜 0.24m)
     */
    public static double sampleSlopeHeight(BlockState state, double relX, double relZ) {
        RampHitResult result = raycastSlopeMesh(state, relX, relZ);
        if (result.hit) {
            return result.height;
        }
        return 0.0;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        String key = this.isBoost ? "tooltip.microdrone.boost_ramp_desc" : "tooltip.microdrone.racing_slope_desc";
        tooltipComponents.add(Component.translatable(key).withStyle(net.minecraft.ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
