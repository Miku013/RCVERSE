package com.microdrone.mod.client.network;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.client.ReplayHelper;
import com.microdrone.mod.client.input.ClientDroneInputHandler;
import com.microdrone.mod.network.DronePilotStatePayload;
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
}
