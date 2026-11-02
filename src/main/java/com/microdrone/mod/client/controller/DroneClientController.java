package com.microdrone.mod.client.controller;

import com.microdrone.mod.client.audio.DroneSoundManager;
import com.microdrone.mod.client.camera.DroneCameraController;
import com.microdrone.mod.entity.DroneEntity;
import com.microdrone.mod.network.DroneControlPayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * マイクロFPVドローン専用クライアントコントローラー
 */
public class DroneClientController implements RcVehicleClientController {

    private RcViewMode viewMode = RcViewMode.FIRST_PERSON;

    // 地上プロポ視点（GROUND）用のLook-At自動追尾＆マウス旋回制御
    private float droneGroundAimYaw = 0.0f;
    private float lastDroneGroundLookYaw = 0.0f;
    private float lastDroneGroundLookPitch = 0.0f;
    private boolean droneGroundCameraInit = false;

    @Override
    public void onStart(Entity vehicle, LocalPlayer player, double startX, double startY, double startZ) {
        viewMode = RcViewMode.FIRST_PERSON;
        if (vehicle instanceof DroneEntity drone) {
            droneGroundAimYaw = drone.getYRot();
            droneGroundCameraInit = false;
            drone.setLocallyControlled(true);
            DroneSoundManager.startPilotingSound(drone);
            DroneCameraController.startViewing(drone.getId());
        }
        if (player != null) {
            player.setInvisible(true);
        }
    }

    @Override
    public void onStop(Entity vehicle, LocalPlayer player) {
        DroneSoundManager.stopPilotingSound();
        if (vehicle instanceof DroneEntity drone) {
            drone.setLocallyControlled(false);
        }
        droneGroundCameraInit = false;
        DroneCameraController.stopViewing();
        if (player != null) {
            player.setInvisible(false);
            Minecraft.getInstance().setCameraEntity(player);
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    @Override
    public void handleTickInput(LocalPlayer player, Entity vehicle, InputSnapshot input, double startX, double startY, double startZ) {
        if (!(vehicle instanceof DroneEntity drone)) return;

        float currentYaw;
        float currentPitch;
        if (viewMode == RcViewMode.GROUND) {
            player.setPos(startX, startY, startZ);
            player.setDeltaMovement(0, 0, 0);
            currentYaw = droneGroundAimYaw;
            currentPitch = 0.0f;
        } else {
            player.setPos(drone.getX(), drone.getY(), drone.getZ());
            currentYaw = player.getYRot();
            currentPitch = player.getXRot();
        }

        drone.applyControlInput(input.forward(), input.strafe(), input.vertical(), currentYaw, currentPitch);

        PacketDistributor.sendToServer(new DroneControlPayload(
                drone.getId(),
                input.forward(),
                input.strafe(),
                input.vertical(),
                currentYaw,
                currentPitch,
                false
        ));
    }

    @Override
    public void computeCameraAngles(ViewportEvent.ComputeCameraAngles event, Entity vehicle, double startX, double startY, double startZ) {
        if (!(vehicle instanceof DroneEntity drone)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (viewMode == RcViewMode.GROUND) {
            float partialTick = (float) event.getPartialTick();
            double droneX = Mth.lerp(partialTick, drone.xOld, drone.getX());
            double droneY = Mth.lerp(partialTick, drone.yOld, drone.getY());
            double droneZ = Mth.lerp(partialTick, drone.zOld, drone.getZ());
            double eyeX = startX;
            double eyeY = startY + mc.player.getEyeHeight();
            double eyeZ = startZ;
            double dx = droneX - eyeX;
            double dy = droneY - eyeY;
            double dz = droneZ - eyeZ;
            double distXZ = Math.sqrt(dx * dx + dz * dz);

            // ドローンを常に画面中央に捉え続ける自動追尾カメラ角度 (Look-At)
            float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
            float targetPitch = (float) (-(Mth.atan2(dy, distXZ) * (180.0 / Math.PI)));

            // 【マウス移動デルタのリアルタイム取得】上書き直前のマウス移動量 (deltaYaw) を即座にドローン旋回角へ加算！
            if (!droneGroundCameraInit) {
                lastDroneGroundLookYaw = targetYaw;
                lastDroneGroundLookPitch = targetPitch;
                droneGroundAimYaw = drone.getYRot();
                droneGroundCameraInit = true;
            } else {
                float deltaYaw = Mth.wrapDegrees(mc.player.getYRot() - lastDroneGroundLookYaw);
                if (Math.abs(deltaYaw) > 0.001f) {
                    droneGroundAimYaw = Mth.wrapDegrees(droneGroundAimYaw + deltaYaw);
                }
            }

            // カメラはドローンを自動追尾 (Look-At)
            event.setYaw(targetYaw);
            event.setPitch(targetPitch);
            event.setRoll(0.0f);

            // プレイヤーの視線もLook-At角度にリセットして視線追尾を維持
            mc.player.setYRot(targetYaw);
            mc.player.setXRot(targetPitch);
            lastDroneGroundLookYaw = targetYaw;
            lastDroneGroundLookPitch = targetPitch;

            // ドローン自身の機首はマウス旋回角度へリアルタイム同期！
            drone.setYRot(droneGroundAimYaw);
            drone.setYHeadRot(droneGroundAimYaw);
        } else if (viewMode == RcViewMode.CHASE) {
            float yaw = mc.player.getYRot();
            drone.setYRot(yaw);
            drone.setYHeadRot(yaw);

            event.setYaw(yaw);
            event.setPitch(15.0f);
            event.setRoll(0.0f);
        } else {
            // FIRST_PERSON (FPV)
            float yaw = mc.player.getYRot();
            float pitch = mc.player.getXRot();

            event.setYaw(yaw);
            event.setPitch(pitch);
            event.setRoll(0.0f);

            drone.setYRot(yaw);
            drone.setYHeadRot(yaw);
            drone.setXRot(pitch);
        }
    }

    @Override
    public void toggleViewMode(Entity vehicle, LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (!(vehicle instanceof DroneEntity drone)) return;

        if (viewMode == RcViewMode.FIRST_PERSON) {
            viewMode = RcViewMode.CHASE;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.microdrone.cam_chase"), true);
                player.setInvisible(true);
            }
            mc.setCameraEntity(drone);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (viewMode == RcViewMode.CHASE) {
            viewMode = RcViewMode.GROUND;
            droneGroundAimYaw = drone.getYRot();
            droneGroundCameraInit = false;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.microdrone.cam_ground"), true);
                player.setInvisible(false);
            }
            mc.setCameraEntity(player);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        } else {
            viewMode = RcViewMode.FIRST_PERSON;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.microdrone.cam_gunner"), true);
                player.setInvisible(true);
                player.setYRot(droneGroundAimYaw);
                player.setXRot(0.0f);
            }
            mc.setCameraEntity(drone);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    @Override
    public RcViewMode getViewMode() {
        return viewMode;
    }

    @Override
    public CameraType getExpectedCameraType() {
        return (viewMode == RcViewMode.CHASE) ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON;
    }
}
