package com.microdrone.mod.client.controller;

import com.microdrone.mod.entity.RcSubmarineEntity;
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
 * マイクロRC潜水艇専用クライアントコントローラー
 */
public class RcSubmarineClientController implements RcVehicleClientController {

    private RcViewMode viewMode = RcViewMode.GROUND;

    private static Method cameraMoveMethod = null;
    private static boolean cameraMoveResolved = false;

    @Override
    public void onStart(Entity vehicle, LocalPlayer player, double startX, double startY, double startZ) {
        viewMode = RcViewMode.GROUND;
        if (vehicle instanceof RcSubmarineEntity sub) {
            sub.setLocallyControlled(true);
        }
        if (player != null) {
            player.setInvisible(false);
            Minecraft.getInstance().setCameraEntity(player);
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    @Override
    public void onStop(Entity vehicle, LocalPlayer player) {
        if (vehicle instanceof RcSubmarineEntity sub) {
            sub.setLocallyControlled(false);
        }
        if (player != null) {
            player.setInvisible(false);
            Minecraft.getInstance().setCameraEntity(player);
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    @Override
    public void handleTickInput(LocalPlayer player, Entity vehicle, InputSnapshot input, double startX, double startY, double startZ) {
        if (!(vehicle instanceof RcSubmarineEntity sub)) return;

        player.setPos(startX, startY, startZ);

        sub.inputForward = input.forward();
        sub.inputSteer = input.strafe();
        sub.inputVertical = input.vertical();

        PacketDistributor.sendToServer(new DroneControlPayload(
                sub.getId(),
                input.forward(),
                input.strafe(),
                input.vertical(),
                sub.getYRot(),
                sub.getPitchAngle(),
                false
        ));
    }

    @Override
    public void computeCameraAngles(ViewportEvent.ComputeCameraAngles event, Entity vehicle, double startX, double startY, double startZ) {
        if (!(vehicle instanceof RcSubmarineEntity sub)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float partialTick = (float) event.getPartialTick();
        float subYaw = Mth.rotLerp(partialTick, sub.yRotO, sub.getYRot());
        float subPitch = Mth.rotLerp(partialTick, sub.prevPitchAngle, sub.getPitchAngle());
        float subRoll = Mth.rotLerp(partialTick, sub.prevRollAngle, sub.getRollAngle());

        if (viewMode == RcViewMode.GROUND) {
            // 地上/水面プロポ視点（水中の潜水艇をLook-At追尾）
            double subX = Mth.lerp(partialTick, sub.xOld, sub.getX());
            double subY = Mth.lerp(partialTick, sub.yOld, sub.getY());
            double subZ = Mth.lerp(partialTick, sub.zOld, sub.getZ());

            double eyeX = mc.player.getX();
            double eyeY = mc.player.getEyeY();
            double eyeZ = mc.player.getZ();

            double dx = subX - eyeX;
            double dy = subY - eyeY;
            double dz = subZ - eyeZ;
            double distXZ = Math.sqrt(dx * dx + dz * dz);

            float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
            float targetPitch = (float) (-(Mth.atan2(dy, distXZ) * (180.0 / Math.PI)));

            event.setYaw(targetYaw);
            event.setPitch(targetPitch);
            mc.player.setYRot(targetYaw);
            mc.player.setXRot(targetPitch);
        } else if (viewMode == RcViewMode.CHASE) {
            // 水中チェイス視点
            float mousePitch = mc.player.getXRot();
            float pitch = Mth.clamp(subPitch + mousePitch * 0.35f, -60.0f, 60.0f);
            event.setYaw(subYaw);
            event.setPitch(pitch);
            event.setRoll(0.0f);
            mc.player.setYRot(subYaw);
            moveCameraSafe(event.getCamera(), 2.0, 0.3, 0.0);
        } else {
            // 水中探査コックピットFPV視点
            event.setYaw(subYaw);
            event.setPitch(-subPitch);
            event.setRoll(subRoll);
        }
    }

    @Override
    public void toggleViewMode(Entity vehicle, LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (!(vehicle instanceof RcSubmarineEntity sub)) return;

        if (viewMode == RcViewMode.GROUND) {
            // 地上視点 -> チェイス視点
            viewMode = RcViewMode.CHASE;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.microdrone.cam_chase"), true);
                player.setInvisible(true);
            }
            mc.setCameraEntity(sub);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (viewMode == RcViewMode.CHASE) {
            // チェイス視点 -> 水中探査一人称FPV視点
            viewMode = RcViewMode.FIRST_PERSON;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.microdrone.sub_cam_fpv"), true);
                player.setInvisible(true);
            }
            mc.setCameraEntity(sub);
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
