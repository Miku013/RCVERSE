package com.microdrone.mod.item;

import com.microdrone.mod.client.renderer.item.DroneControllerItemRenderer;
import com.microdrone.mod.entity.DroneEntity;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

public class DroneControllerItem extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public DroneControllerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // 自身が操縦中のドローンがあれば即座に操縦終了（距離制限に関係なく確実に停止）
            DroneEntity activeDrone = DroneEntity.getByPilot(player.getUUID());
            if (activeDrone != null) {
                activeDrone.stopPiloting(serverPlayer);
                return InteractionResultHolder.success(itemStack);
            }

            // 周囲32ブロック以内のドローンを検索
            AABB searchBox = player.getBoundingBox().inflate(32.0);
            List<DroneEntity> drones = level.getEntitiesOfClass(DroneEntity.class, searchBox);

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
