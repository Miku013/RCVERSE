package com.microdrone.mod.network;

import com.microdrone.mod.entity.DroneEntity;
import com.microdrone.mod.entity.RcCarEntity;
import com.microdrone.mod.entity.RcBoatEntity;
import com.microdrone.mod.entity.RcPlaneEntity;
import com.microdrone.mod.entity.RcSubmarineEntity;
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
                } else if (entity instanceof RcPlaneEntity plane) {
                    if (plane.isPilotedBy(player)) {
                        if (payload.stop()) {
                            plane.stopPiloting(player);
                        } else {
                            // 航空機操縦入力:
                            // W: ピッチダウン(正), S: ピッチアップ/上昇(負)
                            plane.inputElevator = -payload.forward();
                            // A: 左ロール(負), D: 右ロール(正) (クライアントと符号整合)
                            plane.inputAileron = -payload.strafe();
                            // Space: スロットル増(+1.0), Shift: スロットル減(-1.0)
                            plane.inputThrottleDelta = payload.vertical();
                            // ラダー / ヨー旋回
                            plane.inputRudder = payload.yaw();
                        }
                    }
                } else if (entity instanceof RcBoatEntity boat) {
                    if (boat.isPilotedBy(player)) {
                        if (payload.stop()) {
                            boat.stopPiloting(player);
                        } else {
                            boat.inputForward = payload.forward();
                            boat.inputSteer = payload.strafe();
                        }
                    }
                } else if (entity instanceof RcSubmarineEntity submarine) {
                    if (submarine.isPilotedBy(player)) {
                        if (payload.stop()) {
                            submarine.stopPiloting(player);
                        } else {
                            submarine.inputForward = payload.forward();
                            submarine.inputSteer = payload.strafe();
                            submarine.inputVertical = payload.vertical();
                        }
                    }
                }
            }
        });
    }

    public static void handlePlaneModeToggle(PlaneModeTogglePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Entity entity = player.level().getEntity(payload.planeId());
                if (entity instanceof RcPlaneEntity plane && plane.isPilotedBy(player)) {
                    plane.setFlightMode(payload.mode());
                }
            }
        });
    }

    public static void handleRcCarTuning(RcCarTuningPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Entity entity = player.level().getEntity(payload.carEntityId());
                if (entity instanceof RcCarEntity car) {
                    car.setCustomWeight(payload.weightKg());
                }
            }
        });
    }
}
