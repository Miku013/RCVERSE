package com.microdrone.mod.inventory;

import com.microdrone.mod.MicroDroneMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MicroDroneMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<RcWorkbenchMenu>> RC_WORKBENCH_MENU =
            MENUS.register("rc_workbench", () -> IMenuTypeExtension.create(RcWorkbenchMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
