package com.microdrone.mod.client;

import com.microdrone.mod.client.camera.DroneCameraController;
import com.microdrone.mod.client.input.ClientDroneInputHandler;
import com.microdrone.mod.client.model.DroneModel;
import com.microdrone.mod.client.renderer.DroneRenderer;
import com.microdrone.mod.entity.ModEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public class ClientModEvents {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ClientModEvents::registerRenderers);
        modEventBus.addListener(ClientModEvents::registerLayerDefinitions);

        // クライアント側入力、カメラコントローラー、チャンクイベントリスナーの登録
        ClientDroneInputHandler.register();
        DroneCameraController.register();
        com.microdrone.mod.client.chunk.ClientChunkEvents.register();
    }


    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DRONE.get(), DroneRenderer::new);
    }

    private static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DroneModel.LAYER_LOCATION, DroneModel::createBodyLayer);
    }
}
