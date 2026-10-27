package com.microdrone.mod.item;

import com.microdrone.mod.entity.DroneEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class DroneControllerItem extends Item {

    public DroneControllerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // 周囲32ブロック以内のドローンを検索
            AABB searchBox = player.getBoundingBox().inflate(32.0);
            List<DroneEntity> drones = level.getEntitiesOfClass(DroneEntity.class, searchBox);

            // 自身が操縦中のドローンがあれば操縦終了
            for (DroneEntity drone : drones) {
                if (drone.isPilotedBy(player)) {
                    drone.stopPiloting(serverPlayer);
                    return InteractionResultHolder.success(itemStack);
                }
            }

            // 操縦していない場合、最も近い空いているドローンの操縦を開始
            DroneEntity nearestAvailable = null;
            double nearestDistSq = Double.MAX_VALUE;

            for (DroneEntity drone : drones) {
                if (!drone.isPiloted()) {
                    double distSq = player.distanceToSqr(drone);
                    if (distSq < nearestDistSq) {
                        nearestDistSq = distSq;
                        nearestAvailable = drone;
                    }
                }
            }

            if (nearestAvailable != null) {
                nearestAvailable.startPiloting(serverPlayer);
                return InteractionResultHolder.success(itemStack);
            } else {
                player.sendSystemMessage(Component.translatable("message.microdrone.no_drone_nearby"));
                return InteractionResultHolder.fail(itemStack);
            }
        }

        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide);
    }
}
