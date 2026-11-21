package com.microdrone.mod.network;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.client.network.ClientPayloadHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModNetworking {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModNetworking::onRegisterPayloadHandlers);
    }

    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MicroDroneMod.MODID).versioned("1.0.0");

        // クライアント -> サーバー (操作入力)
        registrar.playToServer(
                DroneControlPayload.TYPE,
                DroneControlPayload.STREAM_CODEC,
                ServerPayloadHandler::handleDroneControl
        );

        // クライアント -> サーバー (プレーン機フライトモード切替)
        registrar.playToServer(
                PlaneModeTogglePayload.TYPE,
                PlaneModeTogglePayload.STREAM_CODEC,
                ServerPayloadHandler::handlePlaneModeToggle
        );

        // クライアント -> サーバー (RCカー車重チューニング)
        registrar.playToServer(
                RcCarTuningPayload.TYPE,
                RcCarTuningPayload.STREAM_CODEC,
                ServerPayloadHandler::handleRcCarTuning
        );

        // クライアント -> サーバー (RCカーヘッドライト切替)
        registrar.playToServer(
                CarHeadlightTogglePayload.TYPE,
                CarHeadlightTogglePayload.STREAM_CODEC,
                ServerPayloadHandler::handleCarHeadlightToggle
        );

        // クライアント -> サーバー (道路・ヘアピンカーブ設定同期)
        registrar.playToServer(
                RoadConfigPayload.TYPE,
                RoadConfigPayload.STREAM_CODEC,
                ServerPayloadHandler::handleRoadConfig
        );

        // クライアント -> サーバー (デカール作業台 ステッカー設定同期)
        registrar.playToServer(
                UpdateDecalsPayload.TYPE,
                UpdateDecalsPayload.STREAM_CODEC,
                ServerPayloadHandler::handleUpdateDecals
        );

        // クライアント -> サーバー (デカール作業台 キャンセル時アイテム返還)
        registrar.playToServer(
                CancelDecalPayload.TYPE,
                CancelDecalPayload.STREAM_CODEC,
                ServerPayloadHandler::handleCancelDecal
        );

        // サーバー -> クライアント (操縦状態通知)
        registrar.playToClient(
                DronePilotStatePayload.TYPE,
                DronePilotStatePayload.STREAM_CODEC,
                ClientPayloadHandler::handlePilotState
        );

        // サーバー -> クライアント (マルチプレイ車両スナップショット同期: 第1段階)
        registrar.playToClient(
                RcVehicleSnapshotPayload.TYPE,
                RcVehicleSnapshotPayload.STREAM_CODEC,
                ClientPayloadHandler::handleVehicleSnapshot
        );
    }
}
