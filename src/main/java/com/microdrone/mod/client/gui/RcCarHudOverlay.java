package com.microdrone.mod.client.gui;

import com.microdrone.mod.client.input.ClientDroneInputHandler;
import com.microdrone.mod.entity.RcCarEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * マイクロRCカー専用 クラシックスタイルのドット調インストルメントHUD
 * - 中央: ドット調タコメーター(LEDバー&RPM)、デジタル速度計(km/h)、ギア/ドリフトインジケータ
 * - 左側: ドット調バッテリーメーター(残量バー & %)
 * - 右側: クラシックヘッドライト点灯マーク & 電波強度(RSSI)・距離
 */
public class RcCarHudOverlay {

    private static float smoothRpm = 800.0f;
    private static int blinkTicks = 0;
    public static boolean showPhysicsDebug = false; // 通常プレイでは非表示 (セッティング画面またはF3で表示)

    public static void register() {
        NeoForge.EVENT_BUS.register(new RcCarHudOverlay());
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        // RCカー操縦中のみ描画
        if (!ClientDroneInputHandler.isPiloting() || !ClientDroneInputHandler.isRcCarPiloting()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Entity entity = mc.level.getEntity(ClientDroneInputHandler.getActiveDroneId());
        if (!(entity instanceof RcCarEntity car)) return;

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();

        blinkTicks++;

        // パネル配置: 画面下部中央 (ホットバーの真上・車載/地上視点両対応)
        int panelW = 224;
        int panelH = 46;
        int cx = screenW / 2;
        int panelX = cx - panelW / 2;
        int panelY = screenH - 52; // ホットバーの上

        // 1. クラシックドット調パネル背景フレーム
        renderPanelFrame(graphics, panelX, panelY, panelW, panelH);

        // 2. 左側: バッテリー計器
        renderBatteryGauge(graphics, font, panelX + 8, panelY + 6, car);

        // 3. 中央: タコメーター ＆ スピードメーター ＆ ギア
        renderCenterCluster(graphics, font, cx, panelY + 4, car);

        // 4. 右側: ヘッドライトマーク ＆ 電波強度(RSSI)・距離
        renderRightCluster(graphics, font, panelX + panelW - 58, panelY + 6, car, mc.player);

        // 5. 開発用物理デバッグ表示 (通常非表示、トグルON時のみ左上に表示)
        if (showPhysicsDebug) {
            renderPhysicsDebugOverlay(graphics, font, car);
        }
    }

    /**
     * クラシックスタイルのドット枠パネル背景
     */
    private void renderPanelFrame(GuiGraphics graphics, int x, int y, int w, int h) {
        // パネル半透明ダーク背景
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xD0121418);

        // ドット調外枠 (二重ボーダー & 飾りビス)
        int borderOuter = 0xFF353942;
        int borderInner = 0xFF202328;
        int borderHighlight = 0xFF4A505C;

        // 上下左右の枠線
        graphics.fill(x + 2, y, x + w - 2, y + 1, borderOuter);
        graphics.fill(x + 2, y + h - 1, x + w - 2, y + h, borderOuter);
        graphics.fill(x, y + 2, x + 1, y + h - 2, borderOuter);
        graphics.fill(x + w - 1, y + 2, x + w, y + h - 2, borderOuter);

        // コーナーのカット (面取り風ドット)
        graphics.fill(x + 1, y + 1, x + 2, y + 2, borderOuter);
        graphics.fill(x + w - 2, y + 1, x + w - 1, y + 2, borderOuter);
        graphics.fill(x + 1, y + h - 2, x + 2, y + h - 1, borderOuter);
        graphics.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, borderOuter);

        // 内側ハイライトライン (上部)
        graphics.fill(x + 3, y + 1, x + w - 3, y + 2, borderHighlight);

        // 左右のセクション区切り縦線
        int divX1 = x + 56;
        int divX2 = x + w - 58;
        graphics.fill(divX1, y + 4, divX1 + 1, y + h - 4, borderInner);
        graphics.fill(divX2, y + 4, divX2 + 1, y + h - 4, borderInner);
    }

    /**
     * 左側: バッテリー計器 (ドット調バッテリーアイコン + 5段階セグメント + %)
     */
    private void renderBatteryGauge(GuiGraphics graphics, Font font, int x, int y, RcCarEntity car) {

        boolean hasBat = car.hasBattery();
        int remaining = hasBat ? car.getBatteryRemaining() : 0;
        int max = hasBat ? car.getBatteryMax() : 1;
        float pct = hasBat && max > 0 ? (float) remaining / max * 100.0f : 0.0f;

        // ラベル "BATT"
        graphics.drawString(font, "BATT", x, y, 0xFF8E95A5, false);

        // ドット絵バッテリーアイコン (18x9 px)
        int bx = x;
        int by = y + 10;
        // バッテリー外枠
        graphics.fill(bx, by, bx + 16, by + 9, 0xFF22262E);
        graphics.fill(bx + 1, by + 1, bx + 15, by + 8, 0xFF0D0E11);
        // プラス極の突起
        graphics.fill(bx + 16, by + 2, bx + 18, by + 7, 0xFF22262E);

        // 内部セグメント (4本バー)
        int barCount = (int) Math.ceil((pct / 100.0f) * 4.0f);
        int barColor = (pct > 50) ? 0xFF22C55E : (pct > 20) ? 0xFFF59E0B : 0xFFEF4444;

        if (pct <= 15 && (blinkTicks % 20 < 10)) {
            // ローバッテリー点滅
            barColor = 0xFF550000;
        }

        for (int i = 0; i < 4; i++) {
            if (i < barCount) {
                graphics.fill(bx + 2 + i * 3, by + 2, bx + 4 + i * 3, by + 7, barColor);
            }
        }

        // パーセント表示 (例: "98%")
        String pctStr = hasBat ? String.format("%d%%", (int) pct) : "NO BAT";
        int pctColor = (pct > 20) ? 0xFFE2E8F0 : 0xFFF87171;
        graphics.drawString(font, pctStr, x, by + 12, pctColor, true);
    }

    /**
     * 中央: タコメーター(LEDバー) + デジタルスピードメーター(km/h) + ギア/ドリフト
     */
    private void renderCenterCluster(GuiGraphics graphics, Font font, int cx, int y, RcCarEntity car) {
        float rawSpeed = car.getCurrentSpeed(); // blocks/tick
        float kmh = Math.abs(rawSpeed) * 20.0f * 3.6f;

        // 疑似ギア計算
        int gear;
        if (kmh > 75) gear = 5;
        else if (kmh > 50) gear = 4;
        else if (kmh > 30) gear = 3;
        else if (kmh > 15) gear = 2;
        else gear = 1;

        // 擬似タコメーター回転数計算 (800〜8500 RPM)
        float gearRatio = switch (gear) {
            case 1 -> 4.2f;
            case 2 -> 2.6f;
            case 3 -> 1.7f;
            case 4 -> 1.2f;
            default -> 0.9f;
        };
        float targetRpm = 800.0f + (kmh * gearRatio * 32.0f);
        if (rawSpeed < 0.01f && rawSpeed > -0.01f) {
            targetRpm = 800.0f + (float) Math.sin(blinkTicks * 0.2f) * 60.0f; // アイドリング鼓動
        }
        if (targetRpm > 8500.0f) targetRpm = 8500.0f;

        // スムージング補間
        smoothRpm += (targetRpm - smoothRpm) * 0.25f;

        // --- 1. ドット調タコメーター LEDセグメントバー (16個) ---
        int totalSegments = 16;
        int segW = 4;
        int segH = 5;
        int segGap = 2;
        int barTotalW = totalSegments * segW + (totalSegments - 1) * segGap; // 16*4 + 15*2 = 94px
        int barStartX = cx - barTotalW / 2;
        int barY = y + 2;

        int activeSegments = (int) ((smoothRpm / 8500.0f) * totalSegments);

        for (int i = 0; i < totalSegments; i++) {
            int sx = barStartX + i * (segW + segGap);
            boolean isActive = i < activeSegments;

            // セグメント色グラデーション: 緑(0~9) -> 黄(10~12) -> 赤(13~15: レッドゾーン)
            int segColor;
            if (i >= 13) {
                // レッドゾーン
                segColor = isActive ? 0xFFEF4444 : 0xFF351214;
            } else if (i >= 10) {
                // 高回転イエロー
                segColor = isActive ? 0xFFFBBF24 : 0xFF382D10;
            } else {
                // 通常グリーン
                segColor = isActive ? 0xFF22C55E : 0xFF0F2B1A;
            }

            // レブリミッター点滅 (8200 RPM以上)
            if (isActive && smoothRpm >= 8200 && (blinkTicks % 4 < 2)) {
                segColor = 0xFFFFFFFF; // ピカッと光る
            }

            graphics.fill(sx, barY, sx + segW, barY + segH, segColor);
        }

        // --- 2. デジタル速度計 (中央大文字) ---
        int spdY = barY + 8;
        String spdVal = String.format("%03d", (int) Math.round(kmh));
        int valW = font.width(spdVal);
        String unit = "km/h";

        // メインデジタル数字 (黄色/白でクラシックLCD感)
        graphics.drawString(font, spdVal, cx - valW / 2 - 10, spdY, 0xFFF8FAFC, true);
        // 単位
        graphics.drawString(font, unit, cx + valW / 2 - 6, spdY + 1, 0xFF94A3B8, false);

        // --- 3. ギア ＆ ドリフトインジケータ ---
        int bottomY = spdY + 13;

        // ドリフト判定 (進行方向と機首Yawの差)
        boolean isDrifting = false;
        Vec3 vel = car.getDeltaMovement();
        double hSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (hSpeed > 0.12) {
            float moveYaw = (float) (Mth.atan2(-vel.x, vel.z) * Mth.RAD_TO_DEG);
            float carYaw = car.getYRot();
            float diff = Math.abs(Mth.wrapDegrees(moveYaw - carYaw));
            if (diff > 16.0f && Math.abs(car.getSteerAngle()) > 4.0f) {
                isDrifting = true;
            }
        }

        // ギア文字列: 前進は "D" またはギア段数 "1~5"、後退は "R"、停止は "N"
        String gearStr;
        int gearColor;
        if (rawSpeed < -0.01f) {
            gearStr = "R";
            gearColor = 0xFFEF4444; // 赤
        } else if (rawSpeed < 0.01f) {
            gearStr = "N";
            gearColor = 0xFF94A3B8; // ニュートラル灰
        } else {
            gearStr = String.valueOf(gear);
            gearColor = 0xFF38BDF8; // スカイブルー
        }

        if (isDrifting) {
            // ドリフト中は [ DRIFT ] が激しく点灯！
            String driftText = "DRIFT!";
            int driftColor = (blinkTicks % 6 < 3) ? 0xFFFF3333 : 0xFFFFAA00;
            int dw = font.width(driftText);
            graphics.drawString(font, driftText, cx - dw / 2, bottomY, driftColor, true);
        } else {
            // 通常時: ギア表示 [ D:3 ]
            String gText = "GEAR: " + gearStr;
            int gw = font.width(gText);
            graphics.drawString(font, gText, cx - gw / 2, bottomY, gearColor, true);
        }
    }

    /**
     * 右側: ヘッドライト点灯マーク ＆ 電波強度(RSSI)・プロポ距離
     */
    private void renderRightCluster(GuiGraphics graphics, Font font, int x, int y, RcCarEntity car, Player player) {
        // --- 1. クラシックヘッドライトマーク (ドット絵) ---
        boolean isLightOn = car.isHeadlightOn();
        int hx = x;
        int hy = y + 2;

        // ライトアイコン色: 点灯時はエメラルドグリーン(0xFF10B981)、消灯時は暗いグレー(0xFF262C36)
        int lightColor = isLightOn ? 0xFF10B981 : 0xFF2A303C;

        // ヘッドライト本体 (半円風ドット)
        graphics.fill(hx, hy + 1, hx + 4, hy + 7, lightColor);
        graphics.fill(hx + 1, hy, hx + 3, hy + 8, lightColor);

        // 光線ビーム (3本の平行斜めライン)
        graphics.fill(hx + 6, hy, hx + 9, hy + 1, lightColor);
        graphics.fill(hx + 6, hy + 3, hx + 10, hy + 4, lightColor);
        graphics.fill(hx + 6, hy + 6, hx + 9, hy + 7, lightColor);

        // "LIGHT" ラベル
        int labelColor = isLightOn ? 0xFF34D399 : 0xFF64748B;
        graphics.drawString(font, isLightOn ? "ON" : "OFF", hx + 13, hy, labelColor, false);

        // --- 2. 電波強度 (RSSI) & 距離 ---
        double startX = ClientDroneInputHandler.getStartX();
        double startY = ClientDroneInputHandler.getStartY();
        double startZ = ClientDroneInputHandler.getStartZ();
        double dist = player.distanceTo(car);
        if (!Double.isNaN(startX) && startX != 0) {
            dist = Math.sqrt(car.distanceToSqr(startX, startY, startZ));
        }

        int rx = x;
        int ry = y + 14;

        // 4本のアンテナピクトバー (📶)
        int maxRange = 128; // 最大電波距離(m)
        float signalRatio = Math.max(0.0f, 1.0f - (float)(dist / maxRange));
        int activeBars = (int) Math.ceil(signalRatio * 4.0f);
        if (dist > maxRange) activeBars = 0;

        for (int i = 0; i < 4; i++) {
            int barH = 2 + i * 2; // 2, 4, 6, 8 px
            int bx = rx + i * 3;
            int by = ry + 8 - barH;

            int bColor;
            if (i < activeBars) {
                bColor = (activeBars >= 3) ? 0xFF38BDF8 : (activeBars == 2) ? 0xFFFBBF24 : 0xFFEF4444;
            } else {
                bColor = 0xFF1E2430; // 非アクティブ
            }
            graphics.fill(bx, by, bx + 2, by + barH, bColor);
        }

        // 距離数値 (例: "28m")
        String distStr = String.format("%dm", (int) Math.round(dist));
        int distColor = (dist < 80) ? 0xFFCBD5E1 : (dist < 110) ? 0xFFFCD34D : 0xFFF87171;
        graphics.drawString(font, distStr, rx + 14, ry, distColor, true);
    }

    /**
     * 開発用 物理デバッグテレメトリ表示（仕様書14章準拠）
     */
    private void renderPhysicsDebugOverlay(GuiGraphics graphics, Font font, RcCarEntity car) {
        int x = 8;
        int y = 8;
        int w = 178;
        int h = car.isRealMode() ? 94 : 32;

        graphics.fill(x, y, x + w, y + h, 0xB80E1118);
        graphics.fill(x, y, x + w, y + 1, 0xFF06B6D4);

        String modeText = car.isRealMode() ? "§b§lMODE: REAL (Physics)" : "§a§lMODE: RACE (Assist)";
        graphics.drawString(font, modeText, x + 5, y + 4, 0xFFFFFF, true);

        if (!car.isRealMode()) {
            graphics.drawString(font, String.format("§7SPD: §f%.3f  §7DRIFT: %s",
                    car.getCurrentSpeed(), car.getDriftDirection() != 0 ? "§eACTIVE" : "§8NO"), x + 5, y + 16, 0xFFFFFF, true);
            return;
        }

        var phys = car.getRealPhysics();
        var wt = phys.getWeightTransfer();

        graphics.drawString(font, String.format("§7FWD: §f%.3f  §7LAT: §b%.3f",
                phys.getForwardVelocity(), phys.getLateralVelocity()), x + 5, y + 16, 0xFFFFFF, true);
        graphics.drawString(font, String.format("§7F-GRIP: %s%.0f%%  §7R-GRIP: %s%.0f%%",
                phys.isFrontSlipping() ? "§c" : "§a", phys.getFrontGripUsage() * 100.0f,
                phys.isRearSlipping() ? "§c" : "§a", phys.getRearGripUsage() * 100.0f), x + 5, y + 27, 0xFFFFFF, true);
        graphics.drawString(font, String.format("§7LOAD F/R: §e%.0f%% / %.0f%%",
                wt.getFrontLoadRatio() * 100.0f, wt.getRearLoadRatio() * 100.0f), x + 5, y + 38, 0xFFFFFF, true);
        graphics.drawString(font, String.format("§7LOAD L/R: §e%.0f%% / %.0f%%",
                wt.getLeftLoadRatio() * 100.0f, wt.getRightLoadRatio() * 100.0f), x + 5, y + 49, 0xFFFFFF, true);
        graphics.drawString(font, String.format("§7SLIP: §f%.1f°  §7YAW: §f%.2f°/t",
                phys.getSlipAngleDeg(), phys.getYawRateDeg()), x + 5, y + 60, 0xFFFFFF, true);
        graphics.drawString(font, String.format("§7FRIC: §f%.2f  §7STEER: §f%.1f°",
                phys.getSurfaceFriction(), phys.getSteerAngleDeg()), x + 5, y + 71, 0xFFFFFF, true);
        graphics.drawString(font, "§7DRIFT: " + (phys.isDrifting() ? "§e§lSLIDING!" : "§aGRIPPING"), x + 5, y + 82, 0xFFFFFF, true);
    }
}
