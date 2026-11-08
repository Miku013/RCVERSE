package com.microdrone.mod.client.controller;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.client.ClientModEvents;
import com.microdrone.mod.entity.RcCarEntity;
import com.microdrone.mod.network.CarHeadlightTogglePayload;
import com.microdrone.mod.network.DroneControlPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;

/**
 * マイクロRCカー専用クライアントコントローラー
 */
public class RcCarClientController implements RcVehicleClientController {

    private RcViewMode viewMode = RcViewMode.GROUND;

    public static final double DEFAULT_CAM_DISTANCE = 1.8;
    public static final double MIN_CAM_DISTANCE = 0.5;
    public static final double MAX_CAM_DISTANCE = 6.0;

    public static final float DEFAULT_FOV_MULTIPLIER = 1.0f;
    public static final float MIN_FOV_MULTIPLIER = 0.35f;
    public static final float MAX_FOV_MULTIPLIER = 1.35f;

    private double camDistance = DEFAULT_CAM_DISTANCE;
    private float fovMultiplier = DEFAULT_FOV_MULTIPLIER;

    private static Method cameraMoveMethod = null;
    private static boolean cameraMoveResolved = false;

    @Override
    public void onStart(Entity vehicle, LocalPlayer player, double startX, double startY, double startZ) {
        viewMode = RcViewMode.GROUND;
        camDistance = DEFAULT_CAM_DISTANCE;
        fovMultiplier = DEFAULT_FOV_MULTIPLIER;
        if (vehicle instanceof RcCarEntity car) {
            car.setLocallyControlled(true);
        }
        if (player != null) {
            player.setInvisible(false);
            Minecraft.getInstance().setCameraEntity(player);
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
            MicroDroneMod.LOGGER.info("[CAMERA_SWITCH] Event: START_PILOTING, VehicleUUID: {}, InitialViewMode: GROUND, PlayerPos: [{}, {}, {}]",
                    vehicle.getUUID(), String.format("%.2f", player.getX()), String.format("%.2f", player.getY()), String.format("%.2f", player.getZ()));
        }
    }

    @Override
    public void onStop(Entity vehicle, LocalPlayer player) {
        camDistance = DEFAULT_CAM_DISTANCE;
        fovMultiplier = DEFAULT_FOV_MULTIPLIER;
        if (vehicle instanceof RcCarEntity car) {
            car.setLocallyControlled(false);
        }
        if (player != null) {
            player.setInvisible(false);
            Minecraft.getInstance().setCameraEntity(player);
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
            MicroDroneMod.LOGGER.info("[CAMERA_SWITCH] Event: STOP_PILOTING, VehicleUUID: {}, FinalViewMode: GROUND, PlayerPos: [{}, {}, {}]",
                    vehicle != null ? vehicle.getUUID() : "null", String.format("%.2f", player.getX()), String.format("%.2f", player.getY()), String.format("%.2f", player.getZ()));
        }
    }

    @Override
    public void onKeyInput(int key, int action, Entity vehicle, LocalPlayer player) {
        if (action == GLFW.GLFW_PRESS && vehicle instanceof RcCarEntity car) {
            if (ClientModEvents.KEY_HEADLIGHT.matches(key, 0) || key == GLFW.GLFW_KEY_H) {
                boolean newState = !car.isHeadlightOn();
                car.setHeadlightOn(newState);

                // サーバーへ通知
                PacketDistributor.sendToServer(new CarHeadlightTogglePayload(car.getId()));

                // アクションバー表示＆効果音
                if (player != null) {
                    if (newState) {
                        player.displayClientMessage(Component.translatable("message.microdrone.headlight_on"), true);
                        player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.4f);
                    } else {
                        player.displayClientMessage(Component.translatable("message.microdrone.headlight_off"), true);
                        player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 0.9f);
                    }
                }
            }
        }
    }

    @Override
    public void handleTickInput(LocalPlayer player, Entity vehicle, InputSnapshot input, double startX, double startY, double startZ) {
        if (!(vehicle instanceof RcCarEntity car)) return;

        player.setPos(startX, startY, startZ);
        car.inputForward = input.forward();
        car.inputSteer = input.strafe();
        car.inputHandbrake = (input.vertical() > 0.5f);

        PacketDistributor.sendToServer(new DroneControlPayload(
                car.getId(),
                input.forward(),
                input.strafe(),
                input.vertical(),
                car.getYRot(),
                car.getXRot(),
                false,
                true,
                car.getX(),
                car.getY(),
                car.getZ(),
                car.getCurrentSpeed()
        ));
    }

    @Override
    public void computeCameraAngles(ViewportEvent.ComputeCameraAngles event, Entity vehicle, double startX, double startY, double startZ) {
        if (!(vehicle instanceof RcCarEntity car)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float partialTick = (float) event.getPartialTick();
        float carYaw = Mth.rotLerp(partialTick, car.yRotO, car.getYRot());

        double carX = Mth.lerp(partialTick, car.xOld, car.getX());
        double carY = Mth.lerp(partialTick, car.yOld, car.getY());
        double carZ = Mth.lerp(partialTick, car.zOld, car.getZ());

        if (viewMode == RcViewMode.GROUND) {
            double eyeX = mc.player.getX();
            double eyeY = mc.player.getEyeY();
            double eyeZ = mc.player.getZ();

            double dx = carX - eyeX;
            double dy = carY - eyeY;
            double dz = carZ - eyeZ;
            double distXZ = Math.sqrt(dx * dx + dz * dz);

            float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
            float targetPitch = (float) (-(Mth.atan2(dy, distXZ) * (180.0 / Math.PI)));

            event.setYaw(targetYaw);
            event.setPitch(targetPitch);

            mc.player.setYRot(targetYaw);
            mc.player.setXRot(targetPitch);
        } else {
            // CHASE視点
            float mousePitch = mc.player.getXRot();
            float pitch = Mth.clamp(20.0f + mousePitch * 0.35f, 10.0f, 60.0f);
            event.setYaw(carYaw);
            event.setPitch(pitch);
            mc.player.setYRot(carYaw);
            double camHeight = 0.15 + (camDistance * 0.08);
            moveCameraSafe(event.getCamera(), camDistance, camHeight, 0.0);
        }
    }

    @Override
    public void toggleViewMode(Entity vehicle, LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (!(vehicle instanceof RcCarEntity car)) return;

        RcViewMode oldMode = viewMode;
        if (viewMode == RcViewMode.GROUND) {
            viewMode = RcViewMode.CHASE;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.microdrone.cam_chase"), true);
                player.setInvisible(true);
            }
            mc.setCameraEntity(car);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else {
            viewMode = RcViewMode.GROUND;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.microdrone.cam_ground"), true);
                player.setInvisible(false);
            }
            mc.setCameraEntity(player);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }

        MicroDroneMod.LOGGER.info("[CAMERA_SWITCH] VehicleUUID: {}, OldMode: {}, NewMode: {}, PlayerPos: [{}, {}, {}], CarPos: [{}, {}, {}]",
                car.getUUID(), oldMode, viewMode,
                player != null ? String.format("%.2f, %.2f, %.2f", player.getX(), player.getY(), player.getZ()) : "null",
                String.format("%.2f, %.2f, %.2f", car.getX(), car.getY(), car.getZ()));
    }

    @Override
    public RcViewMode getViewMode() {
        return viewMode;
    }

    @Override
    public CameraType getExpectedCameraType() {
        return (viewMode == RcViewMode.CHASE) ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON;
    }

    @Override
    public boolean onMouseScroll(double scrollDeltaY, boolean isShiftDown, Entity vehicle, LocalPlayer player) {
        if (viewMode != RcViewMode.CHASE) return false;

        if (isShiftDown) {
            // FOV望遠/広角ズーム (レンズ倍率)
            if (scrollDeltaY > 0) {
                // 奥スクロール -> 拡大（望遠）
                fovMultiplier = Math.max(MIN_FOV_MULTIPLIER, fovMultiplier - 0.05f);
            } else if (scrollDeltaY < 0) {
                // 手前スクロール -> 縮小（広角）
                fovMultiplier = Math.min(MAX_FOV_MULTIPLIER, fovMultiplier + 0.05f);
            }
        } else {
            // カメラ距離ズーム
            if (scrollDeltaY > 0) {
                // 奥スクロール -> 車体に近づく（ズームイン）
                camDistance = Math.max(MIN_CAM_DISTANCE, camDistance - 0.2);
            } else if (scrollDeltaY < 0) {
                // 手前スクロール -> 車体から離れる（ズームアウト）
                camDistance = Math.min(MAX_CAM_DISTANCE, camDistance + 0.2);
            }
        }

        if (player != null) {
            float zoomX = 1.0f / fovMultiplier;
            player.displayClientMessage(Component.literal(String.format("§6[RC Camera] §f距離: §e%.1fm §f| レンズ: §e%.2fx §7(中クリックでリセット)", camDistance, zoomX)), true);
        }
        return true;
    }

    @Override
    public boolean onMouseButton(int button, int action, int mods, Entity vehicle, LocalPlayer player) {
        if (viewMode != RcViewMode.CHASE) return false;

        // GLFW_MOUSE_BUTTON_MIDDLE = 2
        if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE && action == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            camDistance = DEFAULT_CAM_DISTANCE;
            fovMultiplier = DEFAULT_FOV_MULTIPLIER;
            if (player != null) {
                player.displayClientMessage(Component.literal(String.format("§a[RC Camera] §fズームをリセットしました §7(距離: %.1fm, レンズ: 1.00x)", camDistance)), true);
            }
            return true;
        }
        return false;
    }

    @Override
    public void computeFov(ViewportEvent.ComputeFov event, Entity vehicle) {
        if (viewMode == RcViewMode.CHASE) {
            event.setFOV(event.getFOV() * fovMultiplier);
        }
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
