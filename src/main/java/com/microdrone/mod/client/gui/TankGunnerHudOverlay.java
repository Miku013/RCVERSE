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
 * マイクロRC戦車 ガンナー視点（一人称FPV）専用 ミリタリー照準HUD
 * - 主砲次弾装填秒数 / リロードゲージ
 * - バッテリー残量バー
 * - プロポ通信距離 & 電波強度
 * - 精密主砲レティクル & 方位コンパス
 */
public class TankGunnerHudOverlay {

    public static void register() {
        NeoForge.EVENT_BUS.register(new TankGunnerHudOverlay());
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        // 戦車操縦中 かつ ガンナーモードの時のみ描画
        if (!ClientDroneInputHandler.isPiloting()) return;
        if (!ClientDroneInputHandler.isRcTankPiloting()) return;
        if (ClientDroneInputHandler.getCarCameraMode() != ClientDroneInputHandler.RcCameraMode.GUNNER_AIM) return;

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

        // 1. ガンナー専用照準レティクル
        renderGunnerReticle(graphics, font, cx, cy);

        // 2. 主砲装填（リロードカウントダウン & バー）- 中央直下
        renderReloadStatus(graphics, font, cx, cy + 30, tank);

        // 3. 上部方位コンパス（HDG）
        renderCompass(graphics, font, cx, 12, mc.player.getYRot());

        // 4. 左下: バッテリー・機関・速度
        renderTankStatus(graphics, font, 14, height - 55, tank);

        // 5. 右下: 受信距離・電波強度
        renderSignalStatus(graphics, font, width - 110, height - 55, tank);
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
            // 装填中 (カウントダウン表示)
            float remainingSec = reloadTicks / 20.0f;
            String text = String.format("装填中 [%.1fs]", remainingSec);
            int textWidth = font.width(text);
            graphics.drawString(font, text, cx - (textWidth / 2), y, 0xFFFF5555, true);

            // 背景バー (ダークグレー)
            graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0x60000000);
            // 進行バー (オレンジ)
            float progress = 1.0f - ((float) reloadTicks / maxReload);
            int fillW = (int) (barWidth * progress);
            graphics.fill(barX, barY, barX + fillW, barY + barHeight, 0xFFFF8800);
        } else {
            // 装填完了 (READY)
            String text = "[ READY / 装填完了 ]";
            int textWidth = font.width(text);
            graphics.drawString(font, text, cx - (textWidth / 2), y, 0xFF55FF55, true);

            // 背景バー
            graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0x60000000);
            // フル点灯グリーンバー
            graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF55FF55);
        }
    }

    /**
     * 左下: バッテリー残量・機関Tier・速度
     */
    private void renderTankStatus(GuiGraphics graphics, Font font, int x, int y, RcTankEntity tank) {
        // 半透明背景プレート
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

        String batText = String.format("動力  %d%%", batPct);
        graphics.drawString(font, batText, x, y, batColor, true);

        // バッテリーバー
        int barX = x + 50;
        int barY = y + 2;
        int barW = 38;
        int barH = 5;
        graphics.fill(barX, barY, barX + barW, barY + barH, 0x60333333);
        int fillW = (int) (barW * Math.min(1.0f, batRatio));
        graphics.fill(barX, barY, barX + fillW, barY + barH, batColor);

        // 機関 (モーターTier)
        String motorText = String.format("機関  TIER %d", tank.getMotorTier());
        graphics.drawString(font, motorText, x, y + 11, 0xFFE0E0E0, true);

        // 車速
        Vec3 vel = tank.getDeltaMovement();
        double speedKmh = Math.sqrt(vel.x * vel.x + vel.z * vel.z) * 20.0 * 3.6;
        String speedText = String.format("車速  %.1f km/h", speedKmh);
        graphics.drawString(font, speedText, x, y + 22, 0xFFB0B0B0, true);

        // 操作ガイド
        String fireGuide = "Space: 主砲発射";
        graphics.drawString(font, fireGuide, x, y + 33, 0xFFFFCC00, true);
    }

    /**
     * 右下: 受信距離・電波強度・視点モード
     */
    private void renderSignalStatus(GuiGraphics graphics, Font font, int x, int y, RcTankEntity tank) {
        double sx = ClientDroneInputHandler.getStartX();
        double sy = ClientDroneInputHandler.getStartY();
        double sz = ClientDroneInputHandler.getStartZ();
        double dist = Math.sqrt(tank.distanceToSqr(sx, sy, sz));

        double maxRange = DroneEntity.MAX_SIGNAL_RANGE; // 64m
        double warnRange = DroneEntity.WARN_SIGNAL_RANGE;
        double critRange = DroneEntity.CRITICAL_SIGNAL_RANGE;

        double ratio = Math.max(0.0, 1.0 - (dist / maxRange));
        int rssi = (int) (ratio * 100);

        // 半透明背景プレート
        graphics.fill(x - 4, y - 4, x + 96, y + 47, 0x40000000);

        int sigColor;
        String statusText;
        if (dist > maxRange) {
            sigColor = (tank.tickCount % 10 < 5) ? 0xFFFF2222 : 0x50FF2222;
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

        String distText = String.format("距離  %.1f m", dist);
        String rssiText = String.format("電波  %d%%", rssi);

        graphics.drawString(font, distText, x, y, 0xFFE0E0E0, true);
        graphics.drawString(font, rssiText, x, y + 11, sigColor, true);

        // 4本アンテナピクト
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
        graphics.drawString(font, "視点  GUNNER", x, y + 33, 0xFF88CCFF, true);
    }

    /**
     * 上部コンパス（HDG）
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

        String headingText = String.format("HDG %03.0f° %s", normalizedYaw, cardinal);
        int textWidth = font.width(headingText);

        // 上部背景バー
        graphics.fill(cx - 45, topY - 2, cx + 45, topY + 11, 0x40000000);
        graphics.drawString(font, headingText, cx - (textWidth / 2), topY, 0xFF55FF55, true);
    }
}

