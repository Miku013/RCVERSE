package com.microdrone.mod.item;

import com.microdrone.mod.MicroDroneMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MicroDroneMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DRONE_TAB =
            CREATIVE_MODE_TABS.register("microdrone_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.microdrone"))
                    .icon(() -> new ItemStack(ModItems.DRONE_CONTROLLER.get()))
                    .displayItems((parameters, output) -> {
                        // 完成品
                        output.accept(ModItems.DRONE_SPAWN_ITEM.get());
                        output.accept(ModItems.RC_CAR_ITEM.get());
                        output.accept(ModItems.RC_TANK_ITEM.get());
                        output.accept(ModItems.RC_PLANE_ITEM.get());
                        output.accept(ModItems.RC_BOAT_ITEM.get());
                        output.accept(ModItems.RC_SUBMARINE_ITEM.get());
                        output.accept(ModItems.DRONE_CONTROLLER.get());
                        output.accept(ModItems.DRONE_CHARGER_ITEM.get());
                        output.accept(ModItems.TEST_PIVOT_ITEM.get());
                        output.accept(ModItems.TRAFFIC_CONE.get());
                        output.accept(ModItems.RACING_CURB.get());
                        output.accept(ModItems.CURB_STONE.get());
                        output.accept(ModItems.GUARDRAIL.get());
                        output.accept(ModItems.TIRE_BARRIER.get());
                        output.accept(ModItems.GOAL_GATE.get());
                        output.accept(ModItems.MINIATURE_PLACEMENT_ITEM.get());

                        // バッテリー
                        output.accept(ModItems.BATTERY_TIER_1.get());
                        output.accept(ModItems.BATTERY_TIER_2.get());
                        output.accept(ModItems.BATTERY_TIER_3.get());
                        output.accept(ModItems.BATTERY_TIER_4.get());

                        // モーター
                        output.accept(ModItems.MOTOR_TIER_1.get());
                        output.accept(ModItems.MOTOR_TIER_2.get());
                        output.accept(ModItems.MOTOR_TIER_3.get());
                        output.accept(ModItems.MOTOR_TIER_4.get());

                        // ドローン本体素材
                        output.accept(ModItems.DRONE_FRAME.get());
                        output.accept(ModItems.PROPELLER.get());
                        output.accept(ModItems.ESC_BOARD.get());
                        output.accept(ModItems.DRONE_SCREWS.get());
                        output.accept(ModItems.CONNECTION_CABLE.get());

                        // プロポ素材
                        output.accept(ModItems.RADIO_ANTENNA.get());
                        output.accept(ModItems.CONTROLLER_CASE.get());
                        output.accept(ModItems.GIMBAL_STICK.get());
                        output.accept(ModItems.CONTROLLER_BOARD.get());
                        output.accept(ModItems.TINY_SPRING.get());
                        output.accept(ModItems.LCD_SCREEN.get());

                        // モーター素材
                        output.accept(ModItems.MOTOR_CAN_TIER_1.get());
                        output.accept(ModItems.MOTOR_CAN_TIER_2.get());
                        output.accept(ModItems.MOTOR_CAN_TIER_3.get());
                        output.accept(ModItems.MOTOR_CAN_TIER_4.get());
                        output.accept(ModItems.MAGNET_WIRE.get());
                        output.accept(ModItems.MOTOR_SHAFT.get());
                        output.accept(ModItems.NEODYMIUM_MAGNET.get());
                        output.accept(ModItems.MOTOR_STATOR_TERMINAL.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
