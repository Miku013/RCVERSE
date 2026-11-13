package com.microdrone.mod.block;

import com.microdrone.mod.block.entity.RcShowcaseBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * 10スロットRC大型ショウケースブロック (幅3マス × 高さ2マス × 奥行き1マス)
 * 上段5スロット (slot_0〜slot_4) ＋ 下段5スロット (slot_5〜slot_9) の計10台のRCマシンを3D展示可能
 */
public class RcShowcaseBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<RcShowcaseBlock> CODEC = simpleCodec(RcShowcaseBlock::new);
    public static final EnumProperty<ShowcasePart> PART = EnumProperty.create("part", ShowcasePart.class);

    protected static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

    public enum ShowcasePart implements StringRepresentable {
        CENTER_LOWER("center_lower"),
        LEFT_LOWER("left_lower"),
        RIGHT_LOWER("right_lower"),
        CENTER_UPPER("center_upper"),
        LEFT_UPPER("left_upper"),
        RIGHT_UPPER("right_upper");

        private final String name;
        ShowcasePart(String name) { this.name = name; }
        @Override public String getSerializedName() { return this.name; }
        public boolean isMaster() { return this == CENTER_LOWER; }
        public boolean isLower() { return this == CENTER_LOWER || this == LEFT_LOWER || this == RIGHT_LOWER; }
    }

    public RcShowcaseBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, ShowcasePart.CENTER_LOWER));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    public static BlockPos getMasterPos(BlockState state, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        ShowcasePart part = state.getValue(PART);
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        return switch (part) {
            case CENTER_LOWER -> pos;
            case CENTER_UPPER -> pos.below();
            case LEFT_LOWER -> pos.relative(right);
            case RIGHT_LOWER -> pos.relative(left);
            case LEFT_UPPER -> pos.below().relative(right);
            case RIGHT_UPPER -> pos.below().relative(left);
        };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        Direction facing = context.getHorizontalDirection().getOpposite();
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        if (pos.getY() >= level.getMaxBuildHeight() - 1) return null;

        BlockPos leftPos = pos.relative(left);
        BlockPos rightPos = pos.relative(right);

        // 6箇所（中央下、左下、右下、中央上、左上、右上）がすべて設置可能か検査
        if (level.getBlockState(pos.above()).canBeReplaced(context)
                && level.getBlockState(leftPos).canBeReplaced(context)
                && level.getBlockState(leftPos.above()).canBeReplaced(context)
                && level.getBlockState(rightPos).canBeReplaced(context)
                && level.getBlockState(rightPos.above()).canBeReplaced(context)) {
            return this.defaultBlockState().setValue(FACING, facing).setValue(PART, ShowcasePart.CENTER_LOWER);
        }

        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        BlockPos leftPos = pos.relative(left);
        BlockPos rightPos = pos.relative(right);

        level.setBlock(leftPos, state.setValue(PART, ShowcasePart.LEFT_LOWER), 3);
        level.setBlock(rightPos, state.setValue(PART, ShowcasePart.RIGHT_LOWER), 3);
        level.setBlock(pos.above(), state.setValue(PART, ShowcasePart.CENTER_UPPER), 3);
        level.setBlock(leftPos.above(), state.setValue(PART, ShowcasePart.LEFT_UPPER), 3);
        level.setBlock(rightPos.above(), state.setValue(PART, ShowcasePart.RIGHT_UPPER), 3);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos masterPos = getMasterPos(state, pos);
            Direction facing = state.getValue(FACING);
            Direction left = facing.getCounterClockWise();
            Direction right = facing.getClockWise();

            BlockPos[] parts = new BlockPos[]{
                    masterPos,
                    masterPos.relative(left),
                    masterPos.relative(right),
                    masterPos.above(),
                    masterPos.relative(left).above(),
                    masterPos.relative(right).above()
            };

            for (BlockPos p : parts) {
                BlockState s = level.getBlockState(p);
                if (s.is(this)) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 35);
                    level.levelEvent(player, 2001, p, Block.getId(s));
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (state.getValue(PART) == ShowcasePart.CENTER_LOWER) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof RcShowcaseBlockEntity showcase) {
                    for (int i = 0; i < 10; i++) {
                        ItemStack st = showcase.getItem(i);
                        if (!st.isEmpty()) {
                            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), st);
                        }
                    }
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // 中央下段（CENTER_LOWER）のみがGeckoLibモデルを3x2サイズで描画
        return state.getValue(PART) == ShowcasePart.CENTER_LOWER ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(PART) == ShowcasePart.CENTER_LOWER) {
            return new RcShowcaseBlockEntity(pos, state);
        }
        return null;
    }

    /**
     * クリック位置のワールド座標をショウケースのローカル座標系（Blockbench単位）に逆変換し、10スロットの該当位置を特定
     */
    private int getSlotIndexFromHit(BlockState state, BlockHitResult hitResult, BlockPos clickedPos) {
        BlockPos masterPos = getMasterPos(state, clickedPos);
        Direction facing = state.getValue(FACING);

        Vec3 hit = hitResult.getLocation();
        double clickY = hit.y - masterPos.getY(); // 0.0〜2.0m
        boolean isUpper = clickY >= 1.05;

        // 水平方向（X軸方向）のオフセットを計算（プレイヤー視線の左右とモデルスロットの配置を一致）
        Vec3 masterCenter = new Vec3(masterPos.getX() + 0.5, masterPos.getY(), masterPos.getZ() + 0.5);
        Vec3 delta = hit.subtract(masterCenter);
        double rad = Math.toRadians(-facing.toYRot());
        double localX = delta.x * Math.cos(rad) - delta.z * Math.sin(rad);
        double bbX = -localX * 16.0; // Blockbench単位 (-24〜+24) 左右反転補正

        if (isUpper) {
            if (bbX < -13.5) return 0;      // slot_0 (-18)
            if (bbX < -4.5) return 1;       // slot_1 (-9)
            if (bbX < 4.5) return 2;        // slot_2 (0)
            if (bbX < 13.5) return 3;       // slot_3 (9)
            return 4;                       // slot_4 (18)
        } else {
            if (bbX < -14.5) return 5;      // slot_5 (-18)
            if (bbX < -5.5) return 6;       // slot_6 (-11)
            if (bbX < 5.5) return 7;        // slot_7 (0)
            if (bbX < 14.5) return 8;       // slot_8 (11)
            return 9;                       // slot_9 (18)
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockPos masterPos = getMasterPos(state, pos);
        BlockEntity be = level.getBlockEntity(masterPos);
        if (!(be instanceof RcShowcaseBlockEntity showcase)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        int slot = getSlotIndexFromHit(state, hitResult, pos);
        ItemStack current = showcase.getItem(slot);

        if (!stack.isEmpty()) {
            if (!level.isClientSide) {
                if (current.isEmpty()) {
                    ItemStack toPlace = stack.split(1);
                    showcase.setItem(slot, toPlace);
                    level.playSound(null, masterPos, SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.BLOCKS, 0.8F, 1.2F);
                } else {
                    ItemStack toPlace = stack.split(1);
                    showcase.setItem(slot, toPlace);
                    if (!player.getInventory().add(current)) {
                        player.drop(current, false);
                    }
                    level.playSound(null, masterPos, SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.BLOCKS, 0.8F, 1.2F);
                }
            }
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockPos masterPos = getMasterPos(state, pos);
        BlockEntity be = level.getBlockEntity(masterPos);
        if (!(be instanceof RcShowcaseBlockEntity showcase)) {
            return InteractionResult.PASS;
        }

        int slot = getSlotIndexFromHit(state, hitResult, pos);
        ItemStack current = showcase.getItem(slot);

        if (!current.isEmpty()) {
            if (!level.isClientSide) {
                showcase.setItem(slot, ItemStack.EMPTY);
                if (!player.getInventory().add(current)) {
                    player.drop(current, false);
                }
                level.playSound(null, masterPos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }
}
