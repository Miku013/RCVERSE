package com.microdrone.mod.client.input;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.client.camera.DroneCameraController;
import com.microdrone.mod.entity.DroneEntity;
import com.microdrone.mod.entity.RcCarEntity;
import com.microdrone.mod.entity.RcControllable;
import com.microdrone.mod.item.DroneControllerItem;
import com.microdrone.mod.network.DroneControlPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.client.player.LocalPlayer;
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
        GROUND_RC,   // 地上プロポ視点 (本物のラジコン感覚: 立ち位置から車を見下ろし視線追従)
        CHASE_CAR    // 車載チェイス視点 (車体斜め後方上空の見下ろしカメラ)
    }

    private static RcCameraMode carCameraMode = RcCameraMode.GROUND_RC;

    public static RcCameraMode getCarCameraMode() {
        return carCameraMode;
    }

    public static boolean isRcCarPiloting() {
        if (!isPiloting || activeDroneId == -1) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        return mc.level.getEntity(activeDroneId) instanceof RcCarEntity;
    }

    public static void toggleCarCameraMode() {
        Minecraft mc = Minecraft.getInstance();
        if (carCameraMode == RcCameraMode.GROUND_RC) {
            carCameraMode = RcCameraMode.CHASE_CAR;
            if (mc.player != null) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.microdrone.cam_chase"), true);
                mc.player.setInvisible(true);
            }
            if (mc.level != null && activeDroneId != -1) {
                Entity car = mc.level.getEntity(activeDroneId);
                if (car != null) {
                    mc.setCameraEntity(car);
                }
            }
        } else {
            carCameraMode = RcCameraMode.GROUND_RC;
            if (mc.player != null) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.microdrone.cam_ground"), true);
                mc.player.setInvisible(false);
                mc.setCameraEntity(mc.player);
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
            carCameraMode = RcCameraMode.GROUND_RC; // RCカーは「地上プロポ視点」で操縦開始！

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
                if (mc.player != null) {
                    lastPlayerYaw = mc.player.getYRot();
                    mc.player.setInvisible(false); // 地上プロポ視点: 手元にプロポが見える！
                    mc.setCameraEntity(mc.player); // カメラはプレイヤー自身の頭！
                }
                car.setLocallyControlled(true);
            }
        } else {
            com.microdrone.mod.client.audio.DroneSoundManager.stopPilotingSound();

            if (mc.player != null) {
                mc.player.setInvisible(false); // 操縦終了時に透明化解除
                mc.setCameraEntity(mc.player); // Restore camera to player
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); // Restore first person
            }

            if (mc.level != null && activeDroneId != -1) {
                Entity entity = mc.level.getEntity(activeDroneId);
                if (entity instanceof DroneEntity drone) {
                    drone.setLocallyControlled(false);
                } else if (entity instanceof RcCarEntity car) {
                    car.setLocallyControlled(false);
                }
            }

            carCameraMode = RcCameraMode.GROUND_RC; // Reset camera mode to ground
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

        // ドローン用の入力を取得 (W/S, A/D, Space, Shift)
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

        // プレイヤー自身の入力を完全に抑制（プレイヤーはその場に静止）
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

        Entity entity = mc.level.getEntity(activeDroneId);
        if (entity == null || entity.isRemoved() || !(entity instanceof RcControllable)) {
            missingTicks++;
            // 一時的なパケット遅延や遠距離チャンク移行での即座切断を防止（約2秒間猶予）
            if (missingTicks > 40) {
                stopPiloting();
            }
            return;
        }

        missingTicks = 0;

        if (entity instanceof DroneEntity drone) {
            // ドローン操縦時はプレイヤーの座標をドローンに同期
            player.setPos(drone.getX(), drone.getY(), drone.getZ());

            float currentYaw = player.getYRot();
            float currentPitch = player.getXRot();
            drone.applyControlInput(capturedForward, capturedStrafe, capturedVertical, currentYaw, currentPitch);

            // サーバーへ最新の姿勢と移動入力を送信
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
            // RCカー操縦時: プレイヤーは立ち位置(startX, startY, startZ)にとどまる！
            // Client-side battery empty failsafe
            if (!car.hasBattery() || car.getBatteryRemaining() <= 0) {
                stopPiloting();
                return;
            }
            player.setPos(startX, startY, startZ);

            car.inputForward = capturedForward;
            car.inputSteer = capturedStrafe;
            car.inputHandbrake = (capturedVertical > 0.5f); // スペースキーでハンドブレーキ

            // サーバーへ車の操作入力（W/S: 前後, A/D: ステアリング, Space: ハンドブレーキ）を送信
            PacketDistributor.sendToServer(new DroneControlPayload(
                    activeDroneId,
                    capturedForward,
                    capturedStrafe,
                    capturedVertical,
                    car.getYRot(),
                    car.getXRot(),
                    false
            ));
        }
    }

    /**
     * 【144Hz+ 超滑らか視線追従】
     * 描画フレームごとにカメラ角度を計算するイベント。
     * マウス操作によってリアルタイムに変化するプレイヤーの視線を即座にカメラ角度にダイレクト適用！
     * これにより 20Hz Tick の遅延や補間の引っ掛かりがゼロになり、完璧にヌルヌルの視線移動を実現。
     */
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

                // ドローン自身の姿勢もフレーム単位で同期（外見の向きの完全一致）
                drone.setYRot(yaw);
                drone.setYHeadRot(yaw);
                drone.setXRot(pitch);
            } else if (entity instanceof RcCarEntity car) {
                float partialTick = (float) event.getPartialTick();
                float carYaw = Mth.rotLerp(partialTick, car.yRotO, car.getYRot());

                // 車体のフレーム補間位置
                double carX = Mth.lerp(partialTick, car.xOld, car.getX());
                double carY = Mth.lerp(partialTick, car.yOld, car.getY());
                double carZ = Mth.lerp(partialTick, car.zOld, car.getZ());

                if (carCameraMode == RcCameraMode.GROUND_RC) {
                    // === 【モード1: 地上プロポ視点（本物のラジコン感覚）】 ===
                    // カメラはプレイヤー自身の頭！足元〜遠くを走る車を見下ろして首を振って追従
                    double eyeX = mc.player.getX();
                    double eyeY = mc.player.getEyeY();
                    double eyeZ = mc.player.getZ();

                    double dx = carX - eyeX;
                    double dy = carY - eyeY;
                    double dz = carZ - eyeZ;
                    double distXZ = Math.sqrt(dx * dx + dz * dz);

                    // 走るRCカーを自然に追尾するターゲット角度 (Look-At)
                    float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
                    float targetPitch = (float) (-(Mth.atan2(dy, distXZ) * (180.0 / Math.PI)));

                    event.setYaw(targetYaw);
                    event.setPitch(targetPitch);

                    mc.player.setYRot(targetYaw);
                    mc.player.setXRot(targetPitch);
                } else {
                    // === 【モード2: 車載チェイス視点（見下ろし追従カメラ）】 ===
                    double dist = 1.6;
                    double height = 0.9;
                    float yawRad = carYaw * Mth.DEG_TO_RAD;

                    double targetCamX = carX + Mth.sin(yawRad) * dist;
                    double targetCamY = carY + height;
                    double targetCamZ = carZ - Mth.cos(yawRad) * dist;

                    // 壁抜け防止
                    net.minecraft.world.phys.Vec3 carCenter = new net.minecraft.world.phys.Vec3(carX, carY + 0.2, carZ);
                    net.minecraft.world.phys.Vec3 camPos = new net.minecraft.world.phys.Vec3(targetCamX, targetCamY, targetCamZ);
                    net.minecraft.world.phys.BlockHitResult hit = mc.level.clip(new net.minecraft.world.level.ClipContext(
                            carCenter, camPos,
                            net.minecraft.world.level.ClipContext.Block.VISUAL,
                            net.minecraft.world.level.ClipContext.Fluid.NONE,
                            car
                    ));
                    if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                        net.minecraft.world.phys.Vec3 hitPos = hit.getLocation();
                        targetCamX = Mth.lerp(0.85, hitPos.x, carCenter.x);
                        targetCamY = Mth.lerp(0.85, hitPos.y, carCenter.y);
                        targetCamZ = Mth.lerp(0.85, hitPos.z, carCenter.z);
                    }

                    setCameraPositionSafe(event.getCamera(), targetCamX, targetCamY, targetCamZ);

                    float mousePitch = mc.player.getXRot();
                    float pitch = Mth.clamp(22.0f + mousePitch * 0.25f, 10.0f, 60.0f);

                    event.setYaw(carYaw);
                    event.setPitch(pitch);
                    mc.player.setYRot(carYaw);
                }
            }
        }
    }

    /**
     * 【右クリックでの安全な操縦終了 ＆ ブロック/エンティティ干渉の完全防止】
     * ドローン操縦中に入力キーをインターセプト：
     * - 右クリック（使用キー）:
     *   - 手にドローンコントローラーを持っている場合のみ、安全に操縦を終了！
     *   - それ以外のアイテム所持時は誤終了を防止し、かつブロック設置や飲食などの誤動作を100%遮断。
     * - 左クリック（攻撃キー）: ワールド破壊や誤攻撃を完全ブロック。
     */
    @SubscribeEvent
    public void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!isPiloting) return;

        if (event.isUseItem()) {
            // ワールドのブロック設置やアイテム使用は常に完全無効化
            event.setCanceled(true);
            event.setSwingHand(false);

            // 手にドローンコントローラーを持っている時のみ操縦を終了
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

    /**
     * 操縦中、すべてのブロックに対する右クリック（ドア開閉、チェスト、ブロック設置等）を完全無効化
     */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isPiloting) {
            event.setCanceled(true);
        }
    }

    /**
     * 操縦中、ブロックの左クリック破壊を完全無効化
     */
    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isPiloting) {
            event.setCanceled(true);
        }
    }

    /**
     * 操縦中、身代わりダミー人形や他エンティティへの右クリック・インタラクトを完全無効化
     */
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

    /**
     * 操縦中、自プレイヤーや操縦中ドローン、ダミー等への攻撃パケット送信を完全遮断
     */
    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (isPiloting) {
            event.setCanceled(true);
        }
    }

    private static java.lang.reflect.Method cameraSetPositionMethod = null;
    private static boolean cameraMethodResolved = false;

    private static void setCameraPositionSafe(net.minecraft.client.Camera camera, double x, double y, double z) {
        if (!cameraMethodResolved) {
            try {
                for (java.lang.reflect.Method m : net.minecraft.client.Camera.class.getDeclaredMethods()) {
                    if (m.getName().equals("setPosition") && m.getParameterCount() == 3) {
                        cameraSetPositionMethod = m;
                        cameraSetPositionMethod.setAccessible(true);
                        break;
                    }
                }
            } catch (Exception e) {
                MicroDroneMod.LOGGER.error("Failed to reflect Camera.setPosition", e);
            }
            cameraMethodResolved = true;
        }

        if (cameraSetPositionMethod != null) {
            try {
                cameraSetPositionMethod.invoke(camera, x, y, z);
            } catch (Exception ignored) {}
        }
    }

    /**
     * 【F5キー（視点切替キー）でのカメラモード切替】
     * 地上プロポ視点（本物RC） と 車載チェイス視点 をワンタッチで切替！
     */
    @SubscribeEvent
    public void onKeyInput(net.neoforged.neoforge.client.event.InputEvent.Key event) {
        if (!isPiloting || activeDroneId == -1) return;

        Minecraft mc = Minecraft.getInstance();
        if (isRcCarPiloting()) {
            if (mc.options.keyTogglePerspective.matches(event.getKey(), event.getScanCode()) && event.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                toggleCarCameraMode();
            }
        }
    }
}
