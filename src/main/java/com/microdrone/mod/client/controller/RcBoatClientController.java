package com.microdrone.mod.client.controller;

import com.microdrone.mod.entity.RcBoatEntity;
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
 * マイクロRCボート専用クライアントコントローラー
 */
public class RcBoatClientController implements RcVehicleClientController {

    private RcViewMode viewMode = RcViewMode.GROUND;

    private static Method cameraMoveMethod = null;
    private static boolean cameraMoveResolved = false;

    @Override
    public void onStart(Entity vehicle, LocalPlayer player, double startX, double startY, double startZ) {
        viewMode = RcViewMode.GROUND;
        if (vehicle instanceof RcBoatEntity boat) {
            boat.setLocallyControlled(true);
        }
        if (player != null) {
            player.setInvisible(false);
            Minecraft.getInstance().setCameraEntity(player);
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    @Override
    public void onStop(Entity vehicle, LocalPlayer player) {
        if (vehicle instanceof RcBoatEntity boat) {
            boat.setLocallyControlled(false);
        }
        if (player != null) {
            player.setInvisible(false);
            Minecraft.getInstance().setCameraEntity(player);
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    @Override
    public void handleTickInput(LocalPlayer player, Entity vehicle, InputSnapshot input, double startX, double startY, double startZ) {
        if (!(vehicle instanceof RcBoatEntity boat)) return;

        player.setPos(startX, startY, startZ);

        boat.inputForward = input.forward();
        boat.inputSteer = input.strafe();

        PacketDistributor.sendToServer(new DroneControlPayload(
                boat.getId(),
                input.forward(),
                input.strafe(),
                input.vertical(),
                boat.getYRot(),
                boat.getPitchAngle(),
                false
        ));
    }

    @Override
    public void computeCameraAngles(ViewportEvent.ComputeCameraAngles event, Entity vehicle, double startX, double startY, double startZ) {
        if (!(vehicle instanceof RcBoatEntity boat)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float partialTick = (float) event.getPartialTick();
        float boatYaw = Mth.rotLerp(partialTick, boat.yRotO, boat.getYRot());
        float boatPitch = Mth.rotLerp(partialTick, boat.prevPitchAngle, boat.getPitchAngle());
        float boatRoll = Mth.rotLerp(partialTick, boat.prevRollAngle, boat.getRollAngle());

        if (viewMode == RcViewMode.GROUND) {
            // 地上プロポ視点（水辺からボートをLook-At追尾）
            double boatX = Mth.lerp(partialTick, boat.xOld, boat.getX());
            double boatY = Mth.lerp(partialTick, boat.yOld, boat.getY());
            double boatZ = Mth.lerp(partialTick, boat.zOld, boat.getZ());

            double eyeX = mc.player.getX();
            double eyeY = mc.player.getEyeY();
            double eyeZ = mc.player.getZ();

            double dx = boatX - eyeX;
            double dy = boatY - eyeY;
            double dz = boatZ - eyeZ;
            double distXZ = Math.sqrt(dx * dx + dz * dz);

            float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
            float targetPitch = (float) (-(Mth.atan2(dy, distXZ) * (180.0 / Math.PI)));

            event.setYaw(targetYaw);
            event.setPitch(targetPitch);
            mc.player.setYRot(targetYaw);
            mc.player.setXRot(targetPitch);
        } else if (viewMode == RcViewMode.CHASE) {
            // チェイス視点（後方から一定見下ろし追従・バニラ補間による滑らか追従）
            event.setYaw(boatYaw);
            event.setPitch(15.0f);
            event.setRoll(0.0f);
        } else {
            // 一人称コックピットFPV視点
            event.setYaw(boatYaw);
            event.setPitch(-boatPitch);
            event.setRoll(boatRoll);
        }
    }

    @Override
    public void toggleViewMode(Entity vehicle, LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (!(vehicle instanceof RcBoatEntity boat)) return;

        if (viewMode == RcViewMode.GROUND) {
            // 地上視点 -> チェイス視点
            viewMode = RcViewMode.CHASE;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.microdrone.cam_chase"), true);
                player.setInvisible(true);
            }
            mc.setCameraEntity(boat);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (viewMode == RcViewMode.CHASE) {
            // チェイス視点 -> 一人称FPV視点
            viewMode = RcViewMode.FIRST_PERSON;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.microdrone.boat_cam_fpv"), true);
                player.setInvisible(true);
            }
            mc.setCameraEntity(boat);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        } else {
            // 一人称FPV視点 -> 地上視点
            viewMode = RcViewMode.GROUND;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.microdrone.cam_ground"), true);
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
        return switch (viewMode) {
            case CHASE -> CameraType.THIRD_PERSON_BACK;
            case FIRST_PERSON -> CameraType.FIRST_PERSON;
            default -> CameraType.FIRST_PERSON;
        };
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

