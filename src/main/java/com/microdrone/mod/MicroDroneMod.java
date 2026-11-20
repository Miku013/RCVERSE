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
        modEventBus.addListener(ModEntities::registerAttributes);
        ModItems.register(modEventBus);
        com.microdrone.mod.inventory.ModMenus.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModNetworking.register(modEventBus);
        com.microdrone.mod.sound.ModSounds.register(modEventBus);

        // 外部コンフィグの初期化（戦車・車・ドローン・飛行機・ボート・潜水艦）
        com.microdrone.mod.config.tank.TankConfigManager.init();
        com.microdrone.mod.config.car.CarConfigManager.init();
        com.microdrone.mod.config.drone.DroneConfigManager.init();
        com.microdrone.mod.config.plane.PlaneConfigManager.init();
        com.microdrone.mod.config.boat.BoatConfigManager.init();
        com.microdrone.mod.config.submarine.SubmarineConfigManager.init();
        com.microdrone.mod.config.spider.SpiderConfigManager.init();

        if (FMLEnvironment.dist.isClient()) {
            ClientModEvents.register(modEventBus);
        }

        // コマンド登録 (撮影用モードコマンド等)
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::registerCommands);

        LOGGER.info("Micro Drone MOD initialized successfully.");
    }

    private void registerCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        com.microdrone.mod.cinematic.CinematicCommand.register(event.getDispatcher());
    }
}
