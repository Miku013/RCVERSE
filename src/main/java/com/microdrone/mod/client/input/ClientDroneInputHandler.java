package com.microdrone.mod.client.input;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.client.ReplayHelper;
import com.microdrone.mod.client.controller.DroneClientController;
import com.microdrone.mod.client.controller.InputSnapshot;
import com.microdrone.mod.client.controller.RcBoatClientController;
import com.microdrone.mod.client.controller.RcCarClientController;
import com.microdrone.mod.client.controller.RcSubmarineClientController;
import com.microdrone.mod.entity.RcBoatEntity;
import com.microdrone.mod.entity.RcSubmarineEntity;
import com.microdrone.mod.client.controller.RcPlaneClientController;
import com.microdrone.mod.client.controller.RcTankClientController;
import com.microdrone.mod.client.controller.RcVehicleClientController;
import com.microdrone.mod.client.controller.RcViewMode;
import com.microdrone.mod.entity.DroneEntity;
import com.microdrone.mod.entity.RcCarEntity;
import com.microdrone.mod.entity.RcControllable;
import com.microdrone.mod.entity.RcPlaneEntity;
import com.microdrone.mod.entity.RcTankEntity;
import com.microdrone.mod.item.DroneControllerItem;
import com.microdrone.mod.network.DroneControlPayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * すべてのRC機体（ドローン、カー、戦車、プレーン等）の共通セッション管理・イベント購読・入力インターセプトを担当する基盤マネージャー
 */
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

    private static float capturedForward = 0.0f;
    private static float capturedStrafe = 0.0f;
    private static float capturedVertical = 0.0f;

    // 各機体専用コントローラーのインスタンス
    private static final DroneClientController DRONE_CONTROLLER = new DroneClientController();
    private static final RcCarClientController CAR_CONTROLLER = new RcCarClientController();
    private static final RcTankClientController TANK_CONTROLLER = new RcTankClientController();
    private static final RcPlaneClientController PLANE_CONTROLLER = new RcPlaneClientController();
    private static final RcBoatClientController BOAT_CONTROLLER = new RcBoatClientController();
    private static final RcSubmarineClientController SUB_CONTROLLER = new RcSubmarineClientController();

    // 既存コード互換用の列挙型
    public enum DroneCameraMode {
        FPV, CHASE, GROUND
    }
    public enum RcCameraMode {
        GROUND_RC, CHASE_CAR, GUNNER_AIM
    }

    public static RcVehicleClientController getCurrentController() {
        if (!isPiloting || activeDroneId == -1) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity entity = mc.level.getEntity(activeDroneId);
        return getControllerForEntity(entity);
    }

    public static RcVehicleClientController getControllerForEntity(Entity entity) {
        if (entity instanceof DroneEntity) return DRONE_CONTROLLER;
        if (entity instanceof RcCarEntity) return CAR_CONTROLLER;
        if (entity instanceof RcTankEntity) return TANK_CONTROLLER;
        if (entity instanceof RcPlaneEntity) return PLANE_CONTROLLER;
        if (entity instanceof RcBoatEntity) return BOAT_CONTROLLER;
        if (entity instanceof RcSubmarineEntity) return SUB_CONTROLLER;
        return null;
    }

    public static RcViewMode getCurrentViewMode() {
        RcVehicleClientController controller = getCurrentController();
        return controller != null ? controller.getViewMode() : RcViewMode.GROUND;
    }

    // 既存コード互換メソッド
    public static DroneCameraMode getDroneCameraMode() {
        RcViewMode mode = DRONE_CONTROLLER.getViewMode();
        return switch (mode) {
            case CHASE -> DroneCameraMode.CHASE;
            case GROUND -> DroneCameraMode.GROUND;
            default -> DroneCameraMode.FPV;
        };
    }

    public static RcCameraMode getCarCameraMode() {
        RcVehicleClientController controller = getCurrentController();
        RcViewMode mode = controller != null ? controller.getViewMode() : RcViewMode.GROUND;
        return switch (mode) {
            case CHASE -> RcCameraMode.CHASE_CAR;
            case FIRST_PERSON -> RcCameraMode.GUNNER_AIM;
            default -> RcCameraMode.GROUND_RC;
        };
    }

    public static boolean isDronePiloting() {
        if (!isPiloting || activeDroneId == -1) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getEntity(activeDroneId) instanceof DroneEntity;
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
        return mc.level != null && mc.level.getEntity(activeDroneId) instanceof RcCarEntity;
    }

    public static boolean isRcTankPiloting() {
        if (!isPiloting || activeDroneId == -1) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getEntity(activeDroneId) instanceof RcTankEntity;
    }

    public static boolean isRcPlanePiloting() {
        if (!isPiloting || activeDroneId == -1) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getEntity(activeDroneId) instanceof RcPlaneEntity;
    }

    public static void register() {
        NeoForge.EVENT_BUS.register(new ClientDroneInputHandler());
    }

    public static boolean isPiloting() {
        return isPiloting && !ReplayHelper.isReplayActive();
    }

    public static int getActiveDroneId() {
        return activeDroneId;
    }

    public static void setPiloting(int droneId, boolean state) {
        setPiloting(droneId, state, 0, 0, 0);
    }

    public static void setPiloting(int droneId, boolean state, double sx, double sy, double sz) {
        if (state && ReplayHelper.isReplayActive()) {
            MicroDroneMod.LOGGER.info("[CLIENT] [REPLAY] setPiloting ignored because Replay is active. DroneId: {}", droneId);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        missingTicks = 0;
        MicroDroneMod.LOGGER.info("[CLIENT] [PILOT_STATE_SET] DroneId: {}, State: {}, PlayerPos: {}",
                droneId, state,
                mc.player != null ? String.format("[%.2f, %.2f, %.2f]", mc.player.getX(), mc.player.getY(), mc.player.getZ()) : "null");

        if (state) {
            boolean hasValidStartPos = !Double.isNaN(sx) && !(Math.abs(sx) < 0.001 && Math.abs(sy) < 0.001 && Math.abs(sz) < 0.001);
            if (hasValidStartPos) {
                startX = sx;
                startY = sy;
                startZ = sz;
            } else if (mc.player != null) {
                startX = mc.player.getX();
                startY = mc.player.getY();
                startZ = mc.player.getZ();
            }
            isPiloting = true;
            activeDroneId = droneId;
            Entity targetEnt = mc.level != null ? mc.level.getEntity(droneId) : null;
            RcVehicleClientController controller = getControllerForEntity(targetEnt);
            if (controller != null) {
                controller.onStart(targetEnt, mc.player, startX, startY, startZ);
            }
        } else {
            Entity entity = (mc.level != null && activeDroneId != -1) ? mc.level.getEntity(activeDroneId) : null;
            RcVehicleClientController controller = getControllerForEntity(entity);
            if (controller != null) {
                controller.onStop(entity, mc.player);
            }
            // 確実なフェイルセーフ: コントローラー取得可否やエンティティ消滅に関わらず、
            // 操縦終了時は必ずカメラ・可視性・視点を100%自プレイヤーに完全復元する！
            if (mc.player != null) {
                mc.player.setInvisible(false);
                if (mc.getCameraEntity() != mc.player) {
                    mc.setCameraEntity(mc.player);
                }
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            }
            isPiloting = false;
            activeDroneId = -1;
            capturedForward = 0.0f;
            capturedStrafe = 0.0f;
            capturedVertical = 0.0f;
            missingTicks = 0;
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

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && activeDroneId != -1) {
            Entity entity = mc.level.getEntity(activeDroneId);
            // 機体が消滅・削除済みの場合は入力を抑制せずプレイヤーに即時操作権を解放
            if (entity == null || entity.isRemoved()) {
                return;
            }
        }

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
    private static long lastClientTickTime = 0;
    private static long lastWarnTime = 0;

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Pre event) {
        if (ReplayHelper.isReplayActive()) return;

        long now = System.currentTimeMillis();
        // 通常の20TPS動作 (50ms) で毎ティック警告が出ないよう、150ms以上の深刻なフレームスタッター時かつ3秒に1回にスロットリング
        if (isPiloting && lastClientTickTime > 0) {
            long delta = now - lastClientTickTime;
            if (delta > 150 && (now - lastWarnTime > 3000)) {
                MicroDroneMod.LOGGER.warn("[CLIENT_FRAME_TIME] Severe tick interval spike: {}ms (>150ms / <7fps) during RC piloting", delta);
                lastWarnTime = now;
            }
        }
        lastClientTickTime = now;

        if (!isPiloting || activeDroneId == -1) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            setPiloting(-1, false);
            return;
        }

        Entity entity = mc.level.getEntity(activeDroneId);
        if (entity == null || entity.isRemoved() || !(entity instanceof RcControllable)) {
            // エンティティが消滅・削除された場合は2秒待機せず、即座に操縦終了してプレイヤー視点へ復帰！
            if (entity != null && entity.isRemoved()) {
                stopPiloting();
                return;
            }
            missingTicks++;
            if (missingTicks > 5) {
                stopPiloting();
            }
            return;
        }
        missingTicks = 0;

        RcVehicleClientController controller = getControllerForEntity(entity);
        if (controller == null) return;

        // F5キーのインターセプト
        while (mc.options.keyTogglePerspective.consumeClick()) {
            controller.toggleViewMode(entity, player);
        }

        CameraType expectedCameraType = controller.getExpectedCameraType();
        if (mc.options.getCameraType() != expectedCameraType) {
            mc.options.setCameraType(expectedCameraType);
        }

        InputSnapshot snapshot = new InputSnapshot(capturedForward, capturedStrafe, capturedVertical);
        controller.handleTickInput(player, entity, snapshot, startX, startY, startZ);
    }

    @SubscribeEvent
    public void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!isPiloting || activeDroneId == -1) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            Entity entity = mc.level.getEntity(activeDroneId);
            if (entity == null || entity.isRemoved()) {
                // 機体消滅・削除時はカメラアングル操作を即時停止（画面ガタつき防止）
                return;
            }
            RcVehicleClientController controller = getControllerForEntity(entity);
            if (controller != null) {
                controller.computeCameraAngles(event, entity, startX, startY, startZ);
            }
        }
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        if (!isPiloting || activeDroneId == -1) return;
        if (event.getAction() != org.lwjgl.glfw.GLFW.GLFW_PRESS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Entity entity = mc.level.getEntity(activeDroneId);
        RcVehicleClientController controller = getControllerForEntity(entity);
        if (controller != null) {
            controller.onKeyInput(event.getKey(), event.getAction(), entity, mc.player);
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
                boolean holdingController = mc.player.getMainHandItem().getItem() instanceof DroneControllerItem
                        || mc.player.getOffhandItem().getItem() instanceof DroneControllerItem;
                if (holdingController) {
                    stopPiloting();
                }
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
        if (!event.getLevel().isClientSide()) return;
        if (event.getEntity() != Minecraft.getInstance().player) return;
        if (isPiloting) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide()) return;
        if (event.getEntity() != Minecraft.getInstance().player) return;
        if (isPiloting) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide()) return;
        if (event.getEntity() != Minecraft.getInstance().player) return;
        if (isPiloting) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!event.getLevel().isClientSide()) return;
        if (event.getEntity() != Minecraft.getInstance().player) return;
        if (isPiloting) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (!event.getEntity().level().isClientSide()) return;
        if (event.getEntity() != Minecraft.getInstance().player) return;
        if (isPiloting) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (isPiloting) {
            RcVehicleClientController controller = getCurrentController();
            if (controller != null && controller.getViewMode() == RcViewMode.GROUND) {
                if (event.getEntity() instanceof ArmorStand stand) {
                    double dx = stand.getX() - startX;
                    double dy = stand.getY() - startY;
                    double dz = stand.getZ() - startZ;
                    if (dx * dx + dy * dy + dz * dz < 4.0) {
                        event.setCanceled(true);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!isPiloting || activeDroneId == -1) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        Entity entity = mc.level.getEntity(activeDroneId);
        RcVehicleClientController controller = getControllerForEntity(entity);
        if (controller != null) {
            boolean isShiftDown = net.minecraft.client.gui.screens.Screen.hasShiftDown() || mc.options.keyShift.isDown();
            boolean handled = controller.onMouseScroll(event.getScrollDeltaY(), isShiftDown, entity, player);
            if (handled) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!isPiloting || activeDroneId == -1) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        Entity entity = mc.level.getEntity(activeDroneId);
        RcVehicleClientController controller = getControllerForEntity(entity);
        if (controller != null) {
            boolean handled = controller.onMouseButton(event.getButton(), event.getAction(), event.getModifiers(), entity, player);
            if (handled) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!isPiloting || activeDroneId == -1) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            Entity entity = mc.level.getEntity(activeDroneId);
            RcVehicleClientController controller = getControllerForEntity(entity);
            if (controller != null) {
                controller.computeFov(event, entity);
            }
        }
    }
}
