package com.microdrone.mod.cinematic;

import com.microdrone.mod.entity.*;
import com.microdrone.mod.network.DroneControlPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * プレイ動画・PV撮影用マルチビークル同時操縦（ブロードキャスト）マネージャー
 * 操縦カメラはメインのRCカーにロックしたまま、周囲の全RCビークルに同一の操縦入力を一括反映します。
 */
public class CinematicRecordingManager {

    private static boolean enabled = true; // 撮影用MODではデフォルトで有効
    private static double broadcastRadius = 48.0; // 同調有効半径（ブロック）

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static double getBroadcastRadius() {
        return broadcastRadius;
    }

    public static void setBroadcastRadius(double radius) {
        broadcastRadius = Math.max(5.0, Math.min(256.0, radius));
    }

    /**
     * メイン車両の操作入力を、周囲のすべてのRCラジコンへ同時配信
     */
    public static void broadcastControlInput(ServerPlayer player, Entity primaryVehicle, DroneControlPayload payload) {
        if (!enabled || primaryVehicle == null || !(primaryVehicle.level() instanceof ServerLevel level)) {
            return;
        }

        AABB searchBox = primaryVehicle.getBoundingBox().inflate(broadcastRadius);
        List<Entity> nearbyEntities = level.getEntities(primaryVehicle, searchBox, e -> e instanceof RcControllable);

        for (Entity e : nearbyEntities) {
            if (e == primaryVehicle) continue;

            if (payload.stop()) {
                stopVehicle(e);
                continue;
            }

            if (e instanceof RcCarEntity car) {
                // RCカー同期（ツインドリフト・並走）
                car.inputForward = payload.forward();
                car.inputSteer = payload.strafe();
                car.inputHandbrake = (payload.vertical() > 0.5f);
                if (primaryVehicle instanceof RcCarEntity primaryCar) {
                    car.setDrivingMode(primaryCar.isRealMode());
                }
            } else if (e instanceof RcTankEntity tank) {
                // 戦車同期（キャタピラ前進・砲塔旋回）
                tank.inputForward = payload.forward();
                tank.inputSteer = payload.strafe();
                tank.inputFire = (payload.vertical() > 0.5f);
                tank.setCinematicActive(true);
            } else if (e instanceof RcPlaneEntity plane) {
                // 飛行機同期（プロペラ全開・滑走前進）
                plane.inputThrottleDelta = (payload.forward() > 0.05f) ? 1.0f : ((payload.forward() < -0.05f) ? -1.0f : 0.0f);
                plane.inputElevator = -payload.forward();
                plane.inputAileron = -payload.strafe();
                plane.inputRudder = payload.yaw();
                plane.setCinematicActive(true);
            } else if (e instanceof RcBoatEntity boat) {
                // 高速ボート同期（水上スクリュー推進）
                boat.inputForward = payload.forward();
                boat.inputSteer = payload.strafe();
                boat.setCinematicActive(true);
            } else if (e instanceof RcSubmarineEntity sub) {
                // 潜水艦同期（スクリュー推進）
                sub.inputForward = payload.forward();
                sub.inputSteer = payload.strafe();
                sub.inputVertical = payload.vertical();
                sub.setCinematicActive(true);
            } else if (e instanceof DroneEntity drone) {
                // ドローン同期（プロペラ回転・前進）
                drone.applyControlInput(payload.forward(), payload.strafe(), payload.vertical(), payload.yaw(), payload.pitch());
                drone.setCinematicActive(true);
            }
        }
    }

    private static void stopVehicle(Entity e) {
        if (e instanceof RcCarEntity car) {
            car.inputForward = 0.0f;
            car.inputSteer = 0.0f;
            car.inputHandbrake = false;
        } else if (e instanceof RcTankEntity tank) {
            tank.inputForward = 0.0f;
            tank.inputSteer = 0.0f;
            tank.inputFire = false;
            tank.setCinematicActive(false);
        } else if (e instanceof RcPlaneEntity plane) {
            plane.inputThrottleDelta = 0.0f;
            plane.inputElevator = 0.0f;
            plane.inputAileron = 0.0f;
            plane.setCinematicActive(false);
        } else if (e instanceof RcBoatEntity boat) {
            boat.inputForward = 0.0f;
            boat.inputSteer = 0.0f;
            boat.setCinematicActive(false);
        } else if (e instanceof RcSubmarineEntity sub) {
            sub.inputForward = 0.0f;
            sub.inputSteer = 0.0f;
            sub.inputVertical = 0.0f;
            sub.setCinematicActive(false);
        } else if (e instanceof DroneEntity drone) {
            drone.applyControlInput(0, 0, 0, 0, 0);
            drone.setCinematicActive(false);
        }
    }
}
