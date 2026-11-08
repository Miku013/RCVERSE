package com.microdrone.mod.block;

import com.microdrone.mod.block.entity.ModularSlopeBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * つなげた分だけ自動でどこまでも浅く・滑らかになる動的モジュラースロープ坂ブロック
 * 前後に連続して設置すると、BlockEntityRendererにより何ブロックでも自動で完全な一直線スロープが動的生成されます。
 */
public class ModularSlopeBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<ModularSlopeBlock> CODEC = simpleCodec(ModularSlopeBlock::new);

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape OUTLINE_SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public ModularSlopeBlock(Properties properties) {
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ModularSlopeBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // BlockEntityRenderer による動的メッシュ描画に委ねる
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

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

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (level.getBlockEntity(pos) instanceof ModularSlopeBlockEntity be) {
            be.markDirtyChain();
            be.recalculateChain();
        }
        return state;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        notifyChain(level, pos, state.getValue(FACING));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        notifyChain(level, pos, state.getValue(FACING));
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            notifyChain(level, pos, state.getValue(FACING));
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private void notifyChain(Level level, BlockPos pos, Direction facing) {
        Direction backwardDir = facing.getOpposite();
        Direction forwardDir = facing;

        // 前後および左右の近隣スロープのチェーン状態を再計算
        for (int i = 1; i <= 64; i++) {
            BlockPos p = pos.relative(backwardDir, i);
            if (level.getBlockEntity(p) instanceof ModularSlopeBlockEntity be) {
                be.markDirtyChain();
                be.recalculateChain();
                if (!level.isClientSide()) {
                    level.sendBlockUpdated(p, level.getBlockState(p), level.getBlockState(p), 3);
                }
            } else {
                break;
            }
        }
        for (int i = 1; i <= 64; i++) {
            BlockPos p = pos.relative(forwardDir, i);
            if (level.getBlockEntity(p) instanceof ModularSlopeBlockEntity be) {
                be.markDirtyChain();
                be.recalculateChain();
                if (!level.isClientSide()) {
                    level.sendBlockUpdated(p, level.getBlockState(p), level.getBlockState(p), 3);
                }
            } else {
                break;
            }
        }

        // 左右の隣接ブロック（幅広道路接続）
        Direction leftDir = facing.getCounterClockWise();
        Direction rightDir = facing.getClockWise();
        BlockPos leftPos = pos.relative(leftDir);
        if (level.getBlockEntity(leftPos) instanceof ModularSlopeBlockEntity be) {
            be.markDirtyChain();
            be.recalculateChain();
            if (!level.isClientSide()) {
                level.sendBlockUpdated(leftPos, level.getBlockState(leftPos), level.getBlockState(leftPos), 3);
            }
        }
        BlockPos rightPos = pos.relative(rightDir);
        if (level.getBlockEntity(rightPos) instanceof ModularSlopeBlockEntity be) {
            be.markDirtyChain();
            be.recalculateChain();
            if (!level.isClientSide()) {
                level.sendBlockUpdated(rightPos, level.getBlockState(rightPos), level.getBlockState(rightPos), 3);
            }
        }

        if (level.getBlockEntity(pos) instanceof ModularSlopeBlockEntity be) {
            be.markDirtyChain();
            be.recalculateChain();
            if (!level.isClientSide()) {
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            }
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // RCカーに対しては独自メッシュレイキャストに100%委ねるためコライダーを透過
        if (context instanceof net.minecraft.world.phys.shapes.EntityCollisionContext ecc && ecc.getEntity() instanceof com.microdrone.mod.entity.RcCarEntity) {
            return Shapes.empty();
        }
        return OUTLINE_SHAPE;
    }

    /**
     * 三角形メッシュ物理衝突計算: 車両タイヤ位置からの精密レイキャスト
     * つなげたブロック数(totalChain)に応じて動的に傾斜角と高さを計算
     */
    public static RacingSlopeBlock.RampHitResult raycastSlopeMesh(BlockGetter level, BlockPos pos, BlockState state, double relX, double relZ) {
        Direction facing = state.getValue(FACING);

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

        int chainIndex = 0;
        int totalChain = 1;
        if (level != null && level.getBlockEntity(pos) instanceof ModularSlopeBlockEntity be) {
            chainIndex = be.getChainIndex();
            totalChain = be.getTotalChain();
        }

        double clampedZ = net.minecraft.util.Mth.clamp(localZ, 0.0, 1.0);
        double startH = (double) chainIndex / (double) totalChain;
        double endH = (double) (chainIndex + 1) / (double) totalChain;
        double deltaH = endH - startH; // 1.0 / totalChain

        double height = startH + clampedZ * deltaH;

        // 道路スロープはキッカーではなく滑らかな路面のためリップ判定はfalse
        boolean isLip = false;

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
        tooltipComponents.add(Component.translatable("tooltip.microdrone.modular_slope_desc").withStyle(net.minecraft.ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
