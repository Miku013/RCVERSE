package com.microdrone.mod.client.input;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.client.camera.DroneCameraController;
import com.microdrone.mod.entity.DroneEntity;
import com.microdrone.mod.item.DroneControllerItem;
import com.microdrone.mod.network.DroneControlPayload;
import net.minecraft.client.Minecraft;
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
            if (mc.player != null) {
                lastPlayerYaw = mc.player.getYRot();
                mc.player.setInvisible(true); // 操縦中のプレイヤーモデル不可視化
            }

            // クライアント側ドローンにローカル制御フラグを設定（カクつき防止・予測物理用）
            if (mc.level != null) {
                Entity entity = mc.level.getEntity(droneId);
                if (entity instanceof DroneEntity drone) {
                    drone.setLocallyControlled(true);
                }
            }

            DroneCameraController.startViewing(droneId);
        } else {
            if (mc.player != null) {
                mc.player.setInvisible(false); // 操縦終了時に透明化解除
            }

            if (mc.level != null && activeDroneId != -1) {

                Entity entity = mc.level.getEntity(activeDroneId);
                if (entity instanceof DroneEntity drone) {
                    drone.setLocallyControlled(false);
                }
            }

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
        if (!(entity instanceof DroneEntity drone) || drone.isRemoved()) {
            missingTicks++;
            // 一時的なパケット遅延や遠距離チャンク移行での即座切断を防止（約2秒間猶予）
            if (missingTicks > 40) {
                stopPiloting();
            }
            return;
        }

        // パターンA: クライアント側ローカルプレイヤーの座標をドローンに同期
        player.setPos(drone.getX(), drone.getY(), drone.getZ());

        // プレイヤーのマウス最新角度を取得してドローン制御とサーバー同期を行う
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
}
