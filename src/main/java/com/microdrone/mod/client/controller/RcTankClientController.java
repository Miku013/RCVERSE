package com.microdrone.mod.client.controller;

import com.microdrone.mod.entity.RcTankEntity;
import com.microdrone.mod.network.DroneControlPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Method;

/**
 * マイクロRC戦車専用クライアントコントローラー
 */
public class RcTankClientController implements RcVehicleClientController {

    private RcViewMode viewMode = RcViewMode.GROUND;

    private static Method cameraMoveMethod = null;
    private static boolean cameraMoveResolved = false;

    @Override
    public void onStart(Entity vehicle, LocalPlayer player, double startX, double startY, double startZ) {
        viewMode = RcViewMode.GROUND;
        if (vehicle instanceof RcTankEntity tank) {
            tank.setLocallyControlled(true);
        }
        if (player != null) {
            player.setInvisible(false);
            Minecraft.getInstance().setCameraEntity(player);
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
            player.displayClientMessage(Component.translatable("message.microdrone.tank_cam_ground"), true);
        }
    }

    @Override
    public void onStop(Entity vehicle, LocalPlayer player) {
        if (vehicle instanceof RcTankEntity tank) {
            tank.setLocallyControlled(false);
        }
        if (player != null) {
            player.setInvisible(false);
            Minecraft.getInstance().setCameraEntity(player);
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    @Override
    public void handleTickInput(LocalPlayer player, Entity vehicle, InputSnapshot input, double startX, double startY, double startZ) {
        if (!(vehicle instanceof RcTankEntity tank)) return;

        player.setPos(startX, startY, startZ);
        tank.inputForward = input.forward();
        tank.inputSteer = input.strafe();
        tank.inputFire = (input.vertical() > 0.5f);

        float mouseYaw = player.getYRot();
        float mousePitch = player.getXRot();
        float relativeYaw = Mth.wrapDegrees(mouseYaw - tank.getYRot());
        float cannonPitch = Mth.clamp(mousePitch, -15.0f, 35.0f);

        tank.targetTurretYaw = relativeYaw;
        tank.targetCannonPitch = cannonPitch;

        PacketDistributor.sendToServer(new DroneControlPayload(
                tank.getId(),
                input.forward(),
                input.strafe(),
                input.vertical(),
                mouseYaw,
                cannonPitch,
                false
        ));
    }

    @Override
    public void computeCameraAngles(ViewportEvent.ComputeCameraAngles event, Entity vehicle, double startX, double startY, double startZ) {
        if (!(vehicle instanceof RcTankEntity tank) || tank.isRemoved()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float partialTick = (float) event.getPartialTick();
        float mouseYaw = mc.player.getYRot();
        float mousePitch = mc.player.getXRot();
        float relativeYaw = Mth.wrapDegrees(mouseYaw - tank.getYRot());
        float cannonPitch = Mth.clamp(mousePitch, -15.0f, 35.0f);

        tank.targetTurretYaw = relativeYaw;
        tank.targetCannonPitch = cannonPitch;
        tank.clientTurretYaw = relativeYaw;
        tank.clientCannonPitch = cannonPitch;

        if (viewMode == RcViewMode.GROUND) {
            double tankX = Mth.lerp(partialTick, tank.xOld, tank.getX());
            double tankY = Mth.lerp(partialTick, tank.yOld, tank.getY());
            double tankZ = Mth.lerp(partialTick, tank.zOld, tank.getZ());
            double eyeX = mc.player.getX();
            double eyeY = mc.player.getEyeY();
            double eyeZ = mc.player.getZ();
            double dx = tankX - eyeX;
            double dy = tankY - eyeY;
            double dz = tankZ - eyeZ;
            double distXZ = Math.sqrt(dx * dx + dz * dz);
            float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
            float targetPitch = (float) (-(Mth.atan2(dy, distXZ) * (180.0 / Math.PI)));

            event.setYaw(targetYaw);
            event.setPitch(targetPitch);
        } else if (viewMode == RcViewMode.CHASE) {
            float pitch = Mth.clamp(18.0f + mousePitch * 0.45f, 5.0f, 65.0f);
            event.setYaw(mouseYaw);
            event.setPitch(pitch);
            moveCameraSafe(event.getCamera(), 1.8, 0.2, 0.0);
        } else {
            // FIRST_PERSON (GUNNER_AIM)
            event.setYaw(mouseYaw);
            event.setPitch(mousePitch);
        }
    }

    @Override
    public void toggleViewMode(Entity vehicle, LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (!(vehicle instanceof RcTankEntity tank)) return;

        if (viewMode == RcViewMode.GROUND) {
            // 地上プロポ視点 -> 車載チェイス視点（三人称後方追従）
            viewMode = RcViewMode.CHASE;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.microdrone.tank_cam_chase"), true);
                player.setInvisible(true);
            }
            mc.setCameraEntity(tank);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (viewMode == RcViewMode.CHASE) {
            // 車載チェイス視点 -> ガンナー照準視点（砲塔同軸FPS・専用HUD）
            viewMode = RcViewMode.FIRST_PERSON;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.microdrone.tank_cam_gunner"), true);
                player.setInvisible(true);
            }
            mc.setCameraEntity(tank);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        } else {
            // ガンナー照準視点 -> 地上プロポ視点
            viewMode = RcViewMode.GROUND;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.microdrone.tank_cam_ground"), true);
                player.setInvisible(false);
            }
            mc.setCameraEntity(player);
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

    private static void moveCameraSafe(Camera camera, double distanceOffset, double yOffset, double xOffset) {
        if (!cameraMoveResolved) {
            try {
                for (Method m : Camera.class.getDeclaredMethods()) {
                    if (m.getParameterCount() == 3 &&
                            m.getParameterTypes()[0] == double.class &&
                            m.getParameterTypes()[1] == double.class &&
                            m.getParameterTypes()[2] == double.class &&
                            (m.getName().equals("move") || m.getName().contains("move") || m.getName().equals("m_90584_"))) {
                        cameraMoveMethod = m;
                        cameraMoveMethod.setAccessible(true);
                        break;
                    }
                }
            } catch (Exception ignored) {}
            cameraMoveResolved = true;
        }

        if (cameraMoveMethod != null) {
            try {
                cameraMoveMethod.invoke(camera, distanceOffset, yOffset, xOffset);
            } catch (Exception ignored) {}
        }
    }
}
