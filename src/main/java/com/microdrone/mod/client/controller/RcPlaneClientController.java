package com.microdrone.mod.client.controller;

import com.microdrone.mod.entity.RcPlaneEntity;
import com.microdrone.mod.network.DroneControlPayload;
import com.microdrone.mod.network.PlaneModeTogglePayload;
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

/**
 * マイクロRCプレーン（固定翼セスナ機）専用クライアントコントローラー
 */
public class RcPlaneClientController implements RcVehicleClientController {

    private RcViewMode viewMode = RcViewMode.GROUND;

    @Override
    public void onStart(Entity vehicle, LocalPlayer player, double startX, double startY, double startZ) {
        viewMode = RcViewMode.GROUND;
        if (vehicle instanceof RcPlaneEntity plane) {
            plane.setLocallyControlled(true);
        }
        if (player != null) {
            player.setInvisible(false);
            Minecraft.getInstance().setCameraEntity(player);
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
            player.displayClientMessage(Component.translatable("message.microdrone.plane_cam_ground"), true);
        }
    }

    @Override
    public void onStop(Entity vehicle, LocalPlayer player) {
        if (vehicle instanceof RcPlaneEntity plane) {
            plane.setLocallyControlled(false);
        }
        if (player != null) {
            player.setInvisible(false);
            Minecraft.getInstance().setCameraEntity(player);
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    @Override
    public void onKeyInput(int key, int action, Entity vehicle, LocalPlayer player) {
        if (key == GLFW.GLFW_KEY_R && vehicle instanceof RcPlaneEntity plane) {
            int currentMode = plane.getFlightMode();
            int newMode = (currentMode == 0) ? 1 : 0;
            plane.setFlightMode(newMode);

            // サーバーへ通知
            PacketDistributor.sendToServer(new PlaneModeTogglePayload(plane.getId(), newMode));

            // アクションバー表示＆効果音
            if (player != null) {
                if (newMode == 0) {
                    player.displayClientMessage(Component.translatable("message.microdrone.flight_mode_assist"), true);
                    player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
                } else {
                    player.displayClientMessage(Component.translatable("message.microdrone.flight_mode_acro"), true);
                    player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 0.8f);
                }
            }
        }
    }

    @Override
    public void handleTickInput(LocalPlayer player, Entity vehicle, InputSnapshot input, double startX, double startY, double startZ) {
        if (!(vehicle instanceof RcPlaneEntity plane)) return;

        player.setPos(startX, startY, startZ);
        player.setDeltaMovement(0, 0, 0); // プレイヤーの微小落下による画面テレポートジッターを完全防止
        player.fallDistance = 0.0f;

        // クライアント側へ即座に反映（ノーディレイ操作感）
        // W: ピッチダウン(-1.0: 機首下げ), S: ピッチアップ(+1.0: 機首引き上げ・上昇離陸)
        plane.inputElevator = -input.forward();
        // A: 左ロール/左傾斜(-1.0), D: 右ロール/右傾斜(+1.0)
        plane.inputAileron = -input.strafe();
        // Space: スロットル増(+1.0), Shift: スロットル減(-1.0)
        plane.inputThrottleDelta = input.vertical();

        // ラダー（方向舵）および地上ステアリングの計算
        // Aキー(左)で負(-1.0)、Dキー(右)で正(+1.0)
        float rudder;

        if (plane.onGround()) {
            // === 【地上タキシング＆滑走時】 ===
            // A/D キーで直感的に尾輪ステアリング（Aで左旋回、Dで右旋回）
            // キーを離せば瞬時に 0（直進ニュートラル）に戻り、まっすぐ滑走！
            rudder = -input.strafe();
        } else {
            // === 【空中飛行時】 ===
            if (viewMode == RcViewMode.GROUND) {
                // 地上プロポ視点: 旋回は A/D キーの操作を100%忠実に反映！
                rudder = -input.strafe() * 0.7f;
            } else {
                // チェイス視点 または コックピット視点:
                float keyRudder = -input.strafe() * 0.7f;

                // マウスの向きによる穏やかなアシスト旋回 (デッドゾーン±8.0度 ＆ マイルド感度)
                float mouseRudder = 0.0f;
                float yawDiff = Mth.wrapDegrees(player.getYRot() - plane.getYRot());

                // デッドゾーン (±8.0度以内は完全直進維持)
                if (Math.abs(yawDiff) > 8.0f) {
                    float effectiveDiff = yawDiff - Math.signum(yawDiff) * 8.0f;
                    mouseRudder = Mth.clamp(effectiveDiff * 0.003f, -0.20f, 0.20f);
                }

                // プレイヤーの視線を機体進行方向へ穏やかに緩慢追従（ズレっぱなし防止）
                player.setYRot(Mth.rotLerp(0.08f, player.getYRot(), plane.getYRot()));

                if (Math.abs(input.strafe()) > 0.05f) {
                    rudder = keyRudder;
                } else {
                    rudder = mouseRudder;
                }
            }
        }
        plane.inputRudder = rudder;

        // サーバーへ送信 (yaw に計算済み rudder を格納して同期)
        PacketDistributor.sendToServer(new DroneControlPayload(
                plane.getId(),
                input.forward(),
                input.strafe(),
                input.vertical(),
                rudder,
                plane.getPitchAngle(),
                false
        ));
    }

    @Override
    public void computeCameraAngles(ViewportEvent.ComputeCameraAngles event, Entity vehicle, double startX, double startY, double startZ) {
        if (!(vehicle instanceof RcPlaneEntity plane)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float partialTick = (float) event.getPartialTick();
        float planeYaw = Mth.rotLerp(partialTick, plane.yRotO, plane.getYRot());
        float planePitch = Mth.rotLerp(partialTick, plane.prevPitchAngle, plane.getPitchAngle());
        float planeRoll = Mth.rotLerp(partialTick, plane.prevRollAngle, plane.getRollAngle());

        if (viewMode == RcViewMode.GROUND) {
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

            event.setYaw(targetYaw);
            event.setPitch(targetPitch);
        } else if (viewMode == RcViewMode.CHASE) {
            // === 【モード2: チェイス視点（見下ろし固定・一定距離追従）】 ===
            // カメラロールを0度に固定して世界の逆回転・傾きを完全防止し、
            // 常に機体の後方斜め上から一定の見下ろし角度(15°)と一定距離で追従
            event.setYaw(planeYaw);
            event.setPitch(15.0f);
            event.setRoll(0.0f);
        } else {
            // === 【モード3: コックピット一人称FPV視点】 ===
            // 機体自身のロール・ピッチ・ヨーと完全同期（右ロールで地平線が正しく右上がりに傾く）
            event.setYaw(planeYaw);
            event.setPitch(-planePitch);
            event.setRoll(planeRoll);
        }
    }

    @Override
    public void toggleViewMode(Entity vehicle, LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (!(vehicle instanceof RcPlaneEntity plane)) return;

        if (viewMode == RcViewMode.GROUND) {
            // 地上プロポ視点 -> チェイス視点（三人称後方追従）
            viewMode = RcViewMode.CHASE;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.microdrone.plane_cam_chase"), true);
                player.setInvisible(true);
            }
            mc.setCameraEntity(plane);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (viewMode == RcViewMode.CHASE) {
            // チェイス視点 -> コックピット一人称FPV視点
            viewMode = RcViewMode.FIRST_PERSON;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.microdrone.plane_cam_cockpit"), true);
                player.setInvisible(true);
            }
            mc.setCameraEntity(plane);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        } else {
            // コックピット視点 -> 地上プロポ視点（地上で見上げるRC飛行場感覚）
            viewMode = RcViewMode.GROUND;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.microdrone.plane_cam_ground"), true);
                player.setInvisible(false);
            }
            mc.setCameraEntity(player);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
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
}
