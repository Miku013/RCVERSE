package com.microdrone.mod.block;

import com.microdrone.mod.block.entity.DecalTableBlockEntity;
import com.microdrone.mod.block.entity.ModBlockEntities;
import com.microdrone.mod.item.RcCarItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DecalTableBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<DecalTableBlock> CODEC = simpleCodec(DecalTableBlock::new);

    // 作業台の当たり判定（脚〜天板）
    protected static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

    public DecalTableBlock(Properties properties) {
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
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // GeckoLib による 3D モデル描画を行うため ENTITYBLOCK_ANIMATED
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DecalTableBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof DecalTableBlockEntity table)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // 1. 手持ちがRC乗り物アイテムの場合: 作業台にセット（または既存乗り物と交換）
        if (isDecalApplicableItem(stack)) {
            ItemStack carToStore = stack.copyWithCount(1);
            if (!level.isClientSide) {
                ItemStack oldCar = table.getStoredCar();
                table.setStoredCar(stack.split(1));
                level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.BLOCKS, 0.8F, 1.2F);
                if (!oldCar.isEmpty()) {
                    if (!player.getInventory().add(oldCar)) {
                        player.drop(oldCar, false);
                    }
                }
            } else {
                // クライアント側でも即座に作業台へ車体をセット
                table.setStoredCar(carToStore);
                // 手に持っていた車体スタックを直接渡してエディターを開く (手持ちから開いたフラグ: true)
                openEditorScreen(table, pos, carToStore, true);
            }
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof DecalTableBlockEntity table)) {
            return InteractionResult.PASS;
        }

        // スニーク素手クリック: 載っているRCカーを取り出す
        if (player.isShiftKeyDown() && table.hasCar()) {
            if (!level.isClientSide) {
                ItemStack car = table.getStoredCar();
                table.setStoredCar(ItemStack.EMPTY);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.8F, 1.0F);
                if (!player.getInventory().add(car)) {
                    player.drop(car, false);
                }
            } else {
                table.setStoredCar(ItemStack.EMPTY);
            }
            return InteractionResult.SUCCESS;
        }

        // 通常素手クリック: 車が載っていればエディター画面を開く (手持ちからではない: false)
        if (table.hasCar()) {
            if (level.isClientSide) {
                openEditorScreen(table, pos, table.getStoredCar(), false);
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private void openEditorScreen(DecalTableBlockEntity table, BlockPos pos, ItemStack carStack, boolean openedFromHand) {
        net.neoforged.fml.loading.FMLEnvironment.dist.isClient();
        com.microdrone.mod.client.gui.DecalEditorOpener.open(pos, carStack, openedFromHand);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DecalTableBlockEntity table) {
                if (table.hasCar()) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), table.getStoredCar());
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    public static boolean isDecalApplicableItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var item = stack.getItem();
        return item instanceof RcCarItem
                || item instanceof com.microdrone.mod.item.DroneSpawnItem
                || item instanceof com.microdrone.mod.item.RcPlaneItem
                || item instanceof com.microdrone.mod.item.RcBoatItem
                || item instanceof com.microdrone.mod.item.RcSubmarineItem
                || item instanceof com.microdrone.mod.item.RcTankItem;
    }
}
