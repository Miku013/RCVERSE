package com.microdrone.mod.client.gui;

import com.microdrone.mod.client.camera.DroneCameraController;
import com.microdrone.mod.client.input.ClientDroneInputHandler;
import com.microdrone.mod.entity.DroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * FPVマイクロドローン専用のフライト計器・HUDオーバーレイ
 */
public class DroneHudOverlay {

    public static void register() {
        NeoForge.EVENT_BUS.register(new DroneHudOverlay());
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        // ドローン操縦中かつカメラがドローン視点の時のみ描画
        if (!ClientDroneInputHandler.isPiloting() || !ClientDroneInputHandler.isDronePiloting()) {
            return;
        }
        if (ClientDroneInputHandler.getDroneCameraMode() == ClientDroneInputHandler.DroneCameraMode.GROUND) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Entity entity = mc.level.getEntity(ClientDroneInputHandler.getActiveDroneId());
        if (!(entity instanceof DroneEntity drone)) return;

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int cx = width / 2;
        int cy = height / 2;

        // 1. フライトクロスヘア（中央照準レティクル）
        renderCrosshair(graphics, cx, cy);

        // 2. 機首方位（HDG / コンパス） - 画面上部中央
        renderCompass(graphics, font, cx, 12, drone.getYRot());

        // 3. 画面下部計器の基準Y座標 (5行表示のため下端から約65px上)
        int bottomInstrumentsY = height - 65;

        // 4. 飛行計器（画面下部左側: 速度、高度、昇降、バッテリー、モーター）
        renderFlightInstruments(graphics, font, 14, bottomInstrumentsY, drone);

        // 5. 電波強度・離脱距離（画面下部右側: 距離、電波、アンテナバー、限界）
        renderSignalStatus(graphics, font, width - 104, bottomInstrumentsY, drone);

        // 6. 範囲 & バッテリー警告メッセージ（画面中央下）
        renderAlerts(graphics, font, cx, cy + 30, drone);

        // 7. 操作ガイド（画面下部中央）
        renderControlsGuide(graphics, font, cx, height - 14);
    }

    /**
     * 中央クロスヘアの描画（控えめな半透明シアン）
     */
    private void renderCrosshair(GuiGraphics graphics, int cx, int cy) {
        int color = 0x8055FFFF; // 半透明のサイアンブルー

        // 水平ガイドバー (隙間 8px, 長さ 10px)
        graphics.fill(cx - 15, cy, cx - 5, cy + 1, color);
        graphics.fill(cx + 6, cy, cx + 16, cy + 1, color);

        // 垂直ガイドバー (隙間 6px, 長さ 6px)
        graphics.fill(cx, cy - 10, cx + 1, cy - 4, color);
        graphics.fill(cx, cy + 5, cx + 1, cy + 11, color);

        // 中央微小ターゲットドット
        graphics.fill(cx, cy, cx + 1, cy + 1, 0xD0FFFFFF);

        // 四隅のFPVフレームブラケット
        int bColor = 0x35FFFFFF;
        // 左上
        graphics.fill(cx - 26, cy - 18, cx - 21, cy - 17, bColor);
        graphics.fill(cx - 26, cy - 18, cx - 25, cy - 13, bColor);
        // 右上
        graphics.fill(cx + 21, cy - 18, cx + 26, cy - 17, bColor);
        graphics.fill(cx + 25, cy - 18, cx + 26, cy - 13, bColor);
        // 左下
        graphics.fill(cx - 26, cy + 17, cx - 21, cy + 18, bColor);
        graphics.fill(cx - 26, cy + 13, cx - 25, cy + 18, bColor);
        // 右下
        graphics.fill(cx + 21, cy + 17, cx + 26, cy + 18, bColor);
        graphics.fill(cx + 25, cy + 13, cx + 26, cy + 18, bColor);
    }

    /**
     * 機首方位（HDG / コンパス）の描画（画面上部中央・日本語方位）
     */
    private void renderCompass(GuiGraphics graphics, Font font, int cx, int topY, float yaw) {
        float normalizedYaw = ((yaw % 360) + 360) % 360;

        String cardinal;
        if (normalizedYaw >= 337.5 || normalizedYaw < 22.5) {
            cardinal = "南";
        } else if (normalizedYaw < 67.5) {
            cardinal = "南西";
        } else if (normalizedYaw < 112.5) {
            cardinal = "西";
        } else if (normalizedYaw < 157.5) {
            cardinal = "北西";
        } else if (normalizedYaw < 202.5) {
            cardinal = "北";
        } else if (normalizedYaw < 247.5) {
            cardinal = "北東";
        } else if (normalizedYaw < 292.5) {
            cardinal = "東";
        } else {
            cardinal = "南東";
        }

        String text = String.format("HDG: %03d° %s", (int) normalizedYaw, cardinal);
        int tw = font.width(text);

        graphics.fill(cx - tw / 2 - 4, topY - 2, cx + tw / 2 + 4, topY + font.lineHeight + 2, 0x50000000);
        graphics.drawString(font, text, cx - tw / 2, topY, 0xFF55FFFF, true);
    }

    /**
     * 速度・高度・昇降速度・バッテリー・モーターインジケーターの描画（画面下部左側・日本語）
     */
    private void renderFlightInstruments(GuiGraphics graphics, Font font, int x, int y, DroneEntity drone) {
        Vec3 vel = drone.getDeltaMovement();
        double hSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z) * 20.0; // m/s
        double vSpeed = vel.y * 20.0;                                     // m/s
        double alt = drone.getY();

        // 背景ボックス (半透明黒, 5行分)
        graphics.fill(x - 4, y - 4, x + 96, y + 58, 0x40000000);

        String spdText = String.format("速度  %.1f m/s", hSpeed);
        String altText = String.format("高度  %.1f m", alt);
        String vsText = String.format("昇降  %+.1f m/s", vSpeed);

        graphics.drawString(font, spdText, x, y, 0xFFE0E0E0, true);
        graphics.drawString(font, altText, x, y + 11, 0xFFE0E0E0, true);

        // 昇降速度
        int vsColor = vSpeed > 0.3 ? 0xFF55FF55 : (vSpeed < -0.3 ? 0xFFFF9944 : 0xFFAAAAAA);
        graphics.drawString(font, vsText, x, y + 22, vsColor, true);

        // バッテリー残量表示
        if (drone.hasBattery()) {
            int remaining = drone.getBatteryRemaining();
            float pct = drone.getBatteryPercentage();
            int percent = (int) (pct * 100);

            int batColor;
            if (percent <= 10) {
                batColor = (drone.tickCount % 10 < 5) ? 0xFFFF2222 : 0x50FF2222; // 赤点滅
            } else if (percent <= 20) {
                batColor = 0xFFFF9944; // オレンジ
            } else if (percent <= 50) {
                batColor = 0xFFFFFF55; // 黄
            } else {
                batColor = 0xFF55FF55; // 緑
            }

            String batText = String.format("電池  %d%% (%dm)", percent, remaining);
            graphics.drawString(font, batText, x, y + 33, batColor, true);
        } else {
            graphics.drawString(font, "電池  未装着", x, y + 33, 0xFFAAAAAA, true);
        }

        // モーターTier表示
        int motorTier = drone.getMotorTier();
        int motorColor = drone.getMotorColor() | 0xFF000000;
        String motorName = switch (motorTier) {
            case 2 -> "T2 (Sport)";
            case 3 -> "T3 (High)";
            case 4 -> "T4 (Race)";
            default -> "T1 (Std)";
        };
        graphics.drawString(font, "動力  " + motorName, x, y + 44, motorColor, true);
    }

    /**
     * 電波強度と離脱距離の描画（画面下部右側・日本語）
     */
    private void renderSignalStatus(GuiGraphics graphics, Font font, int x, int y, DroneEntity drone) {
        double sx = ClientDroneInputHandler.getStartX();
        double sy = ClientDroneInputHandler.getStartY();
        double sz = ClientDroneInputHandler.getStartZ();
        double dist = Math.sqrt(drone.distanceToSqr(sx, sy, sz));

        double maxRange = DroneEntity.MAX_SIGNAL_RANGE;
        double warnRange = DroneEntity.WARN_SIGNAL_RANGE;
        double critRange = DroneEntity.CRITICAL_SIGNAL_RANGE;

        double ratio = Math.max(0.0, 1.0 - (dist / maxRange));
        int rssi = (int) (ratio * 100);

        // 背景ボックス (半透明黒, 4行分)
        graphics.fill(x - 4, y - 4, x + 94, y + 47, 0x40000000);

        // 電波色判定
        int sigColor;
        String statusText;
        if (dist > maxRange) {
            sigColor = (drone.tickCount % 10 < 5) ? 0xFFFF2222 : 0x50FF2222;
            statusText = "通信  切断";
        } else if (dist > critRange) {
            sigColor = 0xFFFF4444;
            statusText = "通信  限界";
        } else if (dist > warnRange) {
            sigColor = 0xFFFFFF44;
            statusText = "通信  減衰";
        } else {
            sigColor = 0xFF55FF55;
            statusText = "通信  良好";
        }

        String distText = String.format("距離  %.0f m", dist);
        String rssiText = String.format("電波  %d%%", rssi);

        graphics.drawString(font, distText, x, y, 0xFFE0E0E0, true);
        graphics.drawString(font, rssiText, x, y + 11, sigColor, true);

        // アンテナピクト（4本のバー）
        int barBaseX = x + 64;
        int barBaseY = y + 20;
        int activeBars = (rssi > 75) ? 4 : (rssi > 50) ? 3 : (rssi > 25) ? 2 : (rssi > 0) ? 1 : 0;

        for (int i = 0; i < 4; i++) {
            int barHeight = 3 + i * 2;
            int bx = barBaseX + i * 4;
            int by = barBaseY - barHeight;
            int bCol = (i < activeBars) ? sigColor : 0x40666666;
            graphics.fill(bx, by, bx + 2, barBaseY, bCol);
        }

        String maxText = String.format("限界  %.0f m", maxRange);
        graphics.drawString(font, maxText, x, y + 22, 0xFF888888, true);
        graphics.drawString(font, statusText, x, y + 33, sigColor, true);
    }

    /**
     * 電波＆バッテリー警告メッセージ（画面中央下・日本語）
     */
    private void renderAlerts(GuiGraphics graphics, Font font, int cx, int y, DroneEntity drone) {
        double sx = ClientDroneInputHandler.getStartX();
        double sy = ClientDroneInputHandler.getStartY();
        double sz = ClientDroneInputHandler.getStartZ();
        double dist = Math.sqrt(drone.distanceToSqr(sx, sy, sz));

        // 1. 電波関連の警告
        if (dist > DroneEntity.MAX_SIGNAL_RANGE) {
            if (drone.tickCount % 8 < 5) {
                Component alert = Component.literal("§c§l[ 電波切断: 緊急自動帰還フェイルセーフ作動中 ]");
                int aw = font.width(alert);
                graphics.fill(cx - aw / 2 - 4, y - 2, cx + aw / 2 + 4, y + font.lineHeight + 2, 0x80000000);
                graphics.drawString(font, alert, cx - aw / 2, y, 0xFFFF2222, true);
            }
        } else if (dist > DroneEntity.CRITICAL_SIGNAL_RANGE) {
            Component alert = Component.literal("§c§l[ 危険: 最大飛行限界に接近中 ]");
            int aw = font.width(alert);
            graphics.fill(cx - aw / 2 - 4, y - 2, cx + aw / 2 + 4, y + font.lineHeight + 2, 0x60000000);
            graphics.drawString(font, alert, cx - aw / 2, y, 0xFFFF4444, true);
        } else if (dist > DroneEntity.WARN_SIGNAL_RANGE) {
            Component alert = Component.literal("§e[ 警告: 電波が弱くなっています ]");
            int aw = font.width(alert);
            graphics.fill(cx - aw / 2 - 4, y - 2, cx + aw / 2 + 4, y + font.lineHeight + 2, 0x50000000);
            graphics.drawString(font, alert, cx - aw / 2, y, 0xFFFFFF55, true);
        }

        // 2. バッテリー関連の警告
        if (drone.hasBattery()) {
            int remaining = drone.getBatteryRemaining();
            float pct = drone.getBatteryPercentage();
            int alertY = y + 14;

            if (remaining <= 0) {
                Component alert = Component.literal("§c§l[ バッテリー切れ: 緊急自動着陸中 ]");
                int aw = font.width(alert);
                graphics.fill(cx - aw / 2 - 4, alertY - 2, cx + aw / 2 + 4, alertY + font.lineHeight + 2, 0x80000000);
                graphics.drawString(font, alert, cx - aw / 2, alertY, 0xFFFF2222, true);
            } else if (pct <= 0.10f) {
                if (drone.tickCount % 10 < 6) {
                    Component alert = Component.literal("§c§l[ 警告: バッテリー残量わずか (" + remaining + "m) ]");
                    int aw = font.width(alert);
                    graphics.fill(cx - aw / 2 - 4, alertY - 2, cx + aw / 2 + 4, alertY + font.lineHeight + 2, 0x80000000);
                    graphics.drawString(font, alert, cx - aw / 2, alertY, 0xFFFF2222, true);
                }
            } else if (pct <= 0.20f) {
                Component alert = Component.literal("§e[ 注意: バッテリー残量低下 (" + remaining + "m) ]");
                int aw = font.width(alert);
                graphics.fill(cx - aw / 2 - 4, alertY - 2, cx + aw / 2 + 4, alertY + font.lineHeight + 2, 0x60000000);
                graphics.drawString(font, alert, cx - aw / 2, alertY, 0xFFFFFF55, true);
            }
        }
    }

    /**
     * 操作ガイド（画面下部中央・日本語）
     */
    private void renderControlsGuide(GuiGraphics graphics, Font font, int cx, int bottomY) {
        String guide = "§7[右クリック: 操縦終了]";
        int gw = font.width(guide);
        graphics.drawString(font, guide, cx - gw / 2, bottomY, 0x90FFFFFF, true);
    }
}
