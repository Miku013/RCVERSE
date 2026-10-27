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

        // パターンA: クライアント側ローカルプレイヤーの水平チャンク座標をドローンに同期
        // （足元地下にオフセット配置することで、ドアやブロックのクリック時に自プレイヤーの当たり判定に衝突して誤攻撃キックされるのを完全防止）
        player.setPos(drone.getX(), drone.getY() - 100.0, drone.getZ());

        // カメラエンティティ（drone）はバニラの MouseHandler により毎フレーム滑らかに回転しているため、
        // その最新角度を取得してサーバー送信・プレイヤー同期を行う（20Hzでの上書きによるカクつきを完全防止）
        float currentYaw = drone.getYRot();
        float currentPitch = drone.getXRot();

        player.setYRot(currentYaw);
        player.setXRot(currentPitch);

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

    /**
     * 操縦中、自プレイヤーや操縦中ドローンへの攻撃・インタラクトをクライアント側で完全遮断
     * （バニラサーバーによる「無効なエンティティを攻撃しようとしました」切断キックを未然に防止）
     */
    @SubscribeEvent
    public void onAttackEntity(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        if (!isPiloting) return;
        Minecraft mc = Minecraft.getInstance();
        if (event.getTarget() == mc.player || (event.getTarget() instanceof DroneEntity drone && drone.getId() == activeDroneId)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (!isPiloting) return;
        Minecraft mc = Minecraft.getInstance();
        if (event.getTarget() == mc.player || (event.getTarget() instanceof DroneEntity drone && drone.getId() == activeDroneId)) {
            event.setCanceled(true);
        }
    }
}
