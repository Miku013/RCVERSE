package com.microdrone.mod.client.camera;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.client.input.ClientDroneInputHandler;
import com.microdrone.mod.entity.DroneEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/**
 * ドローンのカメラ視点切り替えと復元を専門に管理するコントローラー (Phase 4)
 */
public class DroneCameraController {

    private static boolean isViewing = false;
    private static int activeDroneId = -1;
    private static CameraType originalCameraType = CameraType.FIRST_PERSON;

    public static void register() {
        NeoForge.EVENT_BUS.register(new DroneCameraController());
    }

    public static boolean isViewing() {
        return isViewing;
    }

    public static int getActiveDroneId() {
        return activeDroneId;
    }

    /**
     * ドローン視点への切り替え
     */
    public static void startViewing(int droneId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Entity entity = mc.level.getEntity(droneId);
        if (entity != null && !entity.isRemoved()) {
            originalCameraType = mc.options.getCameraType();

            // カメラを対象エンティティ（ドローンまたはRCカー）へ設定
            mc.setCameraEntity(entity);
            isViewing = true;
            activeDroneId = droneId;

            // ドローンの初期向きをプレイヤーの視線に合わせる
            if (entity instanceof DroneEntity drone) {
                drone.setYRot(mc.player.getYRot());
                drone.setXRot(mc.player.getXRot());
            }
            missingTicks = 0;

            MicroDroneMod.LOGGER.info("[CLIENT] [CAMERA_START] VehicleId: {}, Type: {}, Pos: [{}, {}, {}], Chunk: [{}, {}], PlayerPos: [{}, {}, {}]",
                    droneId,
                    entity.getClass().getSimpleName(),
                    String.format("%.2f", entity.getX()), String.format("%.2f", entity.getY()), String.format("%.2f", entity.getZ()),
                    entity.chunkPosition().x, entity.chunkPosition().z,
                    String.format("%.2f", mc.player.getX()), String.format("%.2f", mc.player.getY()), String.format("%.2f", mc.player.getZ()));
        }
    }

    /**
     * プレイヤー視点への安全な復元
     */
    public static void stopViewing() {
        if (!isViewing) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null) {
            mc.setCameraEntity(player);
            mc.options.setCameraType(originalCameraType);
            MicroDroneMod.LOGGER.info("[CLIENT] [CAMERA_STOP] Restored to player: {}, Pos: [{}, {}, {}], RestoredCameraType: {}",
                    player.getName().getString(),
                    String.format("%.2f", player.getX()), String.format("%.2f", player.getY()), String.format("%.2f", player.getZ()),
                    originalCameraType);
        } else {
            MicroDroneMod.LOGGER.info("[CLIENT] [CAMERA_STOP] Player was null when restoring camera.");
        }

        isViewing = false;
        activeDroneId = -1;
        missingTicks = 0;
    }

    private static int missingTicks = 0;

    /**
     * 毎tickの安全性監視（フェイルセーフ）
     * ドローン破壊、消滅、操縦終了、ワールド異常時に即座に視点を復元
     */
    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Pre event) {
        if (!isViewing) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        // プレイヤーまたはワールドが存在しない
        if (player == null || mc.level == null) {
            MicroDroneMod.LOGGER.warn("[CLIENT] [CAMERA_FAILSAFE] Player or level is null, stopping viewing.");
            stopViewing();
            return;
        }

        // 操縦状態が解除されている場合
        if (!ClientDroneInputHandler.isPiloting()) {
            MicroDroneMod.LOGGER.info("[CLIENT] [CAMERA_FAILSAFE] Pilot state ended, stopping viewing.");
            stopViewing();
            return;
        }

        // ビークルが存在しない、または削除されている
        Entity vehicle = mc.level.getEntity(activeDroneId);
        if (vehicle == null || vehicle.isRemoved()) {
            missingTicks++;
            if (missingTicks % 10 == 0) {
                MicroDroneMod.LOGGER.warn("[CLIENT] [CAMERA_VEHICLE_MISSING] VehicleId: {} missing in client level for {} ticks", activeDroneId, missingTicks);
            }
            // 遠距離移動時のパケット遅延を考慮し、約2秒間（40ticks）見失われた場合のみ切断
            if (missingTicks > 40) {
                MicroDroneMod.LOGGER.error("[CLIENT] [CAMERA_LOST_VEHICLE] VehicleId: {} lost for >40 ticks! Restoring camera and stopping piloting.", activeDroneId);
                stopViewing();
                ClientDroneInputHandler.stopPiloting();
            }
            return;
        }

        // 正常に存在している場合はカウンタリセット
        missingTicks = 0;

        // カメラが外れていた場合の自己修復
        if (mc.getCameraEntity() != vehicle) {
            if (ClientDroneInputHandler.isDronePiloting() && ClientDroneInputHandler.getDroneCameraMode() == ClientDroneInputHandler.DroneCameraMode.GROUND) {
                // Ground view bypass: player camera is expected
            } else {
                MicroDroneMod.LOGGER.warn("[CLIENT] [CAMERA_MISMATCH_REPAIR] Reset camera entity back to vehicle: {}", activeDroneId);
                mc.setCameraEntity(vehicle);
            }
        }
    }

    /**
     * ログアウト・切断時の視点復元
     */
    @SubscribeEvent
    public void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MicroDroneMod.LOGGER.info("[CLIENT] [LOGOUT] Client logging out, activeDroneId: {}", activeDroneId);
        stopViewing();
    }

    /**
     * エンティティ離脱時の視点復元
     */
    @SubscribeEvent
    public void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (isViewing && event.getEntity().getId() == activeDroneId) {
            MicroDroneMod.LOGGER.warn("[CLIENT] [ENTITY_LEAVE_LEVEL] Drone entity left client level! EntityId: {}, ActiveDroneId: {}",
                    event.getEntity().getId(), activeDroneId);
            stopViewing();
            ClientDroneInputHandler.stopPiloting();
        }
    }

    /**
     * ドローン視点・操縦中の一人称手持ちアイテム（ツール・武器・コントローラー）の非表示化
     * これによりツールベルト選択変更時のカクつき・チラつき表示を完全に防止
     */
    @SubscribeEvent
    public void onRenderHand(RenderHandEvent event) {
        if (isViewing || ClientDroneInputHandler.isPiloting()) {
            // RCカーの地上プロポ視点(GROUND_RC)のときは、手元にプロポ（送信機）を表示！
            if ((ClientDroneInputHandler.isGroundVehiclePiloting() && ClientDroneInputHandler.getCarCameraMode() == ClientDroneInputHandler.RcCameraMode.GROUND_RC)
                    || (ClientDroneInputHandler.isDronePiloting() && ClientDroneInputHandler.getDroneCameraMode() == ClientDroneInputHandler.DroneCameraMode.GROUND)) {
                return;
            }
            event.setCanceled(true);
        }
    }

    /**
     * ドローン視点・操縦中の自プレイヤーモデルの非表示化
     */
    @SubscribeEvent
    public void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (isViewing || ClientDroneInputHandler.isPiloting()) {
            Minecraft mc = Minecraft.getInstance();
            if (event.getEntity() == mc.player) {
                // RCカーの地上プロポ視点では自然な一人称視線を保つ
                if ((ClientDroneInputHandler.isGroundVehiclePiloting() && ClientDroneInputHandler.getCarCameraMode() == ClientDroneInputHandler.RcCameraMode.GROUND_RC)
                        || (ClientDroneInputHandler.isDronePiloting() && ClientDroneInputHandler.getDroneCameraMode() == ClientDroneInputHandler.DroneCameraMode.GROUND)) {
                    return;
                }
                event.setCanceled(true);
            }
        }
    }
}

