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
                        output.accept(ModItems.DRONE_SPAWN_ITEM.get());
                        output.accept(ModItems.DRONE_CONTROLLER.get());
                        output.accept(ModItems.DRONE_CHARGER_ITEM.get());
                        output.accept(ModItems.BATTERY_TIER_1.get());
                        output.accept(ModItems.BATTERY_TIER_2.get());
                        output.accept(ModItems.BATTERY_TIER_3.get());
                        output.accept(ModItems.BATTERY_TIER_4.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
