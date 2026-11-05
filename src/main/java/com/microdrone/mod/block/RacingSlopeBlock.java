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

    // 2段階カーブキッカーの寸法定数
    public static final double STAGE1_END_Z = 0.59375;  // 1段目終点 (9.5ピクセル = 約0.59m)
    public static final double STAGE1_HEIGHT = 0.2375;  // 1段目高さ (3.8ピクセル = 約0.24m, 22.5°)
    public static final double STAGE2_END_Z = 0.95;     // 2段目先端リップ (15.2ピクセル = 0.95m)
    public static final double STAGE2_HEIGHT = 0.60;    // 2段目先端高さ (9.6ピクセル = 0.60m, 45.0°)

    public static final double RAMP_MAX_HEIGHT = STAGE2_HEIGHT;
    public static final double RAMP_LENGTH = STAGE2_END_Z;

    // 視線選択用のアウトライン (高さ約10ピクセル)
    private static final VoxelShape OUTLINE_SHAPE = Block.box(0, 0, 0, 16, 10.0, 16);

    // 向き別の2段階斜面物理コライダー (1段目6スライス + 2段目6スライス + 背面台座)
    private static final Map<Direction, VoxelShape> COLLISION_SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            COLLISION_SHAPES.put(dir, buildRampCollisionShape(dir));
        }
    }

    private static VoxelShape buildRampCollisionShape(Direction facing) {
        VoxelShape shape = Shapes.empty();

        double z1 = 9.5;  // 1段目終点px
        double h1 = 3.8;  // 1段目高さpx
        double z2 = 15.2; // 2段目終点px
        double h2 = 9.6;  // 2段目高さpx

        int steps1 = 6;
        // 1段目: 緩傾斜スライス (0.0px 〜 9.5px, 高さ0.5px〜3.8px)
        for (int i = 0; i < steps1; i++) {
            double dStart = (i * z1) / steps1;
            double dEnd = ((i + 1) * z1) / steps1;
            double h = Math.max(0.5, ((i + 1) * h1) / steps1);

            VoxelShape slice = switch (facing) {
                case SOUTH -> Block.box(0, 0, dStart, 16, h, dEnd);
                case NORTH -> Block.box(0, 0, 16.0 - dEnd, 16, h, 16.0 - dStart);
                case EAST  -> Block.box(dStart, 0, 0, dEnd, h, 16);
                case WEST  -> Block.box(16.0 - dEnd, 0, 0, 16.0 - dStart, h, 16);
                default    -> Block.box(0, 0, 0, 16, h, 16);
            };
            shape = Shapes.or(shape, slice);
        }

        int steps2 = 6;
        // 2段目: 急傾斜キッカースライス (9.5px 〜 15.2px, 高さ3.8px〜9.6px)
        for (int i = 0; i < steps2; i++) {
            double dStart = z1 + (i * (z2 - z1)) / steps2;
            double dEnd = z1 + ((i + 1) * (z2 - z1)) / steps2;
            double h = h1 + ((i + 1) * (h2 - h1)) / steps2;

            VoxelShape slice = switch (facing) {
                case SOUTH -> Block.box(0, 0, dStart, 16, h, dEnd);
                case NORTH -> Block.box(0, 0, 16.0 - dEnd, 16, h, 16.0 - dStart);
                case EAST  -> Block.box(dStart, 0, 0, dEnd, h, 16);
                case WEST  -> Block.box(16.0 - dEnd, 0, 0, 16.0 - dStart, h, 16);
                default    -> Block.box(0, 0, 0, 16, h, 16);
            };
            shape = Shapes.or(shape, slice);
        }

        // 先端リップ背後 (15.2px 〜 16.0px) の台座 (高さ9.6px)
        VoxelShape backPlate = switch (facing) {
            case SOUTH -> Block.box(0, 0, z2, 16, h2, 16);
            case NORTH -> Block.box(0, 0, 0, 16, h2, 16.0 - z2);
            case EAST  -> Block.box(z2, 0, 0, 16, h2, 16);
            case WEST  -> Block.box(0, 0, 0, 16.0 - z2, h2, 16);
            default    -> Block.box(0, 0, 0, 16, h2, 16);
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
        // RCカーに対しては段階型（階段スライス）ヒットボックスをバイパス！
        // RCカー独自の「レイキャスト × 三角形メッシュ衝突方式」に100%委ねることで、段差引っかかりと潜り込みを完全根絶
        if (context instanceof net.minecraft.world.phys.shapes.EntityCollisionContext ecc && ecc.getEntity() instanceof com.microdrone.mod.entity.RcCarEntity) {
            return Shapes.empty();
        }
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
     * 三角形メッシュ衝突方式: タイヤ位置からのレイキャストで正確な路面高さと法線ベクトルを算出 (2段階カーブキッカー対応)
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

        double height;
        net.minecraft.world.phys.Vec3 worldNormal;
        double progress;
        boolean isLip;

        if (localZ <= STAGE1_END_Z) {
            // === 1段階目: 緩傾斜エントリー (0.0m 〜 0.59m, 傾斜22.5°) ===
            double p1 = Math.max(0.0, localZ / STAGE1_END_Z);
            height = p1 * STAGE1_HEIGHT;
            progress = p1 * 0.5; // 前半 0.0 〜 0.5
            isLip = false;

            double sin1 = 0.3827; // sin(22.5°)
            double cos1 = 0.9239; // cos(22.5°)
            worldNormal = switch (facing) {
                case SOUTH -> new net.minecraft.world.phys.Vec3(0.0, cos1, -sin1);
                case NORTH -> new net.minecraft.world.phys.Vec3(0.0, cos1, sin1);
                case EAST  -> new net.minecraft.world.phys.Vec3(-sin1, cos1, 0.0);
                case WEST  -> new net.minecraft.world.phys.Vec3(sin1, cos1, 0.0);
                default    -> new net.minecraft.world.phys.Vec3(0, 1, 0);
            };
        } else if (localZ <= STAGE2_END_Z) {
            // === 2段階目: 急傾斜キッカーリップ (0.59m 〜 0.95m, 傾斜45.0°) ===
            double p2 = (localZ - STAGE1_END_Z) / (STAGE2_END_Z - STAGE1_END_Z);
            height = STAGE1_HEIGHT + p2 * (STAGE2_HEIGHT - STAGE1_HEIGHT);
            progress = 0.5 + p2 * 0.5; // 後半 0.5 〜 1.0
            isLip = (progress >= 0.85);

            double sin2 = 0.7071; // sin(45.0°)
            double cos2 = 0.7071; // cos(45.0°)
            worldNormal = switch (facing) {
                case SOUTH -> new net.minecraft.world.phys.Vec3(0.0, cos2, -sin2);
                case NORTH -> new net.minecraft.world.phys.Vec3(0.0, cos2, sin2);
                case EAST  -> new net.minecraft.world.phys.Vec3(-sin2, cos2, 0.0);
                case WEST  -> new net.minecraft.world.phys.Vec3(sin2, cos2, 0.0);
                default    -> new net.minecraft.world.phys.Vec3(0, 1, 0);
            };
        } else {
            // === 先端リップ頂上台座 (0.95m 〜 1.0m) ===
            height = STAGE2_HEIGHT;
            progress = 1.0;
            isLip = true;

            double sin2 = 0.7071; // sin(45.0°)
            double cos2 = 0.7071; // cos(45.0°)
            worldNormal = switch (facing) {
                case SOUTH -> new net.minecraft.world.phys.Vec3(0.0, cos2, -sin2);
                case NORTH -> new net.minecraft.world.phys.Vec3(0.0, cos2, sin2);
                case EAST  -> new net.minecraft.world.phys.Vec3(-sin2, cos2, 0.0);
                case WEST  -> new net.minecraft.world.phys.Vec3(sin2, cos2, 0.0);
                default    -> new net.minecraft.world.phys.Vec3(0, 1, 0);
            };
        }

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
