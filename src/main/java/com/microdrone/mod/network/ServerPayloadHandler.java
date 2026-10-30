package com.microdrone.mod.network;

import com.microdrone.mod.entity.DroneEntity;
import com.microdrone.mod.entity.RcCarEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ServerPayloadHandler {

    public static void handleDroneControl(DroneControlPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Entity entity = player.level().getEntity(payload.droneId());
                if (entity instanceof DroneEntity drone) {
                    if (drone.isPilotedBy(player)) {
                        if (payload.stop()) {
                            drone.stopPiloting(player);
                        } else {
                            drone.applyControlInput(
                                    payload.forward(),
                                    payload.strafe(),
                                    payload.vertical(),
                                    payload.yaw(),
                                    payload.pitch()
                            );
                        }
                    }
                } else if (entity instanceof RcCarEntity car) {
                    if (car.isPilotedBy(player)) {
                        if (payload.stop()) {
                            car.stopPiloting(player);
                        } else {
                            car.inputForward = payload.forward();
                            car.inputSteer = payload.strafe();
                        }
                    }
                }
            }
        });
    }
}
