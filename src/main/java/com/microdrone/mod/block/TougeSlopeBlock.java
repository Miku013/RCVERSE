package com.microdrone.mod.block;

import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * どこまでも階段状・連続して伸ばせる「峠スロープ」ブロック
 * 2ブロックで1段上がる（勾配 約26.5°）の設計で、
 * 下段(LOWER: 0.0~0.5m)と上段(UPPER: 0.5~1.0m)が前後の配置から自動で判別され、
 * 何十ブロックでも完全シームレスに続く峠のワインディングロードを構築できます。
 */
public class TougeSlopeBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<TougeSlopeBlock> CODEC = simpleCodec(TougeSlopeBlock::new);

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<SlopeSegment> SEGMENT = EnumProperty.create("segment", SlopeSegment.class);
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    private static final VoxelShape OUTLINE_SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }

    public enum SlopeSegment implements StringRepresentable {
        LOWER("lower", 0.0, 0.5),
        UPPER("upper", 0.5, 1.0);

        private final String name;
        private final double startHeight;
        private final double endHeight;

        SlopeSegment(String name, double startHeight, double endHeight) {
            this.name = name;
            this.startHeight = startHeight;
            this.endHeight = endHeight;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public double getStartHeight() {
            return this.startHeight;
        }

        public double getEndHeight() {
            return this.endHeight;
        }
    }

    public TougeSlopeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SEGMENT, SlopeSegment.LOWER)
                .setValue(LEFT, true)
                .setValue(RIGHT, true));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SEGMENT, LEFT, RIGHT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        return calculateSlopeState(context.getLevel(), context.getClickedPos(), facing);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        return calculateSlopeState(level, pos, facing);
    }

    /**
     * 前後上下の隣接関係から、自動で LOWER(下段: 0.0~0.5m) か UPPER(上段: 0.5~1.0m) を判定
     */
    public BlockState calculateSlopeState(BlockGetter level, BlockPos pos, Direction facing) {
        Direction forwardDir = facing;                // 登り方向
        Direction backwardDir = facing.getOpposite(); // 下り方向

        SlopeSegment segment = SlopeSegment.LOWER;

        // 1. 水平後方に LOWER があれば自分は UPPER
        BlockState backState = level.getBlockState(pos.relative(backwardDir));
        if (isSameFacingTouge(backState, facing)) {
            if (backState.getValue(SEGMENT) == SlopeSegment.LOWER) {
                segment = SlopeSegment.UPPER;
            }
        }
        // 2. 水平前方に UPPER があれば自分は LOWER
        BlockState forwardState = level.getBlockState(pos.relative(forwardDir));
        if (isSameFacingTouge(forwardState, facing)) {
            if (forwardState.getValue(SEGMENT) == SlopeSegment.UPPER) {
                segment = SlopeSegment.LOWER;
            }
        }
        // 3. 斜め後下 (backward + below) に UPPER があれば、自分は新しい段の LOWER
        BlockState backBelowState = level.getBlockState(pos.relative(backwardDir).below());
        if (isSameFacingTouge(backBelowState, facing)) {
            if (backBelowState.getValue(SEGMENT) == SlopeSegment.UPPER) {
                segment = SlopeSegment.LOWER;
            }
        }
        // 4. 斜め前上 (forward + above) に LOWER があれば、自分はその手前段の UPPER
        BlockState forwardAboveState = level.getBlockState(pos.relative(forwardDir).above());
        if (isSameFacingTouge(forwardAboveState, facing)) {
            if (forwardAboveState.getValue(SEGMENT) == SlopeSegment.LOWER) {
                segment = SlopeSegment.UPPER;
            }
        }

        // 左右のガードレール/縁石接続判定
        Direction leftDir = facing.getClockWise();
        Direction rightDir = facing.getCounterClockWise();
        boolean hasLeftSlope = isSameFacingAndSegment(level.getBlockState(pos.relative(leftDir)), facing, segment);
        boolean hasRightSlope = isSameFacingAndSegment(level.getBlockState(pos.relative(rightDir)), facing, segment);

        return this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(SEGMENT, segment)
                .setValue(LEFT, !hasLeftSlope)
                .setValue(RIGHT, !hasRightSlope);
    }

    private boolean isSameFacingTouge(BlockState st, Direction targetFacing) {
        return (st.getBlock() instanceof TougeSlopeBlock) && st.getValue(FACING) == targetFacing;
    }

    private boolean isSameFacingAndSegment(BlockState st, Direction targetFacing, SlopeSegment targetSegment) {
        return (st.getBlock() instanceof TougeSlopeBlock) && st.getValue(FACING) == targetFacing && st.getValue(SEGMENT) == targetSegment;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // RCカーに対しては精密メッシュレイキャストに100%委ねるためコライダーを透過
        if (context instanceof net.minecraft.world.phys.shapes.EntityCollisionContext ecc && ecc.getEntity() instanceof com.microdrone.mod.entity.RcCarEntity) {
            return Shapes.empty();
        }
        return OUTLINE_SHAPE;
    }

    /**
     * 三角形メッシュ物理衝突計算: 車両タイヤ位置からの精密レイキャスト
     */
    public static RacingSlopeBlock.RampHitResult raycastSlopeMesh(BlockState state, double relX, double relZ) {
        Direction facing = state.getValue(FACING);
        SlopeSegment segment = state.getValue(SEGMENT);

        // 登り方向 (localZ: 0.0=下り側入口, 1.0=登り側出口) と 幅方向 (localX: 0.0~1.0)
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

        // 継ぎ目や端での微小な浮動小数点誤差による判定抜けを防ぐためマージンを持たせる
        if (localX < -0.05 || localX > 1.05 || localZ < -0.05 || localZ > 1.20) {
            return RacingSlopeBlock.RampHitResult.MISS;
        }

        double clampedZ = net.minecraft.util.Mth.clamp(localZ, 0.0, 1.0);
        double startH = segment.getStartHeight();
        double endH = segment.getEndHeight();
        double deltaH = endH - startH; // 常に 0.5 (26.5度勾配)

        // レイキャスト判定の高さ
        double height = startH + clampedZ * deltaH;

        // 峠道は飛び出すキッカーではなく滑らかに受け渡すためリップ判定はfalse
        boolean isLip = false;

        // 法線ベクトル (facing=SOUTHで+Zへ登る)
        // deltaH = 0.5, len = sqrt(1.0 + 0.25) = 1.11803
        double len = Math.sqrt(1.0 + deltaH * deltaH);
        double ny = 1.0 / len;
        double nz = -deltaH / len;

        net.minecraft.world.phys.Vec3 worldNormal = switch (facing) {
            case SOUTH -> new net.minecraft.world.phys.Vec3(0.0, ny, nz);
            case NORTH -> new net.minecraft.world.phys.Vec3(0.0, ny, -nz);
            case EAST  -> new net.minecraft.world.phys.Vec3(nz, ny, 0.0);
            case WEST  -> new net.minecraft.world.phys.Vec3(-nz, ny, 0.0);
            default    -> new net.minecraft.world.phys.Vec3(0, 1, 0);
        };

        return new RacingSlopeBlock.RampHitResult(true, height, worldNormal, clampedZ, isLip, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.microdrone.touge_slope_desc").withStyle(net.minecraft.ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}

