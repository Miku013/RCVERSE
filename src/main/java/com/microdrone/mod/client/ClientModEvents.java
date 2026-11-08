package com.microdrone.mod.client;

import com.microdrone.mod.block.entity.ModBlockEntities;
import com.microdrone.mod.client.audio.DroneSoundManager;
import com.microdrone.mod.client.camera.DroneCameraController;
import com.microdrone.mod.client.gui.DroneHudOverlay;
import com.microdrone.mod.client.gui.PlaneFlightHudOverlay;
import com.microdrone.mod.client.gui.TankGunnerHudOverlay;
import com.microdrone.mod.client.input.ClientDroneInputHandler;
import com.microdrone.mod.client.model.DroneModel;
import com.microdrone.mod.client.renderer.DroneChargerBlockEntityRenderer;
import com.microdrone.mod.client.renderer.DroneGeoRenderer;
import com.microdrone.mod.client.renderer.DroneRenderer;
import com.microdrone.mod.client.renderer.RcBoatGeoRenderer;
import com.microdrone.mod.client.renderer.RcCarGeoRenderer;
import com.microdrone.mod.client.renderer.RcPilotDummyRenderer;
import com.microdrone.mod.client.renderer.RcPlaneGeoRenderer;
import com.microdrone.mod.client.renderer.RcSubmarineGeoRenderer;
import com.microdrone.mod.client.renderer.RcTankGeoRenderer;
import com.microdrone.mod.client.renderer.TestPivotBlockEntityRenderer;
import com.microdrone.mod.client.renderer.item.DroneBatteryItemRenderer;
import com.microdrone.mod.client.renderer.item.DroneControllerItemRenderer;
import com.microdrone.mod.entity.ModEntities;
import com.microdrone.mod.item.ModItems;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public class ClientModEvents {

    public static final KeyMapping KEY_HEADLIGHT = new KeyMapping(
            "key.microdrone.headlight",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.microdrone"
    );

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ClientModEvents::registerRenderers);
        modEventBus.addListener(ClientModEvents::registerLayerDefinitions);
        modEventBus.addListener(ClientModEvents::registerClientExtensions);
        modEventBus.addListener(ClientModEvents::registerKeyMappings);

        // クライアント側入力、カメラコントローラー、チャンクイベント、HUDオーバーレイ、サウンドマネージャーの登録
        ClientDroneInputHandler.register();
        DroneCameraController.register();
        com.microdrone.mod.client.chunk.ClientChunkEvents.register();
        DroneHudOverlay.register();
        TankGunnerHudOverlay.register();
        PlaneFlightHudOverlay.register();
        DroneSoundManager.register();
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DRONE.get(), DroneGeoRenderer::new);
        event.registerEntityRenderer(ModEntities.RC_CAR.get(), RcCarGeoRenderer::new);
        event.registerEntityRenderer(ModEntities.RC_TANK.get(), RcTankGeoRenderer::new);
        event.registerEntityRenderer(ModEntities.RC_PLANE.get(), RcPlaneGeoRenderer::new);
        event.registerEntityRenderer(ModEntities.RC_BOAT.get(), RcBoatGeoRenderer::new);
        event.registerEntityRenderer(ModEntities.RC_SUBMARINE.get(), RcSubmarineGeoRenderer::new);
        event.registerEntityRenderer(ModEntities.RC_PILOT_DUMMY.get(), RcPilotDummyRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DRONE_CHARGER_BE.get(), DroneChargerBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TEST_PIVOT_BE.get(), TestPivotBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MINIATURE_PLACEMENT_BE.get(), com.microdrone.mod.client.renderer.MiniaturePlacementBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.RACE_DISPLAY_BE.get(), com.microdrone.mod.client.renderer.RaceDisplayBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.VERTICAL_LOOP_BE.get(), com.microdrone.mod.client.renderer.VerticalLoopBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MODULAR_SLOPE_BE.get(), com.microdrone.mod.client.renderer.ModularSlopeBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ROAD_START_BE.get(), com.microdrone.mod.client.renderer.RoadStartBlockEntityRenderer::new);
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
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KEY_HEADLIGHT);
    }
}
