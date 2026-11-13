package com.microdrone.mod.item;

import com.microdrone.mod.entity.DroneEntity;
import com.microdrone.mod.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

public class DroneSpawnItem extends Item {
    public DroneSpawnItem(Properties properties) {
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

        DroneEntity drone = ModEntities.DRONE.get().create(serverLevel);
        if (drone != null) {
            // アイテムのカスタムデータや金床カスタム名からバリアントIDを適用
            var customData = itemStack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (customData != null) {
                net.minecraft.nbt.CompoundTag tag = customData.copyTag();
                if (tag.contains("Variant")) {
                    drone.setVariantId(tag.getString("Variant"));
                }
                if (tag.contains("Decals", 10)) {
                    drone.setDecals(com.microdrone.mod.decal.CarDecalData.deserializeDecals(tag.getCompound("Decals")));
                }
            } else if (itemStack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
                String customName = itemStack.getHoverName().getString().trim().toLowerCase();
                if (com.microdrone.mod.config.drone.DroneConfigManager.getAllConfigs().containsKey(customName)) {
                    drone.setVariantId(customName);
                }
            }

            drone.moveTo(x, y, z, context.getRotation(), 0.0F);
            drone.setYHeadRot(context.getRotation());
            serverLevel.addFreshEntity(drone);
            level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, spawnPos);
            level.playSound(null, spawnPos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 0.75F, 1.2F);

            if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
                itemStack.shrink(1);
            }
            return InteractionResult.CONSUME;
        }

        return InteractionResult.FAIL;
    }
}
