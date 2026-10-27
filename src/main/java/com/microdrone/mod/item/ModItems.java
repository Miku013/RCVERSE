package com.microdrone.mod.item;

import com.microdrone.mod.MicroDroneMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MicroDroneMod.MODID);

    public static final DeferredHolder<Item, DroneSpawnItem> DRONE_SPAWN_ITEM =
            ITEMS.register("drone_item", () -> new DroneSpawnItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<Item, DroneControllerItem> DRONE_CONTROLLER =
            ITEMS.register("drone_controller", () -> new DroneControllerItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
