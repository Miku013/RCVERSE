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

        // 2. 機首方位（HDG / コンパス） - 画面上部中央
        renderCompass(graphics, font, cx, 12, drone.getYRot());

        // 3. 画面下部計器の基準Y座標 (画面下端から約44px上)
        int bottomInstrumentsY = height - 44;

        // 4. 飛行計器（画面下部左側: 速度、高度、昇降）
        renderFlightInstruments(graphics, font, 14, bottomInstrumentsY, drone);

        // 5. 電波強度・離脱距離（画面下部右側: 距離、電波、アンテナバー）
        renderSignalStatus(graphics, font, width - 104, bottomInstrumentsY, drone);

        // 6. 範囲警告メッセージ（画面中央下）
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
     * 上部コンパス方位の描画（日本語）
     */
    private void renderCompass(GuiGraphics graphics, Font font, int cx, int topY, float rawYaw) {
        float yaw = rawYaw % 360.0f;
        if (yaw < 0) yaw += 360.0f;

        // Minecraft座標系のYawから方位文字を取得（日本語）
        // 0=南, 90=西, 180=北, 270=東
        String dir;
        if (yaw >= 337.5 || yaw < 22.5) dir = "南";
        else if (yaw < 67.5) dir = "南西";
        else if (yaw < 112.5) dir = "西";
        else if (yaw < 157.5) dir = "北西";
        else if (yaw < 202.5) dir = "北";
        else if (yaw < 247.5) dir = "北東";
        else if (yaw < 292.5) dir = "東";
        else dir = "南東";

        String text = String.format("[ 方位: %s %03d° ]", dir, (int) yaw);
        int tw = font.width(text);

        // 背景半透明チップ
        graphics.fill(cx - tw / 2 - 4, topY - 2, cx + tw / 2 + 4, topY + font.lineHeight + 2, 0x50000000);
        graphics.drawString(font, text, cx - tw / 2, topY, 0xFF55FFFF, true);
    }

    /**
     * 速度・高度・昇降速度インジケーターの描画（画面下部左側・日本語）
     */
    private void renderFlightInstruments(GuiGraphics graphics, Font font, int x, int y, DroneEntity drone) {
        Vec3 vel = drone.getDeltaMovement();
        double hSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z) * 20.0; // m/s
        double vSpeed = vel.y * 20.0;                                     // m/s
        double alt = drone.getY();

        // 背景ボックス (半透明黒)
        graphics.fill(x - 4, y - 4, x + 90, y + 36, 0x40000000);

        String spdText = String.format("速度  %.1f m/s", hSpeed);
        String altText = String.format("高度  %.1f m", alt);
        String vsText = String.format("昇降  %+.1f m/s", vSpeed);

        graphics.drawString(font, spdText, x, y, 0xFFE0E0E0, true);
        graphics.drawString(font, altText, x, y + 11, 0xFFE0E0E0, true);

        // 昇降速度は上昇: 緑、下降: オレンジ、水平: グレー
        int vsColor = vSpeed > 0.3 ? 0xFF55FF55 : (vSpeed < -0.3 ? 0xFFFF9944 : 0xFFAAAAAA);
        graphics.drawString(font, vsText, x, y + 22, vsColor, true);
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

        // 背景ボックス (半透明黒)
        graphics.fill(x - 4, y - 4, x + 94, y + 36, 0x40000000);

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

        // 限界距離の目安表示
        String maxText = String.format("限界  %.0f m", maxRange);
        graphics.drawString(font, maxText, x, y + 22, 0xFF888888, true);
    }

    /**
     * 電波警告メッセージ（圏外・危険接近時のみ・日本語）
     */
    private void renderAlerts(GuiGraphics graphics, Font font, int cx, int y, DroneEntity drone) {
        double sx = ClientDroneInputHandler.getStartX();
        double sy = ClientDroneInputHandler.getStartY();
        double sz = ClientDroneInputHandler.getStartZ();
        double dist = Math.sqrt(drone.distanceToSqr(sx, sy, sz));

        if (dist > DroneEntity.MAX_SIGNAL_RANGE) {
            // 電波ロスト
            if (drone.tickCount % 8 < 5) {
                Component alert = Component.literal("§c§l[ 電波切断: 緊急自動帰還フェイルセーフ作動中 ]");
                int aw = font.width(alert);
                graphics.fill(cx - aw / 2 - 4, y - 2, cx + aw / 2 + 4, y + font.lineHeight + 2, 0x80000000);
                graphics.drawString(font, alert, cx - aw / 2, y, 0xFFFF2222, true);
            }
        } else if (dist > DroneEntity.CRITICAL_SIGNAL_RANGE) {
            // 限界接近
            Component alert = Component.literal("§c§l[ 危険: 最大飛行限界に接近中 ]");
            int aw = font.width(alert);
            graphics.fill(cx - aw / 2 - 4, y - 2, cx + aw / 2 + 4, y + font.lineHeight + 2, 0x60000000);
            graphics.drawString(font, alert, cx - aw / 2, y, 0xFFFF4444, true);
        } else if (dist > DroneEntity.WARN_SIGNAL_RANGE) {
            // 減衰警告
            Component alert = Component.literal("§e[ 警告: 電波が弱くなっています ]");
            int aw = font.width(alert);
            graphics.fill(cx - aw / 2 - 4, y - 2, cx + aw / 2 + 4, y + font.lineHeight + 2, 0x50000000);
            graphics.drawString(font, alert, cx - aw / 2, y, 0xFFFFFF55, true);
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
