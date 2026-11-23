package com.microdrone.mod.item;

import com.microdrone.mod.entity.ModEntities;
import com.microdrone.mod.entity.RcCarEntity;
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

public class RcCarItem extends Item {
    public RcCarItem(Properties properties) {
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

        RcCarEntity car = ModEntities.RC_CAR.get().create(serverLevel);
        if (car != null) {
            String variantToSet = null;

            // 1. 金床でのカスタム名によるバリアント指定（最優先で適用）
            if (itemStack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
                String customName = itemStack.getHoverName().getString().trim();
                var config = com.microdrone.mod.config.car.CarConfigManager.getConfig(customName);
                if (config != null && !"default".equalsIgnoreCase(config.id)) {
                    variantToSet = config.id;
                }
            }

            // 2. アイテムのカスタムデータ内の明示的な Variant タグや塗装色
            var customData = itemStack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (customData != null) {
                net.minecraft.nbt.CompoundTag tag = customData.copyTag();
                if (variantToSet == null && tag.contains("Variant")) {
                    variantToSet = tag.getString("Variant");
                }
                if (tag.contains("BodyColor")) {
                    car.setBodyColor(tag.getInt("BodyColor"));
                }
                if (tag.contains("Decals", 10)) {
                    car.setDecals(RcCarEntity.deserializeDecals(tag.getCompound("Decals")));
                }
                if (tag.contains("FrontTire")) {
                    car.setFrontTireType(tag.getString("FrontTire"));
                }
                if (tag.contains("RearTire")) {
                    car.setRearTireType(tag.getString("RearTire"));
                }
            }

            if (variantToSet != null) {
                car.setVariantId(variantToSet);
            }

            car.moveTo(x, y, z, context.getRotation(), 0.0F);
            serverLevel.addFreshEntity(car);
            level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, spawnPos);
            level.playSound(null, spawnPos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 0.75F, 1.0F);

            if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
                itemStack.shrink(1);
            }
            return InteractionResult.CONSUME;
        }

        return InteractionResult.FAIL;
    }
}

