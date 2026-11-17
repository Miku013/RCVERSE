package com.microdrone.mod.client.gui;

import com.microdrone.mod.client.input.ClientDroneInputHandler;
import com.microdrone.mod.entity.DroneEntity;
import com.microdrone.mod.entity.RcTankEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * マイクロRC戦車 HUDオーバーレイ (ガンナー視点 ＆ 車載チェイス/モデル視点)
 * - 砲塔仰角インジケーター（+35° 〜 0° 〜 -15° スケールバー ＆ 数値）
 * - 車体方位 ＆ 主砲方位 ＆ 砲塔旋回角（上部デュアルコンパス）
 * - 車体＆砲身の相対向きミニレーダー（左上方位マップ）
 * - 主砲次弾装填秒数 / リロードゲージ
 * - バッテリー残量バー ＆ 機関Tier ＆ 車速
 * - プロポ通信距離 & 電波強度
 */
public class TankGunnerHudOverlay {

    public static void register() {
        NeoForge.EVENT_BUS.register(new TankGunnerHudOverlay());
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        if (!ClientDroneInputHandler.isPiloting()) return;
        if (!ClientDroneInputHandler.isRcTankPiloting()) return;

        ClientDroneInputHandler.RcCameraMode mode = ClientDroneInputHandler.getCarCameraMode();
        // ガンナーモード または チェイスモード（モデル視点）の時に描画
        if (mode != ClientDroneInputHandler.RcCameraMode.GUNNER_AIM &&
            mode != ClientDroneInputHandler.RcCameraMode.CHASE_CAR) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Entity entity = mc.level.getEntity(ClientDroneInputHandler.getActiveDroneId());
        if (!(entity instanceof RcTankEntity tank)) return;

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int cx = width / 2;
        int cy = height / 2;

        if (mode == ClientDroneInputHandler.RcCameraMode.GUNNER_AIM) {
            // ガンナー視点専用クロスヘア
            renderGunnerReticle(graphics, font, cx, cy);
        } else {
            // モデル/チェイス視点用クロスヘア
            renderChaseCrosshair(graphics, cx, cy);
        }

        // 1. 主砲装填インジケーター（中央下）
        renderReloadStatus(graphics, font, cx, cy + 32, tank);

        // 2. 砲身仰角インジケーター（画面中央右側: +35° 〜 0° 〜 -15° 目盛り & 数値）
        renderElevationIndicator(graphics, font, cx + 50, cy - 26, tank);

        // 3. 上部デュアル方位コンパス（車体方位 ＆ 主砲方位 ＆ 旋回角）
        renderDualCompass(graphics, font, cx, 10, tank);

        // 4. 左上: 車体＆主砲の相対向きミニレーダー
        renderTankRadar(graphics, font, 14, 14, tank);

        // 5. 左下: バッテリー・機関・速度
        renderTankStatus(graphics, font, 14, height - 55, tank);

        // 6. 右下: 受信距離・電波強度・視点名
        renderSignalStatus(graphics, font, width - 110, height - 55, tank, mode);
    }

    /**
     * ガンナー専用クロスヘア・照準目盛り
     */
    private void renderGunnerReticle(GuiGraphics graphics, Font font, int cx, int cy) {
        int color = 0x9055FF55; // ミリタリーオリーブグリーン

        // 中央ドット
        graphics.fill(cx, cy, cx + 1, cy + 1, 0xFFFFFFFF);

        // 中央サークルブラケット
        graphics.fill(cx - 7, cy - 3, cx - 6, cy + 4, color);
        graphics.fill(cx + 7, cy - 3, cx + 8, cy + 4, color);
        graphics.fill(cx - 3, cy - 7, cx + 4, cy - 6, color);
        graphics.fill(cx - 3, cy + 7, cx + 4, cy + 8, color);

        // 水平照準目盛り (左)
        graphics.fill(cx - 28, cy, cx - 12, cy + 1, color);
        graphics.fill(cx - 20, cy - 3, cx - 19, cy + 4, color);
        // 水平照準目盛り (右)
        graphics.fill(cx + 13, cy, cx + 29, cy + 1, color);
        graphics.fill(cx + 20, cy - 3, cx + 21, cy + 4, color);

        // 垂直照準線 (上)
        graphics.fill(cx, cy - 24, cx + 1, cy - 10, color);

        // 垂直弾道ドロップ目盛り (下)
        graphics.fill(cx, cy + 10, cx + 1, cy + 24, color);
        graphics.fill(cx - 3, cy + 14, cx + 4, cy + 15, color);
        graphics.fill(cx - 5, cy + 18, cx + 6, cy + 19, color);
        graphics.fill(cx - 2, cy + 22, cx + 3, cy + 23, color);
    }

    /**
     * チェイス視点（モデル視点）用センターガイド
     */
    private void renderChaseCrosshair(GuiGraphics graphics, int cx, int cy) {
        int color = 0x50FFFFFF;
        graphics.fill(cx - 4, cy, cx + 5, cy + 1, color);
        graphics.fill(cx, cy - 4, cx + 1, cy + 5, color);
    }

    /**
     * 砲身仰角インジケーター（+35° 〜 0° 〜 -15° スケールバー ＆ 数値）
     */
    private void renderElevationIndicator(GuiGraphics graphics, Font font, int x, int y, RcTankEntity tank) {
        // tank.getCannonPitch(): 上向きが負、下向きが正
        // 仰角 (Elevation): 上向きがプラス(+), 下向きがマイナス(-)
        float pitch = tank.getCannonPitch();
        float elev = -pitch;

        int barHeight = 50;

        // 半透明背景
        graphics.fill(x - 4, y - 10, x + 56, y + barHeight + 14, 0x40000000);

        // ヘッダー
        graphics.drawString(font, "ELEV 仰角", x, y - 8, 0xFFB0B0B0, true);

        // 垂直基準スケール線
        graphics.fill(x + 2, y + 4, x + 3, y + 4 + barHeight, 0x80888888);

        // 目盛り: +35° (上端)
        graphics.fill(x + 3, y + 4, x + 7, y + 5, 0x80FFFFFF);
        graphics.drawString(font, "+35°", x + 9, y + 1, 0xFF888888, false);

        // 目盛り: 0° (水平) - 全50°スパンのうち上から35°の位置
        int zeroY = y + 4 + (int) (barHeight * (35.0f / 50.0f));
        graphics.fill(x + 1, zeroY, x + 8, zeroY + 1, 0xFF55FF55);
        graphics.drawString(font, "  0°", x + 9, zeroY - 4, 0xFF55FF55, false);

        // 目盛り: -15° (下端)
        graphics.fill(x + 3, y + 4 + barHeight - 1, x + 7, y + 4 + barHeight, 0x80FFFFFF);
        graphics.drawString(font, "-15°", x + 9, y + 4 + barHeight - 4, 0xFF888888, false);

        // 現在の仰角ポインター
        float clampedElev = Mth.clamp(elev, -15.0f, 35.0f);
        float progress = (35.0f - clampedElev) / 50.0f; // 0.0(+35°) 〜 1.0(-15°)
        int markerY = y + 4 + (int) (barHeight * progress);

        int markerColor = (Math.abs(elev) < 1.0f) ? 0xFF55FF55 : 0xFFFFCC00;
        graphics.fill(x - 2, markerY - 1, x + 4, markerY + 2, markerColor);

        // 数値表示
        String text = String.format("%+4.1f°", elev);
        graphics.drawString(font, text, x, y + barHeight + 5, markerColor, true);
    }

    /**
     * 上部デュアル方位コンパス（車体方位 ＆ 主砲方位 ＆ 旋回角）
     */
    private void renderDualCompass(GuiGraphics graphics, Font font, int cx, int y, RcTankEntity tank) {
        // 車体方位 (0〜360°)
        float hullYaw = ((tank.getYRot() % 360) + 360) % 360;
        String hullDir = getCardinal(hullYaw);
        String hullText = net.minecraft.network.chat.Component.translatable("hud.microdrone.tank.hull_dir", String.format("%03.0f", hullYaw), hullDir).getString();

        // 主砲方位 (車体Yaw + 砲塔相対Yaw)
        float relYaw = tank.getTurretYaw();
        float gunYaw = (((tank.getYRot() + relYaw) % 360) + 360) % 360;
        String gunDir = getCardinal(gunYaw);
        String gunText = net.minecraft.network.chat.Component.translatable("hud.microdrone.tank.gun_dir", String.format("%03.0f", gunYaw), gunDir).getString();

        // 砲塔相対旋回角
        String relText = net.minecraft.network.chat.Component.translatable("hud.microdrone.tank.turret_yaw", String.format("%+4.0f", relYaw)).getString();

        int pad = 8;
        int hW = font.width(hullText);
        int rW = font.width(relText);
        int gW = font.width(gunText);
        int totalW = hW + rW + gW + pad * 2;

        int leftX = cx - (totalW / 2);
        // 背景プレート
        graphics.fill(leftX - 6, y - 2, leftX + totalW + 6, y + 11, 0x50000000);

        int curX = leftX;
        // 車体方位 (シアン)
        graphics.drawString(font, hullText, curX, y, 0xFF88CCFF, true);
        curX += hW + pad;

        // 砲塔相対旋回角 (イエロー)
        graphics.drawString(font, relText, curX, y, 0xFFFFDD44, true);
        curX += rW + pad;

        // 主砲方位 (グリーン)
        graphics.drawString(font, gunText, curX, y, 0xFF55FF55, true);
    }

    /**
     * 左上: 車体＆主砲の相対向きミニレーダー
     */
    private void renderTankRadar(GuiGraphics graphics, Font font, int x, int y, RcTankEntity tank) {
        int size = 46;
        int cx = x + size / 2;
        int cy = y + size / 2;

        // 半透明円形背景
        graphics.fill(x - 2, y - 2, x + size + 2, y + size + 11, 0x40000000);
        graphics.drawString(font, net.minecraft.network.chat.Component.translatable("hud.microdrone.tank.radar").getString(), x, y - 1, 0xFFB0B0B0, false);

        // 方位ラベル (N/S/E/W)
        graphics.drawString(font, "N", cx - 2, y + 7, 0xFFFF4444, false); // 北: 赤
        graphics.drawString(font, "S", cx - 2, y + size - 8, 0xFF888888, false);
        graphics.drawString(font, "E", x + size - 6, cy - 3, 0xFF888888, false);
        graphics.drawString(font, "W", x + 1, cy - 3, 0xFF888888, false);

        // レーダー枠線
        int r = 16;
        drawCircle(graphics, cx, cy, r, 0x30FFFFFF);

        // 1. 車体の向きアイコン（シアン）
        // Minecraft: Yaw=0は南(+Z), 90は西(-X), 180は北(-Z), 270は東(+X)
        // 画面上を「北(0度)」に合わせるため、(yaw - 180)度で回転
        float hullRad = (float) Math.toRadians(tank.getYRot() - 180.0f);
        drawTankBodyIcon(graphics, cx, cy, hullRad, 0xFF88CCFF);

        // 2. 主砲の向きライン（グリーン）
        float gunRad = (float) Math.toRadians(tank.getYRot() + tank.getTurretYaw() - 180.0f);
        float gunEndX = cx - Mth.sin(gunRad) * (r + 1);
        float gunEndY = cy + Mth.cos(gunRad) * (r + 1);
        drawLine(graphics, cx, cy, gunEndX, gunEndY, 0xFF55FF55);
        // 砲口ドット
        graphics.fill((int) gunEndX - 1, (int) gunEndY - 1, (int) gunEndX + 2, (int) gunEndY + 2, 0xFFFFFFFF);
    }

    private void drawTankBodyIcon(GuiGraphics graphics, int cx, int cy, float rad, int color) {
        // 車体を前後に長い角丸長方形として簡易描画
        float fwdX = -Mth.sin(rad);
        float fwdY = Mth.cos(rad);
        float rightX = -fwdY;
        float rightY = fwdX;

        float halfL = 8.0f;
        float halfW = 4.5f;

        // 車体4頂点
        float xFL = cx + fwdX * halfL - rightX * halfW;
        float yFL = cy + fwdY * halfL - rightY * halfW;
        float xFR = cx + fwdX * halfL + rightX * halfW;
        float yFR = cy + fwdY * halfL + rightY * halfW;
        float xRL = cx - fwdX * halfL - rightX * halfW;
        float yRL = cy - fwdY * halfL - rightY * halfW;
        float xRR = cx - fwdX * halfL + rightX * halfW;
        float yRR = cy - fwdY * halfL + rightY * halfW;

        drawLine(graphics, xFL, yFL, xFR, yFR, color);
        drawLine(graphics, xFR, yFR, xRR, yRR, color);
        drawLine(graphics, xRR, yRR, xRL, yRL, color);
        drawLine(graphics, xRL, yRL, xFL, yFL, color);

        // 前方ノーズマーク（小さな突起）
        float noseX = cx + fwdX * (halfL + 2.0f);
        float noseY = cy + fwdY * (halfL + 2.0f);
        graphics.fill((int) noseX, (int) noseY, (int) noseX + 1, (int) noseY + 1, 0xFFFFFFFF);
    }

    private void drawCircle(GuiGraphics graphics, int cx, int cy, int r, int color) {
        for (int i = 0; i < 24; i++) {
            double a1 = i * (Math.PI * 2 / 24);
            double a2 = (i + 1) * (Math.PI * 2 / 24);
            float x1 = (float) (cx + Math.cos(a1) * r);
            float y1 = (float) (cy + Math.sin(a1) * r);
            float x2 = (float) (cx + Math.cos(a2) * r);
            float y2 = (float) (cy + Math.sin(a2) * r);
            drawLine(graphics, x1, y1, x2, y2, color);
        }
    }

    private void drawLine(GuiGraphics graphics, float x1, float y1, float x2, float y2, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        int steps = Math.max(1, (int) (dist * 1.5f));
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            int px = (int) (x1 + dx * t);
            int py = (int) (y1 + dy * t);
            graphics.fill(px, py, px + 1, py + 1, color);
        }
    }

    /**
     * 次弾装填インジケーター（リロードカウントダウン & 進行バー）
     */
    private void renderReloadStatus(GuiGraphics graphics, Font font, int cx, int y, RcTankEntity tank) {
        int reloadTicks = tank.getReloadTicks();
        int maxReload = 20; // 1.0秒 = 20 ticks

        int barWidth = 60;
        int barHeight = 4;
        int barX = cx - (barWidth / 2);
        int barY = y + 12;

        if (reloadTicks > 0) {
            float remainingSec = reloadTicks / 20.0f;
            String text = net.minecraft.network.chat.Component.translatable("hud.microdrone.tank.reloading", String.format("%.1f", remainingSec)).getString();
            int textWidth = font.width(text);
            graphics.drawString(font, text, cx - (textWidth / 2), y, 0xFFFF5555, true);

            graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0x60000000);
            float progress = 1.0f - ((float) reloadTicks / maxReload);
            int fillW = (int) (barWidth * progress);
            graphics.fill(barX, barY, barX + fillW, barY + barHeight, 0xFFFF8800);
        } else {
            String text = net.minecraft.network.chat.Component.translatable("hud.microdrone.tank.ready").getString();
            int textWidth = font.width(text);
            graphics.drawString(font, text, cx - (textWidth / 2), y, 0xFF55FF55, true);

            graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0x60000000);
            graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF55FF55);
        }
    }

    /**
     * 左下: バッテリー残量・機関Tier・速度
     */
    private void renderTankStatus(GuiGraphics graphics, Font font, int x, int y, RcTankEntity tank) {
        graphics.fill(x - 4, y - 4, x + 96, y + 47, 0x40000000);

        int batRemaining = tank.getBatteryRemaining();
        int batMax = tank.getBatteryMax();
        float batRatio = batMax > 0 ? (float) batRemaining / batMax : 0.0f;
        int batPct = (int) (batRatio * 100);

        int batColor;
        if (batPct <= 15) {
            batColor = (tank.tickCount % 10 < 5) ? 0xFFFF2222 : 0x50FF2222;
        } else if (batPct <= 30) {
            batColor = 0xFFFFFF44;
        } else {
            batColor = 0xFF55FF55;
        }

        String batText = net.minecraft.network.chat.Component.translatable("hud.microdrone.tank.power", batPct).getString();
        graphics.drawString(font, batText, x, y, batColor, true);

        int barX = x + 50;
        int barY = y + 2;
        int barW = 38;
        int barH = 5;
        graphics.fill(barX, barY, barX + barW, barY + barH, 0x60333333);
        int fillW = (int) (barW * Math.min(1.0f, batRatio));
        graphics.fill(barX, barY, barX + fillW, barY + barH, batColor);

        String motorText = net.minecraft.network.chat.Component.translatable("hud.microdrone.tank.engine", tank.getMotorTier()).getString();
        graphics.drawString(font, motorText, x, y + 11, 0xFFE0E0E0, true);

        Vec3 vel = tank.getDeltaMovement();
        double speedKmh = Math.sqrt(vel.x * vel.x + vel.z * vel.z) * 20.0 * 3.6;
        String speedText = net.minecraft.network.chat.Component.translatable("hud.microdrone.tank.speed", String.format("%.1f", speedKmh)).getString();
        graphics.drawString(font, speedText, x, y + 22, 0xFFB0B0B0, true);

        String fireGuide = net.minecraft.network.chat.Component.translatable("hud.microdrone.tank.fire_guide").getString();
        graphics.drawString(font, fireGuide, x, y + 33, 0xFFFFCC00, true);
    }

    /**
     * 右下: 受信距離・電波強度・視点モード
     */
    private void renderSignalStatus(GuiGraphics graphics, Font font, int x, int y, RcTankEntity tank, ClientDroneInputHandler.RcCameraMode mode) {
        double sx = ClientDroneInputHandler.getStartX();
        double sy = ClientDroneInputHandler.getStartY();
        double sz = ClientDroneInputHandler.getStartZ();
        double dist = Math.sqrt(tank.distanceToSqr(sx, sy, sz));

        double maxRange = DroneEntity.MAX_SIGNAL_RANGE; // 64m
        double warnRange = DroneEntity.WARN_SIGNAL_RANGE;
        double critRange = DroneEntity.CRITICAL_SIGNAL_RANGE;

        double ratio = Math.max(0.0, 1.0 - (dist / maxRange));
        int rssi = (int) (ratio * 100);

        graphics.fill(x - 4, y - 4, x + 96, y + 47, 0x40000000);

        int sigColor;
        String statusText;
        if (dist > maxRange) {
            sigColor = (tank.tickCount % 10 < 5) ? 0xFFFF2222 : 0x50FF2222;
            statusText = net.minecraft.network.chat.Component.translatable("hud.microdrone.signal_lost").getString();
        } else if (dist > critRange) {
            sigColor = 0xFFFF4444;
            statusText = net.minecraft.network.chat.Component.translatable("hud.microdrone.signal_crit").getString();
        } else if (dist > warnRange) {
            sigColor = 0xFFFFFF44;
            statusText = net.minecraft.network.chat.Component.translatable("hud.microdrone.signal_warn").getString();
        } else {
            sigColor = 0xFF55FF55;
            statusText = net.minecraft.network.chat.Component.translatable("hud.microdrone.signal_good").getString();
        }

        String distText = net.minecraft.network.chat.Component.translatable("hud.microdrone.distance", String.format("%.1f", dist)).getString();
        String rssiText = net.minecraft.network.chat.Component.translatable("hud.microdrone.rssi", rssi).getString();

        graphics.drawString(font, distText, x, y, 0xFFE0E0E0, true);
        graphics.drawString(font, rssiText, x, y + 11, sigColor, true);

        int barBaseX = x + 66;
        int barBaseY = y + 19;
        int activeBars = (rssi > 75) ? 4 : (rssi > 50) ? 3 : (rssi > 25) ? 2 : (rssi > 0) ? 1 : 0;
        for (int i = 0; i < 4; i++) {
            int bHeight = 3 + i * 2;
            int bx = barBaseX + i * 4;
            int by = barBaseY - bHeight;
            int bCol = (i < activeBars) ? sigColor : 0x40666666;
            graphics.fill(bx, by, bx + 2, barBaseY, bCol);
        }

        graphics.drawString(font, statusText, x, y + 22, sigColor, true);

        String viewName = (mode == ClientDroneInputHandler.RcCameraMode.CHASE_CAR) 
                ? net.minecraft.network.chat.Component.translatable("hud.microdrone.tank.view_chase").getString() 
                : net.minecraft.network.chat.Component.translatable("hud.microdrone.tank.view_gunner").getString();
        int viewColor = (mode == ClientDroneInputHandler.RcCameraMode.CHASE_CAR) ? 0xFFFFAA00 : 0xFF88CCFF;
        graphics.drawString(font, viewName, x, y + 33, viewColor, true);
    }

    private static String getCardinal(float yaw) {
        float normalizedYaw = ((yaw % 360) + 360) % 360;
        if (normalizedYaw >= 337.5 || normalizedYaw < 22.5) return net.minecraft.network.chat.Component.translatable("hud.microdrone.cardinal.s").getString();
        if (normalizedYaw < 67.5) return net.minecraft.network.chat.Component.translatable("hud.microdrone.cardinal.sw").getString();
        if (normalizedYaw < 112.5) return net.minecraft.network.chat.Component.translatable("hud.microdrone.cardinal.w").getString();
        if (normalizedYaw < 157.5) return net.minecraft.network.chat.Component.translatable("hud.microdrone.cardinal.nw").getString();
        if (normalizedYaw < 202.5) return net.minecraft.network.chat.Component.translatable("hud.microdrone.cardinal.n").getString();
        if (normalizedYaw < 247.5) return net.minecraft.network.chat.Component.translatable("hud.microdrone.cardinal.ne").getString();
        if (normalizedYaw < 292.5) return net.minecraft.network.chat.Component.translatable("hud.microdrone.cardinal.e").getString();
        return net.minecraft.network.chat.Component.translatable("hud.microdrone.cardinal.se").getString();
    }
}
