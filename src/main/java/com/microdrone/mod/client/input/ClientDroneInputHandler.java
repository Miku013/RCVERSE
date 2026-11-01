package com.microdrone.mod.client.input;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.client.camera.DroneCameraController;
import com.microdrone.mod.entity.DroneEntity;
import com.microdrone.mod.entity.RcCarEntity;
import com.microdrone.mod.entity.RcPlaneEntity;
import com.microdrone.mod.entity.RcTankEntity;
import com.microdrone.mod.entity.RcControllable;
import com.microdrone.mod.network.DroneControlPayload;
import com.microdrone.mod.network.PlaneModeTogglePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class ClientDroneInputHandler {

    private static boolean isPiloting = false;
    private static int activeDroneId = -1;

    // 操縦開始時の初期位置 (電波距離・HUD表示用)
    private static double startX = 0;
    private static double startY = 0;
    private static double startZ = 0;

    public static double getStartX() { return startX; }
    public static double getStartY() { return startY; }
    public static double getStartZ() { return startZ; }

    private static float lastPlayerYaw = 0.0f;
    private static float capturedForward = 0.0f;
    private static float capturedStrafe = 0.0f;
    private static float capturedVertical = 0.0f;

    public enum RcCameraMode {
        GROUND_RC,
        CHASE_CAR,
        GUNNER_AIM
    }

    private static RcCameraMode carCameraMode = RcCameraMode.GROUND_RC;

    public static RcCameraMode getCarCameraMode() {
        return carCameraMode;
    }

    public static boolean isGroundVehiclePiloting() {
        if (!isPiloting || activeDroneId == -1) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        Entity e = mc.level.getEntity(activeDroneId);
        return e instanceof RcCarEntity || e instanceof RcTankEntity || e instanceof RcPlaneEntity;
    }

    public static boolean isRcCarPiloting() {
        if (!isPiloting || activeDroneId == -1) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        return mc.level.getEntity(activeDroneId) instanceof RcCarEntity;
    }

    public static boolean isRcTankPiloting() {
        if (!isPiloting || activeDroneId == -1) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        return mc.level.getEntity(activeDroneId) instanceof RcTankEntity;
    }

    public static boolean isRcPlanePiloting() {
        if (!isPiloting || activeDroneId == -1) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        return mc.level.getEntity(activeDroneId) instanceof RcPlaneEntity;
    }

    public static void toggleGroundCameraMode() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || activeDroneId == -1) return;
        Entity entity = mc.level.getEntity(activeDroneId);

        mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);

        if (entity instanceof RcCarEntity car) {
            if (carCameraMode == RcCameraMode.GROUND_RC) {
                carCameraMode = RcCameraMode.CHASE_CAR;
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.translatable("message.microdrone.cam_chase"), true);
                    mc.player.setInvisible(true);
                }
                mc.setCameraEntity(car);
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            } else {
                carCameraMode = RcCameraMode.GROUND_RC;
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.translatable("message.microdrone.cam_ground"), true);
                    mc.player.setInvisible(false);
                }
                mc.setCameraEntity(mc.player);
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            }
        } else if (entity instanceof RcTankEntity tank) {
            if (carCameraMode == RcCameraMode.GROUND_RC) {
                // 地上プロポ視点 -> 車載チェイス視点（三人称後方追従）
                carCameraMode = RcCameraMode.CHASE_CAR;
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.translatable("message.microdrone.tank_cam_chase"), true);
                    mc.player.setInvisible(true);
                }
                mc.setCameraEntity(tank);
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            } else if (carCameraMode == RcCameraMode.CHASE_CAR) {
                // 車載チェイス視点 -> ガンナー照準視点（砲塔同軸FPS・専用HUD）
                carCameraMode = RcCameraMode.GUNNER_AIM;
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.translatable("message.microdrone.tank_cam_gunner"), true);
                    mc.player.setInvisible(true);
                }
                mc.setCameraEntity(tank);
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            } else {
                // ガンナー照準視点 -> 地上プロポ視点
                carCameraMode = RcCameraMode.GROUND_RC;
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.translatable("message.microdrone.tank_cam_ground"), true);
                    mc.player.setInvisible(false);
                }
                mc.setCameraEntity(mc.player);
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            }
        } else if (entity instanceof RcPlaneEntity plane) {
            if (carCameraMode == RcCameraMode.GROUND_RC) {
                // 地上プロポ視点 -> チェイス視点（三人称後方追従）
                carCameraMode = RcCameraMode.CHASE_CAR;
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.translatable("message.microdrone.plane_cam_chase"), true);
                    mc.player.setInvisible(true);
                }
                mc.setCameraEntity(plane);
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            } else if (carCameraMode == RcCameraMode.CHASE_CAR) {
                // チェイス視点 -> コックピット一人称FPV視点
                carCameraMode = RcCameraMode.GUNNER_AIM;
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.translatable("message.microdrone.plane_cam_cockpit"), true);
                    mc.player.setInvisible(true);
                }
                mc.setCameraEntity(plane);
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            } else {
                // コックピット視点 -> 地上プロポ視点（地上で見上げるRC飛行場感覚）
                carCameraMode = RcCameraMode.GROUND_RC;
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.translatable("message.microdrone.plane_cam_ground"), true);
                    mc.player.setInvisible(false);
                }
                mc.setCameraEntity(mc.player);
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            }
        }
    }

    public static void register() {
        NeoForge.EVENT_BUS.register(new ClientDroneInputHandler());
    }

    public static boolean isPiloting() {
        return isPiloting;
    }

    public static int getActiveDroneId() {
        return activeDroneId;
    }

    public static void setPiloting(int droneId, boolean state) {
        setPiloting(droneId, state, 0, 0, 0);
    }

    public static void setPiloting(int droneId, boolean state, double sx, double sy, double sz) {
        startX = sx;
        startY = sy;
        startZ = sz;
        Minecraft mc = Minecraft.getInstance();
        missingTicks = 0;
        MicroDroneMod.LOGGER.info("[CLIENT] [PILOT_STATE_SET] DroneId: {}, State: {}, PlayerPos: {}",
                droneId, state,
                mc.player != null ? String.format("[%.2f, %.2f, %.2f]", mc.player.getX(), mc.player.getY(), mc.player.getZ()) : "null");

        if (state) {
            isPiloting = true;
            activeDroneId = droneId;
            carCameraMode = RcCameraMode.GROUND_RC;

            Entity targetEnt = mc.level != null ? mc.level.getEntity(droneId) : null;
            if (targetEnt instanceof DroneEntity drone) {
                if (mc.player != null) {
                    lastPlayerYaw = mc.player.getYRot();
                    mc.player.setInvisible(true);
                }
                drone.setLocallyControlled(true);
                com.microdrone.mod.client.audio.DroneSoundManager.startPilotingSound(drone);
                DroneCameraController.startViewing(droneId);
            } else if (targetEnt instanceof RcCarEntity car) {
                carCameraMode = RcCameraMode.GROUND_RC;
                if (mc.player != null) {
                    lastPlayerYaw = mc.player.getYRot();
                    mc.player.setInvisible(false);
                    mc.setCameraEntity(mc.player);
                }
                car.setLocallyControlled(true);
            } else if (targetEnt instanceof RcTankEntity tank) {
                carCameraMode = RcCameraMode.GROUND_RC;
                if (mc.player != null) {
                    lastPlayerYaw = mc.player.getYRot();
                    mc.player.setInvisible(false);
                    mc.setCameraEntity(mc.player);
                    mc.player.displayClientMessage(Component.translatable("message.microdrone.tank_cam_ground"), true);
                }
                tank.setLocallyControlled(true);
            } else if (targetEnt instanceof RcPlaneEntity plane) {
                carCameraMode = RcCameraMode.GROUND_RC;
                if (mc.player != null) {
                    lastPlayerYaw = mc.player.getYRot();
                    mc.player.setInvisible(false);
                    mc.setCameraEntity(mc.player);
                    mc.player.displayClientMessage(Component.translatable("message.microdrone.plane_cam_ground"), true);
                }
                plane.setLocallyControlled(true);
            }
        } else {
            com.microdrone.mod.client.audio.DroneSoundManager.stopPilotingSound();

            if (mc.player != null) {
                mc.player.setInvisible(false);
                mc.setCameraEntity(mc.player);
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            }

            if (mc.level != null && activeDroneId != -1) {
                Entity entity = mc.level.getEntity(activeDroneId);
                if (entity instanceof DroneEntity drone) {
                    drone.setLocallyControlled(false);
                } else if (entity instanceof RcCarEntity car) {
                    car.setLocallyControlled(false);
                } else if (entity instanceof RcTankEntity tank) {
                    tank.setLocallyControlled(false);
                } else if (entity instanceof RcPlaneEntity plane) {
                    plane.setLocallyControlled(false);
                }
            }

            carCameraMode = RcCameraMode.GROUND_RC;
            isPiloting = false;
            activeDroneId = -1;
            capturedForward = 0.0f;
            capturedStrafe = 0.0f;
            capturedVertical = 0.0f;

            DroneCameraController.stopViewing();
        }
    }

    public static void stopPiloting() {
        if (isPiloting && activeDroneId != -1) {
            MicroDroneMod.LOGGER.info("[CLIENT] [PILOT_STOP_REQUEST] ActiveDroneId: {}", activeDroneId);
            PacketDistributor.sendToServer(new DroneControlPayload(activeDroneId, 0, 0, 0, 0, 0, true));
            setPiloting(-1, false);
        }
    }

    @SubscribeEvent
    public void onMovementInput(MovementInputUpdateEvent event) {
        if (!isPiloting) return;

        capturedForward = event.getInput().forwardImpulse;
        capturedStrafe = event.getInput().leftImpulse;

        float vertical = 0.0f;
        if (event.getInput().jumping) {
            vertical += 1.0f;
        }
        if (event.getInput().shiftKeyDown) {
            vertical -= 1.0f;
        }
        capturedVertical = vertical;

        // プレイヤー自身の移動を抑制
        event.getInput().forwardImpulse = 0.0f;
        event.getInput().leftImpulse = 0.0f;
        event.getInput().jumping = false;
        event.getInput().shiftKeyDown = false;
    }

    private static int missingTicks = 0;

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Pre event) {
        if (!isPiloting || activeDroneId == -1) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            setPiloting(-1, false);
            return;
        }

        // F5キーのインターセプト
        while (mc.options.keyTogglePerspective.consumeClick()) {
            if (isGroundVehiclePiloting()) {
                toggleGroundCameraMode();
            }
        }
        net.minecraft.client.CameraType expectedCameraType = (isGroundVehiclePiloting() && carCameraMode == RcCameraMode.CHASE_CAR)
                ? net.minecraft.client.CameraType.THIRD_PERSON_BACK
                : net.minecraft.client.CameraType.FIRST_PERSON;
        if (mc.options.getCameraType() != expectedCameraType) {
            mc.options.setCameraType(expectedCameraType);
        }

        Entity entity = mc.level.getEntity(activeDroneId);
        if (entity == null || entity.isRemoved() || !(entity instanceof RcControllable)) {
            missingTicks++;
            if (missingTicks > 40) {
                stopPiloting();
            }
            return;
        }

        missingTicks = 0;

        if (entity instanceof DroneEntity drone) {
            player.setPos(drone.getX(), drone.getY(), drone.getZ());
            float currentYaw = player.getYRot();
            float currentPitch = player.getXRot();
            drone.applyControlInput(capturedForward, capturedStrafe, capturedVertical, currentYaw, currentPitch);

            PacketDistributor.sendToServer(new DroneControlPayload(
                    activeDroneId,
                    capturedForward,
                    capturedStrafe,
                    capturedVertical,
                    currentYaw,
                    currentPitch,
                    false
            ));
        } else if (entity instanceof RcCarEntity car) {
            if (!car.hasBattery() || car.getBatteryRemaining() <= 0) {
                stopPiloting();
                return;
            }
            player.setPos(startX, startY, startZ);
            car.inputForward = capturedForward;
            car.inputSteer = capturedStrafe;
            car.inputHandbrake = (capturedVertical > 0.5f);

            PacketDistributor.sendToServer(new DroneControlPayload(
                    activeDroneId,
                    capturedForward,
                    capturedStrafe,
                    capturedVertical,
                    car.getYRot(),
                    car.getXRot(),
                    false
            ));
        } else if (entity instanceof RcTankEntity tank) {
            if (!tank.hasBattery() || tank.getBatteryRemaining() <= 0) {
                stopPiloting();
                return;
            }
            player.setPos(startX, startY, startZ);
            tank.inputForward = capturedForward;
            tank.inputSteer = capturedStrafe;
            tank.inputFire = (capturedVertical > 0.5f);

            float mouseYaw = player.getYRot();
            float mousePitch = player.getXRot();
            float relativeYaw = Mth.wrapDegrees(mouseYaw - tank.getYRot());
            float cannonPitch = Mth.clamp(mousePitch, -15.0f, 35.0f);

            tank.targetTurretYaw = relativeYaw;
            tank.targetCannonPitch = cannonPitch;

            PacketDistributor.sendToServer(new DroneControlPayload(
                    activeDroneId,
                    capturedForward,
                    capturedStrafe,
                    capturedVertical,
                    mouseYaw,
                    cannonPitch,
                    false
            ));
        } else if (entity instanceof RcPlaneEntity plane) {
            if (!plane.hasBattery() || plane.getBatteryRemaining() <= 0) {
                stopPiloting();
                return;
            }
            player.setPos(startX, startY, startZ);
            player.setDeltaMovement(0, 0, 0); // プレイヤーの微小落下による画面テレポートジッターを完全防止
            player.fallDistance = 0.0f;

            // クライアント側へ即座に反映（ノーディレイ操作感）
            // W: ピッチダウン(-1.0: 機首下げ), S: ピッチアップ(+1.0: 機首引き上げ・上昇離陸)
            plane.inputElevator = -capturedForward;
            // A: 左ロール/左傾斜(-1.0), D: 右ロール/右傾斜(+1.0)
            plane.inputAileron = -capturedStrafe;
            // Space: スロットル増(+1.0), Shift: スロットル減(-1.0)
            plane.inputThrottleDelta = capturedVertical;

            // ラダー（方向舵）および地上ステアリングの計算
            // Aキー(左)で負(-1.0)、Dキー(右)で正(+1.0)
            float rudder = 0.0f;

            if (plane.onGround()) {
                // === 【地上タキシング＆滑走時】 ===
                // A/D キーで直感的に尾輪ステアリング（Aで左旋回、Dで右旋回）
                // キーを離せば瞬時に 0（直進ニュートラル）に戻り、まっすぐ滑走！
                rudder = -capturedStrafe;
            } else {
                // === 【空中飛行時】 ===
                if (carCameraMode == RcCameraMode.GROUND_RC) {
                    // 地上プロポ視点:
                    // 旋回は A/D キーの操作を100%忠実に反映！
                    rudder = -capturedStrafe * 0.7f;
                } else {
                    // チェイス視点 または コックピット視点:
                    float keyRudder = -capturedStrafe * 0.7f;

                    // マウスの向きによるアシスト旋回 (不感帯デッドゾーン付き)
                    float mouseRudder = 0.0f;
                    float yawDiff = Mth.wrapDegrees(player.getYRot() - plane.getYRot());

                    // デッドゾーン (±3.5度以内は直進維持)
                    if (Math.abs(yawDiff) > 3.5f) {
                        float effectiveDiff = yawDiff - Math.signum(yawDiff) * 3.5f;
                        mouseRudder = Mth.clamp(effectiveDiff * 0.012f, -0.6f, 0.6f);
                    }

                    if (Math.abs(capturedStrafe) > 0.05f) {
                        rudder = keyRudder;
                    } else {
                        rudder = mouseRudder;
                    }
                }
            }
            plane.inputRudder = rudder;

            // サーバーへ送信 (yaw に計算済み rudder を格納して同期)
            PacketDistributor.sendToServer(new DroneControlPayload(
                    activeDroneId,
                    capturedForward,
                    capturedStrafe,
                    capturedVertical,
                    rudder,
                    plane.getPitchAngle(),
                    false
            ));
        }
    }

    /**
     * 【F5キーでの視点切替 ＆ Rキーでのフライトモード切替 (ASSIST ⇔ ACRO)】
     */
    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        if (!isPiloting || activeDroneId == -1) return;
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(activeDroneId);

        // R キーでプレーン機のハイブリッドフライトモード切替！
        if (event.getKey() == GLFW.GLFW_KEY_R && entity instanceof RcPlaneEntity plane) {
            int currentMode = plane.getFlightMode();
            int newMode = (currentMode == 0) ? 1 : 0;
            plane.setFlightMode(newMode);

            // サーバーへ通知
            PacketDistributor.sendToServer(new PlaneModeTogglePayload(plane.getId(), newMode));

            // アクションバー表示＆効果音
            if (mc.player != null) {
                if (newMode == 0) {
                    mc.player.displayClientMessage(Component.translatable("message.microdrone.flight_mode_assist"), true);
                    mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
                } else {
                    mc.player.displayClientMessage(Component.translatable("message.microdrone.flight_mode_acro"), true);
                    mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 0.8f);
                }
            }
        }
    }

    @SubscribeEvent
    public void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!isPiloting || activeDroneId == -1) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            Entity entity = mc.level.getEntity(activeDroneId);
            if (entity instanceof DroneEntity drone) {
                float yaw = mc.player.getYRot();
                float pitch = mc.player.getXRot();

                event.setYaw(yaw);
                event.setPitch(pitch);

                drone.setYRot(yaw);
                drone.setYHeadRot(yaw);
                drone.setXRot(pitch);
            } else if (entity instanceof RcCarEntity car) {
                float partialTick = (float) event.getPartialTick();
                float carYaw = Mth.rotLerp(partialTick, car.yRotO, car.getYRot());

                double carX = Mth.lerp(partialTick, car.xOld, car.getX());
                double carY = Mth.lerp(partialTick, car.yOld, car.getY());
                double carZ = Mth.lerp(partialTick, car.zOld, car.getZ());

                if (carCameraMode == RcCameraMode.GROUND_RC) {
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
                    float mousePitch = mc.player.getXRot();
                    float pitch = Mth.clamp(20.0f + mousePitch * 0.35f, 10.0f, 60.0f);
                    event.setYaw(carYaw);
                    event.setPitch(pitch);
                    mc.player.setYRot(carYaw);
                    moveCameraSafe(event.getCamera(), 1.8, 0.2, 0.0);
                }
            } else if (entity instanceof RcTankEntity tank) {
                float partialTick = (float) event.getPartialTick();

                float mouseYaw = mc.player.getYRot();
                float mousePitch = mc.player.getXRot();
                float relativeYaw = Mth.wrapDegrees(mouseYaw - tank.getYRot());
                float cannonPitch = Mth.clamp(mousePitch, -15.0f, 35.0f);

                tank.targetTurretYaw = relativeYaw;
                tank.targetCannonPitch = cannonPitch;
                tank.clientTurretYaw = relativeYaw;
                tank.clientCannonPitch = cannonPitch;

                if (carCameraMode == RcCameraMode.GROUND_RC) {
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
                } else if (carCameraMode == RcCameraMode.CHASE_CAR) {
                    float pitch = Mth.clamp(18.0f + mousePitch * 0.45f, 5.0f, 65.0f);
                    event.setYaw(mouseYaw);
                    event.setPitch(pitch);
                    moveCameraSafe(event.getCamera(), 1.8, 0.2, 0.0);
                } else if (carCameraMode == RcCameraMode.GUNNER_AIM) {
                    event.setYaw(mouseYaw);
                    event.setPitch(mousePitch);
                }
            } else if (entity instanceof RcPlaneEntity plane) {
                float partialTick = (float) event.getPartialTick();
                float planeYaw = Mth.rotLerp(partialTick, plane.yRotO, plane.getYRot());
                float planePitch = Mth.lerp(partialTick, plane.prevPitchAngle, plane.getPitchAngle());
                float planeRoll = Mth.lerp(partialTick, plane.prevRollAngle, plane.getRollAngle());

                if (carCameraMode == RcCameraMode.GROUND_RC) {
                    // === 【モード1: 地上プロポ視点（RC飛行場感覚・見上げる視点）】 ===
                    double planeX = Mth.lerp(partialTick, plane.xOld, plane.getX());
                    double planeY = Mth.lerp(partialTick, plane.yOld, plane.getY());
                    double planeZ = Mth.lerp(partialTick, plane.zOld, plane.getZ());
                    double eyeX = mc.player.getX();
                    double eyeY = mc.player.getEyeY();
                    double eyeZ = mc.player.getZ();
                    double dx = planeX - eyeX;
                    double dy = planeY - eyeY;
                    double dz = planeZ - eyeZ;
                    double distXZ = Math.sqrt(dx * dx + dz * dz);
                    float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
                    float targetPitch = (float) (-(Mth.atan2(dy, distXZ) * (180.0 / Math.PI)));

                    // カメラの描画角度のみを飛行機（Look-At）に向ける！
                    event.setYaw(targetYaw);
                    event.setPitch(targetPitch);
                } else if (carCameraMode == RcCameraMode.CHASE_CAR) {
                    // === 【モード2: チェイス視点（三人称後方追従）】 ===
                    // バニラの三人称後方カメラで超滑らかに完全追従（ジッター・カクつき防止）
                    event.setYaw(planeYaw);
                    event.setPitch(-planePitch * 0.35f + 12.0f);
                    event.setRoll(-planeRoll * 0.35f);
                } else if (carCameraMode == RcCameraMode.GUNNER_AIM) {
                    // === 【モード3: コックピット一人称FPV視点】 ===
                    event.setYaw(planeYaw);
                    event.setPitch(-planePitch);
                    event.setRoll(-planeRoll);
                }
            }
        }
    }

    @SubscribeEvent
    public void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!isPiloting) return;

        if (event.isUseItem()) {
            event.setCanceled(true);
            event.setSwingHand(false);

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                stopPiloting();
                return;
            }
            return;
        }

        if (event.isAttack()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isPiloting) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isPiloting) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isPiloting) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isPiloting) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (isPiloting) {
            event.setCanceled(true);
        }
    }

    private static java.lang.reflect.Method cameraMoveMethod = null;
    private static boolean cameraMoveResolved = false;

    private static void moveCameraSafe(net.minecraft.client.Camera camera, double distanceOffset, double yOffset, double xOffset) {
        if (!cameraMoveResolved) {
            try {
                for (java.lang.reflect.Method m : net.minecraft.client.Camera.class.getDeclaredMethods()) {
                    if (m.getParameterCount() == 3 &&
                            m.getParameterTypes()[0] == double.class &&
                            m.getParameterTypes()[1] == double.class &&
                            m.getParameterTypes()[2] == double.class &&
                            (m.getName().equals("move") || m.getName().contains("move") || m.getName().equals("m_90584_"))) {
                        cameraMoveMethod = m;
                        cameraMoveMethod.setAccessible(true);
                        MicroDroneMod.LOGGER.info("[CAMERA_FIX] Resolved Camera move method: {}", m.getName());
                        break;
                    }
                }
            } catch (Exception e) {
                MicroDroneMod.LOGGER.error("[CAMERA_FIX] Failed to reflect Camera move method", e);
            }
            cameraMoveResolved = true;
        }

        if (cameraMoveMethod != null) {
            try {
                cameraMoveMethod.invoke(camera, distanceOffset, yOffset, xOffset);
            } catch (Exception ignored) {}
        }
    }
}
