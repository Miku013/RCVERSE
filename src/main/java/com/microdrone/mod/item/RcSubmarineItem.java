package com.microdrone.mod.item;

import com.microdrone.mod.entity.ModEntities;
import com.microdrone.mod.entity.RcSubmarineEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class RcSubmarineItem extends Item {
    public RcSubmarineItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        ItemStack itemStack = context.getItemInHand();
        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        BlockPos spawnPos = clickedPos.relative(clickedFace);

        double x = spawnPos.getX() + 0.5;
        double y = spawnPos.getY();
        double z = spawnPos.getZ() + 0.5;

        RcSubmarineEntity submarine = ModEntities.RC_SUBMARINE.get().create(serverLevel);
        if (submarine != null) {
            applyVariant(itemStack, submarine);
            submarine.moveTo(x, y, z, context.getRotation(), 0.0F);
            serverLevel.addFreshEntity(submarine);
            level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, spawnPos);
            level.playSound(null, spawnPos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.8F, 1.2F);

            if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
                itemStack.shrink(1);
            }
            return InteractionResult.CONSUME;
        }

        return InteractionResult.FAIL;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        BlockHitResult hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (hitResult.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(itemStack);
        }
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = hitResult.getBlockPos();
            if (level.getFluidState(hitPos).isEmpty()) {
                // 水でない通常ブロックの場合は useOn に任せる
                return InteractionResultHolder.pass(itemStack);
            }

            if (!level.isClientSide) {
                double x = hitResult.getLocation().x;
                double y = hitResult.getLocation().y;
                double z = hitResult.getLocation().z;

                RcSubmarineEntity submarine = ModEntities.RC_SUBMARINE.get().create(level);
                if (submarine != null) {
                    applyVariant(itemStack, submarine);
                    submarine.moveTo(x, y, z, player.getYRot(), 0.0F);
                    level.addFreshEntity(submarine);
                    level.gameEvent(player, GameEvent.ENTITY_PLACE, hitPos);
                    level.playSound(null, hitPos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.8F, 1.2F);

                    if (!player.getAbilities().instabuild) {
                        itemStack.shrink(1);
                    }
                    player.awardStat(Stats.ITEM_USED.get(this));
                    return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
                }
            }
            return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
        }
        return InteractionResultHolder.pass(itemStack);
    }

    private void applyVariant(ItemStack itemStack, RcSubmarineEntity submarine) {
        var customData = itemStack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData != null) {
            net.minecraft.nbt.CompoundTag tag = customData.copyTag();
            if (tag.contains("Variant")) {
                submarine.setVariantId(tag.getString("Variant"));
            }
            if (tag.contains("Decals", 10)) {
                submarine.setDecals(com.microdrone.mod.decal.CarDecalData.deserializeDecals(tag.getCompound("Decals")));
            }
        } else if (itemStack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            String customName = itemStack.getHoverName().getString().trim().toLowerCase();
            if (com.microdrone.mod.config.submarine.SubmarineConfigManager.getAllConfigs().containsKey(customName)) {
                submarine.setVariantId(customName);
            }
        }
    }
}

