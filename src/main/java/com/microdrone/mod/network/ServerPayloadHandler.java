package com.microdrone.mod.network;

import com.microdrone.mod.entity.DroneEntity;
import com.microdrone.mod.entity.RcCarEntity;
import com.microdrone.mod.entity.RcTankEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
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
                            car.inputHandbrake = (payload.vertical() > 0.5f); // スペースキーでハンドブレーキ
                        }
                    }
                } else if (entity instanceof RcTankEntity tank) {
                    if (tank.isPilotedBy(player)) {
                        if (payload.stop()) {
                            tank.stopPiloting(player);
                        } else {
                            tank.inputForward = payload.forward();
                            tank.inputSteer = payload.strafe();
                            tank.inputFire = (payload.vertical() > 0.5f); // スペースキーで主砲発射！

                            // プレイヤーの視線から砲塔の相対旋回角・砲身仰俯角を算出
                            float relativeYaw = Mth.wrapDegrees(payload.yaw() - tank.getYRot());
                            tank.targetTurretYaw = relativeYaw;
                            tank.targetCannonPitch = payload.pitch();
                        }
                    }
                }
            }
        });
    }
}
