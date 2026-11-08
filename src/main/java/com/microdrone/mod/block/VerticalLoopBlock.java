package com.microdrone.mod.block;

import com.microdrone.mod.block.entity.ModBlockEntities;
import com.microdrone.mod.block.entity.VerticalLoopBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 360°縦ループ（Loop-the-Loop）ブロック
 * 64分割の精密円形モデルと内周走行物理に対応したアトラクションコース
 */
public class VerticalLoopBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<VerticalLoopBlock> CODEC = simpleCodec(VerticalLoopBlock::new);

    // 設置ベース部（底面プレートと支柱足場）の当たり判定
    private static final VoxelShape BASE_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0);

    public VerticalLoopBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BASE_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // RCカーに対しては独自ループ物理に100%委ねるためコライダーを透過（段差衝突・ガタつきを完全防止）
        if (context instanceof net.minecraft.world.phys.shapes.EntityCollisionContext ecc && ecc.getEntity() instanceof com.microdrone.mod.entity.RcCarEntity) {
            return Shapes.empty();
        }
        return BASE_SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VerticalLoopBlockEntity(pos, state);
    }
}

