package com.microdrone.mod.client.network;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.client.input.ClientDroneInputHandler;
import com.microdrone.mod.network.DronePilotStatePayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClientPayloadHandler {

    public static void handlePilotState(DronePilotStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            MicroDroneMod.LOGGER.info("[CLIENT] [PACKET_RECEIVE] Payload: DronePilotStatePayload, DroneId: {}, IsPiloting: {}",
                    payload.droneId(), payload.isPiloting());
            ClientDroneInputHandler.setPiloting(payload.droneId(), payload.isPiloting());
        });
    }
}

