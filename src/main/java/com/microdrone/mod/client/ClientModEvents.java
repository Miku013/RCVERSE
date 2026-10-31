package com.microdrone.mod.client;

import com.microdrone.mod.block.entity.ModBlockEntities;
import com.microdrone.mod.client.audio.DroneSoundManager;
import com.microdrone.mod.client.camera.DroneCameraController;
import com.microdrone.mod.client.gui.DroneHudOverlay;
import com.microdrone.mod.client.input.ClientDroneInputHandler;
import com.microdrone.mod.client.model.DroneModel;
import com.microdrone.mod.client.renderer.DroneChargerBlockEntityRenderer;
import com.microdrone.mod.client.renderer.DroneGeoRenderer;
import com.microdrone.mod.client.renderer.DroneRenderer;
import com.microdrone.mod.client.renderer.TestPivotBlockEntityRenderer;
import com.microdrone.mod.client.renderer.item.DroneBatteryItemRenderer;
import com.microdrone.mod.client.renderer.item.DroneControllerItemRenderer;
import com.microdrone.mod.entity.ModEntities;
import com.microdrone.mod.item.ModItems;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public class ClientModEvents {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ClientModEvents::registerRenderers);
        modEventBus.addListener(ClientModEvents::registerLayerDefinitions);
        modEventBus.addListener(ClientModEvents::registerClientExtensions);

        // クライアント側入力、カメラコントローラー、チャンクイベント、HUDオーバーレイ、サウンドマネージャーの登録
        ClientDroneInputHandler.register();
        DroneCameraController.register();
        com.microdrone.mod.client.chunk.ClientChunkEvents.register();
        DroneHudOverlay.register();
        com.microdrone.mod.client.gui.TankGunnerHudOverlay.register();
        DroneSoundManager.register();
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DRONE.get(), DroneGeoRenderer::new);
        event.registerEntityRenderer(ModEntities.RC_CAR.get(), com.microdrone.mod.client.renderer.RcCarGeoRenderer::new);
        event.registerEntityRenderer(ModEntities.RC_TANK.get(), com.microdrone.mod.client.renderer.RcTankGeoRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DRONE_CHARGER_BE.get(), DroneChargerBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TEST_PIVOT_BE.get(), TestPivotBlockEntityRenderer::new);
    }

    private static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DroneModel.LAYER_LOCATION, DroneModel::createBodyLayer);
    }

    private static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        // コントローラーの GeckoLib 3D アイテムレンダラー
        event.registerItem(new IClientItemExtensions() {
            private DroneControllerItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new DroneControllerItemRenderer();
                }
                return this.renderer;
            }
        }, ModItems.DRONE_CONTROLLER.get());

        // 4段階バッテリーの GeckoLib 3D アイテムレンダラー
        event.registerItem(new IClientItemExtensions() {
            private DroneBatteryItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new DroneBatteryItemRenderer();
                }
                return this.renderer;
            }
        }, ModItems.BATTERY_TIER_1.get(), ModItems.BATTERY_TIER_2.get(), ModItems.BATTERY_TIER_3.get(), ModItems.BATTERY_TIER_4.get());
    }
}
