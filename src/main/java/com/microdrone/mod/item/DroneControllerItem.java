package com.microdrone.mod.item;

import com.microdrone.mod.client.renderer.item.DroneControllerItemRenderer;
import com.microdrone.mod.entity.DroneEntity;
import com.microdrone.mod.entity.RcControllable;
import com.microdrone.mod.entity.RcVehicleManager;
import net.minecraft.world.entity.Entity;
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
            // 1. 自身が操縦中のRC機体（ドローンや車など）があれば即座に操縦終了
            RcControllable activeVehicle = RcVehicleManager.getByPilot(player.getUUID());
            if (activeVehicle != null) {
                activeVehicle.stopPiloting(serverPlayer);
                return InteractionResultHolder.success(itemStack);
            }

            // 2. 周囲32ブロック以内のRC機体を検索
            AABB searchBox = player.getBoundingBox().inflate(32.0);
            List<Entity> candidates = level.getEntities(player, searchBox, e -> e instanceof RcControllable);

            // 操縦していない場合、最も近い空いているRC機体の操縦を開始
            RcControllable nearestAvailable = null;
            double nearestDistSq = Double.MAX_VALUE;

            for (Entity entity : candidates) {
                if (entity instanceof RcControllable vehicle && !vehicle.isPiloted()) {
                    double distSq = player.distanceToSqr(entity);
                    if (distSq < nearestDistSq) {
                        nearestDistSq = distSq;
                        nearestAvailable = vehicle;
                    }
                }
            }

            if (nearestAvailable != null) {
                nearestAvailable.startPiloting(serverPlayer);
                return InteractionResultHolder.success(itemStack);
            } else {
                player.sendSystemMessage(Component.translatable("message.microdrone.no_vehicle_nearby"));
                return InteractionResultHolder.fail(itemStack);
            }
        }

        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide);
    }
}
