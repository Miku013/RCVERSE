package com.microdrone.mod.client.controller;

import com.microdrone.mod.entity.spider.RcSpiderEntity;
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
 * RC多脚ロボット（ヘキサポッド）専用クライアントコントローラー
 * - プロシージャル歩行の前後進・旋回
 * - タレット旋回・射撃
 * - 地上視点 / FPV視点 / チェイス視点の切り替え
 */
public class RcSpiderClientController implements RcVehicleClientController {

    private RcViewMode viewMode = RcViewMode.GROUND;

    private static Method cameraMoveMethod = null;
    private static boolean cameraMoveResolved = false;

    @Override
    public void onStart(Entity vehicle, LocalPlayer player, double startX, double startY, double startZ) {
        viewMode = RcViewMode.GROUND;
        if (player != null) {
            player.setInvisible(false);
            Minecraft.getInstance().setCameraEntity(player);
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
            player.displayClientMessage(Component.translatable("message.microdrone.spider_cam_ground"), true);
        }
    }

    @Override
    public void onStop(Entity vehicle, LocalPlayer player) {
        if (player != null) {
            player.setInvisible(false);
            Minecraft.getInstance().setCameraEntity(player);
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    @Override
    public void handleTickInput(LocalPlayer player, Entity vehicle, InputSnapshot input, double startX, double startY, double startZ) {
        if (!(vehicle instanceof RcSpiderEntity spider)) return;

        player.setPos(startX, startY, startZ);

        spider.inputThrottle = input.forward();
        spider.inputSteer = input.strafe();
        spider.inputFire = (input.vertical() > 0.5f);

        float mouseYaw = player.getYRot();
        float mousePitch = player.getXRot();
        float relativeYaw = Mth.wrapDegrees(mouseYaw - spider.getYRot());

        spider.targetTurretYaw = relativeYaw;

        // サーバーへ操作入力をパケット送信
        PacketDistributor.sendToServer(new DroneControlPayload(
                spider.getId(),
                input.forward(),
                input.strafe(),
                input.vertical(),
                mouseYaw,
                mousePitch,
                false
        ));
    }

    @Override
    public void computeCameraAngles(ViewportEvent.ComputeCameraAngles event, Entity vehicle, double startX, double startY, double startZ) {
        if (!(vehicle instanceof RcSpiderEntity spider) || spider.isRemoved()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float partialTick = (float) event.getPartialTick();
        float mouseYaw = mc.player.getYRot();
        float mousePitch = mc.player.getXRot();
        float relativeYaw = Mth.wrapDegrees(mouseYaw - spider.getYRot());

        spider.targetTurretYaw = relativeYaw;
        spider.clientTurretYaw = relativeYaw;

        if (viewMode == RcViewMode.GROUND) {
            double spX = Mth.lerp(partialTick, spider.xOld, spider.getX());
            double spY = Mth.lerp(partialTick, spider.yOld, spider.getY());
            double spZ = Mth.lerp(partialTick, spider.zOld, spider.getZ());
            double eyeX = mc.player.getX();
            double eyeY = mc.player.getEyeY();
            double eyeZ = mc.player.getZ();
            double dx = spX - eyeX;
            double dy = spY - eyeY;
            double dz = spZ - eyeZ;
            double distXZ = Math.sqrt(dx * dx + dz * dz);
            float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
            float targetPitch = (float) (-(Mth.atan2(dy, distXZ) * (180.0 / Math.PI)));

            event.setYaw(targetYaw);
            event.setPitch(targetPitch);
        } else if (viewMode == RcViewMode.CHASE) {
            float pitch = Mth.clamp(18.0f + mousePitch * 0.45f, 5.0f, 65.0f);
            event.setYaw(mouseYaw);
            event.setPitch(pitch);
            moveCameraSafe(event.getCamera(), 2.0, 0.4, 0.0);
        } else {
            // FPVカメラ視点（機体の上部カメラから前方を望む）
            float vehicleYaw = Mth.rotLerp(partialTick, spider.yRotO, spider.getYRot());
            float effectiveYaw = vehicleYaw + relativeYaw;
            event.setYaw(effectiveYaw);
            event.setPitch(mousePitch);
            moveCameraSafe(event.getCamera(), 0.0, 0.35, 0.0);
        }
    }

    @Override
    public void toggleViewMode(Entity vehicle, LocalPlayer player) {
        if (viewMode == RcViewMode.GROUND) {
            viewMode = RcViewMode.CHASE;
            Minecraft.getInstance().setCameraEntity(vehicle);
            Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.microdrone.spider_cam_chase"), true);
                player.setInvisible(true);
            }
        } else if (viewMode == RcViewMode.CHASE) {
            viewMode = RcViewMode.FIRST_PERSON;
            Minecraft.getInstance().setCameraEntity(vehicle);
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.microdrone.spider_cam_fpv"), true);
                player.setInvisible(true);
            }
        } else {
            viewMode = RcViewMode.GROUND;
            Minecraft.getInstance().setCameraEntity(player);
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.microdrone.spider_cam_ground"), true);
                player.setInvisible(false);
            }
        }
    }

    @Override
    public CameraType getExpectedCameraType() {
        return (viewMode == RcViewMode.CHASE) ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON;
    }

    @Override
    public RcViewMode getViewMode() {
        return viewMode;
    }

    private static void moveCameraSafe(Camera camera, double distance, double height, double strafe) {
        if (!cameraMoveResolved) {
            cameraMoveResolved = true;
            try {
                cameraMoveMethod = Camera.class.getDeclaredMethod("move", double.class, double.class, double.class);
                cameraMoveMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
                try {
                    cameraMoveMethod = Camera.class.getDeclaredMethod("m_90581_", double.class, double.class, double.class);
                    cameraMoveMethod.setAccessible(true);
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        if (cameraMoveMethod != null) {
            try {
                cameraMoveMethod.invoke(camera, -distance, height, strafe);
            } catch (Exception ignored) {
            }
        }
    }
}
