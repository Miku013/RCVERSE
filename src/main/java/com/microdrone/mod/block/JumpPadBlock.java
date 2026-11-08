package com.microdrone.mod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 踏むと上空へ力強く打ち上がるジャンプパッド
 * RCカーだけでなくプレイヤーや他Mobも大ジャンプ可能
 * 右クリックで跳躍パワーを3段階 (1:LOW, 2:MID, 3:HIGH) に切り替え可能
 */
public class JumpPadBlock extends Block {
    public static final MapCodec<JumpPadBlock> CODEC = simpleCodec(JumpPadBlock::new);
    public static final IntegerProperty POWER = IntegerProperty.create("power", 1, 3);

    // 高さ 2ドット (0.125m) の薄型プレート
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);

    public JumpPadBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWER, 2)); // デフォルトは中(MID: 約6m)
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    private InteractionResult cyclePower(BlockState state, Level level, BlockPos pos, Player player) {
        int current = state.getValue(POWER);
        int next = (current % 3) + 1;
        level.setBlock(pos, state.setValue(POWER, next), 3);

        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.8F, 0.7F + next * 0.35F);

        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.microdrone.jump_pad_power_" + next), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return cyclePower(state, level, pos, player);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        cyclePower(state, level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (entity instanceof com.microdrone.mod.entity.RcCarEntity) {
            // RCカーはtickCarPhysicsでより精密なサスペンション・姿勢角連動処理を行うため除外
            super.stepOn(level, pos, state, entity);
            return;
        }

        int power = state.getValue(POWER);
        // プレイヤー・通常Mob用の打ち上げ初速 (LOW: 約3m, MID: 約6m, HIGH: 約10m)
        double jumpVy = power == 1 ? 0.72 : (power == 2 ? 1.08 : 1.48);

        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.x, jumpVy, motion.z);
        entity.hasImpulse = true;
        entity.resetFallDistance();

        level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.0F, 1.2F + power * 0.2F);
        level.playSound(null, pos, SoundEvents.SLIME_BLOCK_FALL, SoundSource.BLOCKS, 0.7F, 1.5F);

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 6, 0.2, 0.1, 0.2, 0.05);
        }

        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        // ジャンプパッドに着地した場合は落下ダメージを完全無効化
        entity.causeFallDamage(fallDistance, 0.0F, level.damageSources().fall());
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, java.util.List<Component> tooltipComponents, net.minecraft.world.item.TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.microdrone.jump_pad_desc").withStyle(net.minecraft.ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
