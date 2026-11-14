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
    public static final double MAX_CAM_DISTANCE = 8.0;

    public static final float DEFAULT_FOV_MULTIPLIER = 1.0f;
    public static final float MIN_FOV_MULTIPLIER = 0.35f;
    public static final float MAX_FOV_MULTIPLIER = 1.35f;

    private double camDistance = DEFAULT_CAM_DISTANCE;
    private float fovMultiplier = DEFAULT_FOV_MULTIPLIER;

    // シネマティックカメラ補間用変数 (ジンバル＆オービット)
    private float smoothCamYaw = 0.0f;
    private float smoothCamPitch = 20.0f;
    private float orbitYawOffset = 0.0f;
    private float orbitPitchOffset = 0.0f;
    private float lastPlayerMouseYaw = 0.0f;
    private float lastPlayerMousePitch = 0.0f;
    private boolean isCameraInitialized = false;

    private static Method cameraMoveMethod = null;
    private static boolean cameraMoveResolved = false;

    @Override
    public void onStart(Entity vehicle, LocalPlayer player, double startX, double startY, double startZ) {
        // 撮影MODではデフォルトでシネマティックスムーズ追従視点
        viewMode = RcViewMode.CHASE;
        camDistance = DEFAULT_CAM_DISTANCE;
        fovMultiplier = DEFAULT_FOV_MULTIPLIER;
        orbitYawOffset = 0.0f;
        orbitPitchOffset = 0.0f;
        isCameraInitialized = false;

        if (vehicle instanceof RcCarEntity car) {
            car.setLocallyControlled(true);
            smoothCamYaw = car.getYRot();
            smoothCamPitch = 20.0f;
        }

        if (player != null) {
            player.setInvisible(false); // ReplayMod用: 立ち位置にプロポを持った操縦者として自然に配置
            Minecraft mc = Minecraft.getInstance();
            if (vehicle != null) {
                mc.setCameraEntity(vehicle);
            }
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            lastPlayerMouseYaw = player.getYRot();
            lastPlayerMousePitch = player.getXRot();
            MicroDroneMod.LOGGER.info("[CINEMATIC_CAMERA] Event: START_PILOTING, VehicleUUID: {}, Mode: CHASE",
                    vehicle != null ? vehicle.getUUID() : "null");
        }
    }

    @Override
    public void onStop(Entity vehicle, LocalPlayer player) {
        camDistance = DEFAULT_CAM_DISTANCE;
        fovMultiplier = DEFAULT_FOV_MULTIPLIER;
        orbitYawOffset = 0.0f;
        orbitPitchOffset = 0.0f;
        isCameraInitialized = false;

        if (vehicle instanceof RcCarEntity car) {
            car.setLocallyControlled(false);
        }
        if (player != null) {
            player.setInvisible(false);
            Minecraft.getInstance().setCameraEntity(player);
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
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

        // ReplayMod撮影対応: プレイヤーは操縦開始時の立ち位置(startX, startY, startZ)に静止！
        // 車にプレイヤーをテレポートさせないため、車にプレイヤーが張り付く現象を完全に根絶！
        player.setPos(startX, startY, startZ);
        player.setDeltaMovement(0, 0, 0);

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

        if (viewMode == RcViewMode.CHASE) {
            // === シネマティック・ジンバルスムーズ追従カメラ ===
            // マウスの首振り移動量をオービット相対角（フリー回転）に加算
            float currentMouseYaw = mc.player.getYRot();
            float currentMousePitch = mc.player.getXRot();
            float deltaYaw = Mth.wrapDegrees(currentMouseYaw - lastPlayerMouseYaw);
            float deltaPitch = currentMousePitch - lastPlayerMousePitch;
            lastPlayerMouseYaw = currentMouseYaw;
            lastPlayerMousePitch = currentMousePitch;

            orbitYawOffset += deltaYaw;
            orbitPitchOffset = Mth.clamp(orbitPitchOffset + deltaPitch, -25.0f, 40.0f);

            // 目標カメラ角度: 車の進行向き + マウスで調整した自由アングル
            float targetYaw = carYaw + orbitYawOffset;
            float targetPitch = Mth.clamp(18.0f + orbitPitchOffset, 5.0f, 75.0f);

            if (!isCameraInitialized) {
                smoothCamYaw = targetYaw;
                smoothCamPitch = targetPitch;
                isCameraInitialized = true;
            } else {
                // 滑らかなジンバル補間 (Lerp): ドリフト時の急激な首振りを映画カメラのように吸収
                smoothCamYaw = Mth.rotLerp(0.08f, smoothCamYaw, targetYaw);
                smoothCamPitch = Mth.lerp(0.12f, smoothCamPitch, targetPitch);
            }

            event.setYaw(smoothCamYaw);
            event.setPitch(smoothCamPitch);

            double camHeight = 0.20 + (camDistance * 0.10);
            moveCameraSafe(event.getCamera(), camDistance, camHeight, 0.0);
        } else {
            // === 三脚フリーカメラ (TRIPOD FREE) ===
            // プレイヤー自身がマウスで自由にパン・チルトして定点撮影可能（車への強制ロック完全なし）
            event.setYaw(mc.player.getYRot());
            event.setPitch(mc.player.getXRot());
        }
    }

    @Override
    public void toggleViewMode(Entity vehicle, LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (!(vehicle instanceof RcCarEntity car)) return;

        if (viewMode == RcViewMode.CHASE) {
            // CHASE -> TRIPOD FREE (三脚フリーカメラ)
            viewMode = RcViewMode.GROUND;
            if (player != null) {
                player.displayClientMessage(Component.literal("§b[RCVERSE 撮影] §e三脚フリー視点 §7(マウスで自由にアングルを操作して定点撮影)"), true);
                player.setInvisible(false);
            }
            mc.setCameraEntity(player);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        } else {
            // TRIPOD FREE -> CHASE (シネマティックスムーズ追従カメラ)
            viewMode = RcViewMode.CHASE;
            orbitYawOffset = 0.0f;
            orbitPitchOffset = 0.0f;
            isCameraInitialized = false;
            if (player != null) {
                player.displayClientMessage(Component.literal("§b[RCVERSE 撮影] §aシネマティック追従視点 §7(手ブレゼロ・ジンバル補間カメラ)"), true);
                player.setInvisible(false);
                lastPlayerMouseYaw = player.getYRot();
                lastPlayerMousePitch = player.getXRot();
            }
            mc.setCameraEntity(car);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
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
            orbitYawOffset = 0.0f;
            orbitPitchOffset = 0.0f;
            isCameraInitialized = false;
            if (player != null) {
                player.displayClientMessage(Component.literal(String.format("§a[RC Camera] §fアングルとズームをリセットしました §7(距離: %.1fm, レンズ: 1.00x)", camDistance)), true);
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
