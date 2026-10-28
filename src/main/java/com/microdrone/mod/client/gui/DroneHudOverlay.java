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
 * FPVマイクロドローン専用のフライト計器・HUDオーバーレイ (Phase 4 / Step 4)
 */
public class DroneHudOverlay {

    public static void register() {
        NeoForge.EVENT_BUS.register(new DroneHudOverlay());
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        // ドローン操縦中かつカメラがドローン視点の時のみ描画
        if (!ClientDroneInputHandler.isPiloting() || !DroneCameraController.isViewing()) {
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

        // 2. 機首方位（HDG / コンパス）
        renderCompass(graphics, font, cx, 14, drone.getYRot());

        // 3. 飛行計器（左側: 速度 SPD, 高度 ALT, 昇降速度 V/S）
        renderFlightInstruments(graphics, font, 16, cy - 20, drone);

        // 4. 電波強度・離脱距離（右側: 距離 DIST, 電波 RSSI, アンテナバー）
        renderSignalStatus(graphics, font, width - 96, cy - 20, drone);

        // 5. 範囲警告メッセージ（中央下）
        renderAlerts(graphics, font, cx, cy + 28, drone);

        // 6. 操作ガイド（右下）
        renderControlsGuide(graphics, font, width - 10, height - 14);
    }

    /**
     * 中央クロスヘアの描画
     */
    private void renderCrosshair(GuiGraphics graphics, int cx, int cy) {
        int color = 0x90A0E0FF; // 半透明のサイアンブルー

        // 水平ガイドバー (隙間 8px, 長さ 10px)
        graphics.fill(cx - 15, cy, cx - 5, cy + 1, color);
        graphics.fill(cx + 6, cy, cx + 16, cy + 1, color);

        // 垂直ガイドバー (隙間 6px, 長さ 6px)
        graphics.fill(cx, cy - 10, cx + 1, cy - 4, color);
        graphics.fill(cx, cy + 5, cx + 1, cy + 11, color);

        // 中央微小ターゲットドット
        graphics.fill(cx, cy, cx + 1, cy + 1, 0xD0FFFFFF);

        // 四隅のFPVフレームブラケット
        int bColor = 0x40FFFFFF;
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
     * 上部コンパス方位の描画
     */
    private void renderCompass(GuiGraphics graphics, Font font, int cx, int topY, float rawYaw) {
        float yaw = rawYaw % 360.0f;
        if (yaw < 0) yaw += 360.0f;

        // Minecraft座標系のYawから方位文字を取得
        // 0=S, 90=W, 180=N, 270=E
        String dir;
        if (yaw >= 337.5 || yaw < 22.5) dir = "S";
        else if (yaw < 67.5) dir = "SW";
        else if (yaw < 112.5) dir = "W";
        else if (yaw < 157.5) dir = "NW";
        else if (yaw < 202.5) dir = "N";
        else if (yaw < 247.5) dir = "NE";
        else if (yaw < 292.5) dir = "E";
        else dir = "SE";

        String text = String.format("[ %s %03d° ]", dir, (int) yaw);
        int tw = font.width(text);

        // 背景半透明チップ
        graphics.fill(cx - tw / 2 - 4, topY - 2, cx + tw / 2 + 4, topY + font.lineHeight + 2, 0x50000000);
        graphics.drawString(font, text, cx - tw / 2, topY, 0xFF55FFFF, true);
    }

    /**
     * 速度・高度・昇降速度インジケーターの描画
     */
    private void renderFlightInstruments(GuiGraphics graphics, Font font, int x, int y, DroneEntity drone) {
        Vec3 vel = drone.getDeltaMovement();
        double hSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z) * 20.0; // m/s
        double vSpeed = vel.y * 20.0;                                     // m/s
        double alt = drone.getY();

        // 背景ボックス
        graphics.fill(x - 4, y - 4, x + 76, y + 36, 0x40000000);

        String spdText = String.format("SPD  %.1f", hSpeed);
        String altText = String.format("ALT  %.1f", alt);
        String vsText = String.format("V/S  %+.1f", vSpeed);

        graphics.drawString(font, spdText, x, y, 0xFFE0E0E0, true);
        graphics.drawString(font, altText, x, y + 11, 0xFFE0E0E0, true);

        // 昇降速度は上昇: 緑、下降: オレンジ
        int vsColor = vSpeed > 0.3 ? 0xFF55FF55 : (vSpeed < -0.3 ? 0xFFFF9944 : 0xFFAAAAAA);
        graphics.drawString(font, vsText, x, y + 22, vsColor, true);
    }

    /**
     * 電波強度と離脱距離の描画
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

        // 背景ボックス
        graphics.fill(x - 4, y - 4, x + 84, y + 36, 0x40000000);

        // 電波色判定
        int sigColor;
        if (dist > maxRange) {
            // 電波ロスト: 赤点滅
            sigColor = (drone.tickCount % 10 < 5) ? 0xFFFF2222 : 0x50FF2222;
        } else if (dist > critRange) {
            sigColor = 0xFFFF4444; // 赤
        } else if (dist > warnRange) {
            sigColor = 0xFFFFFF44; // 黄
        } else {
            sigColor = 0xFF55FF55; // 緑
        }

        String distText = String.format("DIST %.0fm", dist);
        String rssiText = String.format("RSSI %d%%", rssi);

        graphics.drawString(font, distText, x, y, 0xFFE0E0E0, true);
        graphics.drawString(font, rssiText, x, y + 11, sigColor, true);

        // アンテナピクト（4本のバー）
        int barBaseX = x + 56;
        int barBaseY = y + 20;
        int activeBars = (rssi > 75) ? 4 : (rssi > 50) ? 3 : (rssi > 25) ? 2 : (rssi > 0) ? 1 : 0;

        for (int i = 0; i < 4; i++) {
            int barHeight = 3 + i * 2;
            int bx = barBaseX + i * 4;
            int by = barBaseY - barHeight;
            int bCol = (i < activeBars) ? sigColor : 0x40666666;
            graphics.fill(bx, by, bx + 2, barBaseY, bCol);
        }
    }

    /**
     * 電波警告メッセージ（圏外・危険接近時のみ）
     */
    private void renderAlerts(GuiGraphics graphics, Font font, int cx, int y, DroneEntity drone) {
        double sx = ClientDroneInputHandler.getStartX();
        double sy = ClientDroneInputHandler.getStartY();
        double sz = ClientDroneInputHandler.getStartZ();
        double dist = Math.sqrt(drone.distanceToSqr(sx, sy, sz));

        if (dist > DroneEntity.MAX_SIGNAL_RANGE) {
            // 電波ロスト
            if (drone.tickCount % 8 < 5) {
                Component alert = Component.literal("§c§l[ SIGNAL LOST - FAILSAFE ACTIVATING ]");
                int aw = font.width(alert);
                graphics.fill(cx - aw / 2 - 4, y - 2, cx + aw / 2 + 4, y + font.lineHeight + 2, 0x80000000);
                graphics.drawString(font, alert, cx - aw / 2, y, 0xFFFF2222, true);
            }
        } else if (dist > DroneEntity.CRITICAL_SIGNAL_RANGE) {
            // 限界接近
            Component alert = Component.literal("§c§l[ CAUTION: MAX FLIGHT RANGE LIMIT ]");
            int aw = font.width(alert);
            graphics.fill(cx - aw / 2 - 4, y - 2, cx + aw / 2 + 4, y + font.lineHeight + 2, 0x60000000);
            graphics.drawString(font, alert, cx - aw / 2, y, 0xFFFF4444, true);
        } else if (dist > DroneEntity.WARN_SIGNAL_RANGE) {
            // 減衰警告
            Component alert = Component.literal("§e[ WARNING: SIGNAL WEAK ]");
            int aw = font.width(alert);
            graphics.fill(cx - aw / 2 - 4, y - 2, cx + aw / 2 + 4, y + font.lineHeight + 2, 0x50000000);
            graphics.drawString(font, alert, cx - aw / 2, y, 0xFFFFFF55, true);
        }
    }

    /**
     * 右下の操作ガイド
     */
    private void renderControlsGuide(GuiGraphics graphics, Font font, int rightX, int bottomY) {
        String guide = "§7[Right-Click: Exit]";
        int gw = font.width(guide);
        graphics.drawString(font, guide, rightX - gw, bottomY, 0x80FFFFFF, true);
    }
}
