package com.microdrone.mod.client.input;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.client.camera.DroneCameraController;
import com.microdrone.mod.entity.DroneEntity;
import com.microdrone.mod.network.DroneControlPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

public class ClientDroneInputHandler {

    private static boolean isPiloting = false;
    private static int activeDroneId = -1;

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

        // ドローン正常検知時はカウンタリセット
        missingTicks = 0;

        // パターンA: クライアント側ローカルプレイヤーの座標をドローンに同期
        // （これによりバニラのサーバー・クライアント間の12チャンク同期矛盾・パケット逆流による表示消滅を完全防止）
        player.setPos(drone.getX(), drone.getY(), drone.getZ());

        // マウス操作による視線向き（Yaw / Pitch）をドローンの機首とカメラにダイレクト同期
        float currentYaw = player.getYRot();
        float currentPitch = player.getXRot();

        // クライアント側のドローンエンティティにも即座に入力を適用！
        // これによりクライアントのtickでローカル物理シミュレーションが即座に実行され、
        // サーバーからの遅延同期パケットによるカクつき（ジッター）が完全に解消される。
        drone.setYRot(currentYaw);
        drone.setYHeadRot(currentYaw);
        drone.setXRot(currentPitch);
        drone.applyControlInput(capturedForward, capturedStrafe, capturedVertical, currentYaw, currentPitch);

        // サーバーへ制御パケットを送信
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
}
