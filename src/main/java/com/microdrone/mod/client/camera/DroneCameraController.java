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
        if (entity instanceof DroneEntity drone) {
            originalCameraType = mc.options.getCameraType();

            // カメラをドローンエンティティへ設定
            mc.setCameraEntity(drone);
            isViewing = true;
            activeDroneId = droneId;

            // ドローンの初期向きをプレイヤーの視線に合わせる
            drone.setYRot(mc.player.getYRot());
            drone.setXRot(mc.player.getXRot());
            missingTicks = 0;

            MicroDroneMod.LOGGER.info("[CLIENT] [CAMERA_START] DroneId: {}, DronePos: [{}, {}, {}], DroneChunk: [{}, {}], PlayerPos: [{}, {}, {}]",
                    droneId,
                    String.format("%.2f", drone.getX()), String.format("%.2f", drone.getY()), String.format("%.2f", drone.getZ()),
                    drone.chunkPosition().x, drone.chunkPosition().z,
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

        // ドローンが存在しない、または削除されている
        Entity drone = mc.level.getEntity(activeDroneId);
        if (drone == null || drone.isRemoved()) {
            missingTicks++;
            if (missingTicks % 10 == 0) {
                MicroDroneMod.LOGGER.warn("[CLIENT] [CAMERA_DRONE_MISSING] DroneId: {} missing in client level for {} ticks", activeDroneId, missingTicks);
            }
            // 遠距離移動時のパケット遅延を考慮し、約2秒間（40ticks）見失われた場合のみ切断
            if (missingTicks > 40) {
                MicroDroneMod.LOGGER.error("[CLIENT] [CAMERA_LOST_DRONE] DroneId: {} lost for >40 ticks! Restoring camera and stopping piloting.", activeDroneId);
                stopViewing();
                ClientDroneInputHandler.stopPiloting();
            }
            return;
        }

        // 正常に存在している場合はカウンタリセット
        missingTicks = 0;

        // カメラが外れていた場合の自己修復
        if (mc.getCameraEntity() != drone) {
            MicroDroneMod.LOGGER.warn("[CLIENT] [CAMERA_MISMATCH_REPAIR] Reset camera entity back to drone: {}", activeDroneId);
            mc.setCameraEntity(drone);
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
}

