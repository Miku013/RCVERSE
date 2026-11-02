package com.microdrone.mod.client.input;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.client.controller.DroneClientController;
import com.microdrone.mod.client.controller.InputSnapshot;
import com.microdrone.mod.client.controller.RcCarClientController;
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
            isPiloting = false;
            activeDroneId = -1;
            capturedForward = 0.0f;
            capturedStrafe = 0.0f;
            capturedVertical = 0.0f;
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

        Entity entity = mc.level.getEntity(activeDroneId);
        if (entity == null || entity.isRemoved() || !(entity instanceof RcControllable)) {
            missingTicks++;
            if (missingTicks > 40) {
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
        if (isPiloting) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isPiloting) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isPiloting) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isPiloting) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
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
}
