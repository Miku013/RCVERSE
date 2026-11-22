package com.microdrone.mod.client.network;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.client.ReplayHelper;
import com.microdrone.mod.client.input.ClientDroneInputHandler;
import com.microdrone.mod.entity.RcCarEntity;
import com.microdrone.mod.network.DronePilotStatePayload;
import com.microdrone.mod.network.RcVehicleSnapshotPayload;
import com.microdrone.mod.sync.ISnapshotInterpolated;
import com.microdrone.mod.sync.VehicleSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClientPayloadHandler {

    public static void handlePilotState(DronePilotStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (ReplayHelper.isReplayActive()) {
                MicroDroneMod.LOGGER.info("[CLIENT] [REPLAY] Ignored DronePilotStatePayload during replay playback. DroneId: {}, IsPiloting: {}",
                        payload.droneId(), payload.isPiloting());
                return;
            }
            MicroDroneMod.LOGGER.info("[CLIENT] [PACKET_RECEIVE] Payload: DronePilotStatePayload, DroneId: {}, IsPiloting: {}, StartPos: [{}, {}, {}]",
                    payload.droneId(), payload.isPiloting(), payload.startX(), payload.startY(), payload.startZ());
            ClientDroneInputHandler.setPiloting(payload.droneId(), payload.isPiloting(), payload.startX(), payload.startY(), payload.startZ());
        });
    }

    /**
     * マルチプレイ車両スナップショット受信ハンドラ（第1段階：Snapshot Interpolation）
     * 他プレイヤーが操作する車両の物理状態を受信し、描画補間バッファへ蓄積
     */
    public static void handleVehicleSnapshot(RcVehicleSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;

            Entity entity = mc.level.getEntity(payload.entityId());
            if (entity instanceof ISnapshotInterpolated vehicle) {
                // 自プレイヤー操縦中の機体は手元の予測物理（Prediction）を最優先するためスキップ
                if (vehicle.isLocallyControlled()) {
                    return;
                }

                long now = System.currentTimeMillis();
                vehicle.getSnapshotBuffer().push(new VehicleSnapshot(
                        now,
                        payload.serverTick(),
                        payload.x(),
                        payload.y(),
                        payload.z(),
                        payload.yaw(),
                        payload.pitch(),
                        payload.roll(),
                        payload.vx(),
                        payload.vy(),
                        payload.vz()
                ));
            }
        });
    }
}
