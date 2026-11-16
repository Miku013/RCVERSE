package com.microdrone.mod.block.miniature;

import com.microdrone.mod.item.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 1ブロック内に複数のコース小物を配置できる土台ブロック
 */
public class MiniaturePlacementBlock extends BaseEntityBlock {
    public static final MapCodec<MiniaturePlacementBlock> CODEC = simpleCodec(MiniaturePlacementBlock::new);
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty LIT =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;

    public MiniaturePlacementBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MiniaturePlacementBlockEntity placementBe) {
            boolean hasLight = placementBe.getAllSlots().stream().anyMatch(slot -> "checkpoint_plate".equals(slot.getMiniatureId()));
            if (hasLight) return 14;
        }
        if (state.hasProperty(LIT) && state.getValue(LIT)) {
            return 14;
        }
        return 0;
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MiniaturePlacementBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MiniaturePlacementBlockEntity placementBe) {
            return placementBe.getOutlineShape();
        }
        return MiniaturePlacementBlockEntity.BASE_PLATE_OUTLINE_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MiniaturePlacementBlockEntity placementBe) {
            return placementBe.getCollisionShape();
        }
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    /**
     * 手持ちアイテム使用時の処理（Shift右クリック回収、コース小物設置など）
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        // Shift右クリック時は手持ちアイテムに関係なく「小物の個別回収」を最優先（積み重なりや不発を防止）
        if (player.isShiftKeyDown()) {
            InteractionResult result = tryCollectMiniature(state, level, pos, player, hitResult);
            if (result.consumesAction()) {
                return ItemInteractionResult.SUCCESS;
            }
        }

        // コース小物所持時の設置はアイテムクラス（MiniatureItem）側で処理を優先
        if (stack.getItem() instanceof MiniatureItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * 素手または未処理アイテム時の処理（Shift右クリックで回収、素手通常右クリックで回転）
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.isShiftKeyDown()) {
            // Shift右クリック: 狙った小物を個別に回収
            return tryCollectMiniature(state, level, pos, player, hitResult);
        } else {
            // 素手通常右クリック（スニークなし）: スロットの小物を45度回転
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MiniaturePlacementBlockEntity placementBe) {
                double localX = hitResult.getLocation().x - pos.getX();
                double localZ = hitResult.getLocation().z - pos.getZ();
                int slotId = MiniaturePlacementData.getSlotFromHit(localX, localZ);

                // アシスト判定
                if (!placementBe.hasSlot(slotId) && placementBe.getSlotCount() > 0) {
                    int nearestSlot = -1;
                    double nearestDistSq = Double.MAX_VALUE;
                    for (MiniaturePlacementData slotData : placementBe.getAllSlots()) {
                        double cx = MiniaturePlacementData.getSlotCenterX(slotData.getSlotId());
                        double cz = MiniaturePlacementData.getSlotCenterZ(slotData.getSlotId());
                        double distSq = (localX - cx) * (localX - cx) + (localZ - cz) * (localZ - cz);
                        if (distSq < nearestDistSq) {
                            nearestDistSq = distSq;
                            nearestSlot = slotData.getSlotId();
                        }
                    }
                    if (nearestSlot != -1 && nearestDistSq <= 0.16) {
                        slotId = nearestSlot;
                    }
                }

                if (placementBe.hasSlot(slotId)) {
                    if (!level.isClientSide) {
                        placementBe.rotateMiniature(slotId, 45.0f);
                        level.playSound(null, pos, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 0.5f, 1.5f);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            }
        }

        return InteractionResult.PASS;
    }

    /**
     * 小物の個別回収ロジック（Shift右クリック用）
     */
    private InteractionResult tryCollectMiniature(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MiniaturePlacementBlockEntity placementBe)) {
            return InteractionResult.PASS;
        }

        // 空の土台ブロックが残っている場合は、土台自体を消去（重複スタック解消）
        if (placementBe.getSlotCount() == 0) {
            if (!level.isClientSide) {
                level.removeBlock(pos, false);
                level.playSound(null, pos, SoundEvents.LANTERN_BREAK, SoundSource.BLOCKS, 0.8f, 1.2f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // クリックしたローカル座標からスロットIDを計算
        double localX = hitResult.getLocation().x - pos.getX();
        double localZ = hitResult.getLocation().z - pos.getZ();
        int slotId = MiniaturePlacementData.getSlotFromHit(localX, localZ);

        // クリック判定アシスト: 直撃スロットが空の場合、近傍（約0.4m以内）の配置済み小物を自動補正
        if (!placementBe.hasSlot(slotId) && placementBe.getSlotCount() > 0) {
            int nearestSlot = -1;
            double nearestDistSq = Double.MAX_VALUE;
            for (MiniaturePlacementData slotData : placementBe.getAllSlots()) {
                double cx = MiniaturePlacementData.getSlotCenterX(slotData.getSlotId());
                double cz = MiniaturePlacementData.getSlotCenterZ(slotData.getSlotId());
                double distSq = (localX - cx) * (localX - cx) + (localZ - cz) * (localZ - cz);
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearestSlot = slotData.getSlotId();
                }
            }
            if (nearestSlot != -1 && nearestDistSq <= 0.16) {
                slotId = nearestSlot;
            }
        }

        if (placementBe.hasSlot(slotId)) {
            if (!level.isClientSide) {
                MiniaturePlacementData removed = placementBe.removeMiniature(slotId);
                if (removed != null) {
                    // 該当のコース小物をインベントリに追加（満杯ならドロップ）
                    ItemStack drop = new ItemStack(ModItems.getItemForMiniature(removed.getMiniatureId()));
                    if (!player.getInventory().add(drop)) {
                        player.drop(drop, false);
                    }
                    level.playSound(null, pos, SoundEvents.LANTERN_BREAK, SoundSource.BLOCKS, 0.8f, 1.2f);

                    // 全スロットが空になった場合、土台ブロック自体も消去
                    if (placementBe.getSlotCount() == 0) {
                        level.removeBlock(pos, false);
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    /**
     * ブロック撤去時: 中に入っている全小物をドロップ
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MiniaturePlacementBlockEntity placementBe && !level.isClientSide) {
                for (MiniaturePlacementData slot : placementBe.getAllSlots()) {
                    ItemStack drop = new ItemStack(ModItems.getItemForMiniature(slot.getMiniatureId()));
                    popResource(level, pos, drop);
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}

