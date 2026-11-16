package com.microdrone.mod.item;

import com.microdrone.mod.entity.ModEntities;
import com.microdrone.mod.entity.RcTankEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

public class RcTankItem extends Item {
    public RcTankItem(Properties properties) {
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

        RcTankEntity tank = ModEntities.RC_TANK.get().create(serverLevel);
        if (tank != null) {
            String variantToSet = null;

            // 1. 金床でのカスタム名によるバリアント指定（ユーザーが名前を変えた場合を最優先で適用）
            if (itemStack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
                String customName = itemStack.getHoverName().getString().trim();
                com.microdrone.mod.config.tank.TankConfig config = com.microdrone.mod.config.tank.TankConfigManager.getConfig(customName);
                if (config != null && !"default".equalsIgnoreCase(config.id)) {
                    variantToSet = config.id;
                }
            }

            // 2. アイテムのカスタムデータ内の明示的な Variant タグ（金床で未指定の場合）
            var customData = itemStack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (customData != null) {
                net.minecraft.nbt.CompoundTag tag = customData.copyTag();
                if (variantToSet == null && tag.contains("Variant")) {
                    variantToSet = tag.getString("Variant");
                }
                if (tag.contains("Decals", 10)) {
                    tank.setDecals(com.microdrone.mod.decal.CarDecalData.deserializeDecals(tag.getCompound("Decals")));
                }
            }

            if (variantToSet != null) {
                tank.setVariantId(variantToSet);
            }

            tank.moveTo(x, y, z, context.getRotation(), 0.0F);
            serverLevel.addFreshEntity(tank);
            level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, spawnPos);
            level.playSound(null, spawnPos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.75F, 1.2F);

            if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
                itemStack.shrink(1);
            }
            return InteractionResult.CONSUME;
        }

        return InteractionResult.FAIL;
    }
}

