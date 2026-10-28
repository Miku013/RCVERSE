package com.microdrone.mod;

import com.microdrone.mod.block.ModBlocks;
import com.microdrone.mod.block.entity.ModBlockEntities;
import com.microdrone.mod.client.ClientModEvents;
import com.microdrone.mod.entity.ModEntities;
import com.microdrone.mod.item.ModCreativeTabs;
import com.microdrone.mod.item.ModItems;
import com.microdrone.mod.network.ModNetworking;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(MicroDroneMod.MODID)
public class MicroDroneMod {
    public static final String MODID = "microdrone";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MicroDroneMod(IEventBus modEventBus) {
        LOGGER.info("Initializing Micro Drone MOD...");

        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModNetworking.register(modEventBus);

        if (FMLEnvironment.dist.isClient()) {
            ClientModEvents.register(modEventBus);
        }

        LOGGER.info("Micro Drone MOD initialized successfully.");
    }
}
