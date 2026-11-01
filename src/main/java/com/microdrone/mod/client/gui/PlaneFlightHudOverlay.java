package com.microdrone.mod.client.gui;

import com.microdrone.mod.client.input.ClientDroneInputHandler;
import com.microdrone.mod.entity.RcPlaneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * マイクロRCプレーン専用 航空計器HUDオーバーレイ (チェイス視点 ＆ コックピットFPV視点)
 * - 姿勢指示器（人工水平儀 / PFD: ピッチ・ロール追従水平線 ＆ 角度ラダー）
 * - 対気速度計（AIRSPEED: km/h ＆ スピードバー ＆ 低速時STALL警告）
 * - 高度計（ALTITUDE: 海抜・対地高度 ＆ 昇降速度VSI）
 * - スロットル開度バー（THROTTLE 0〜100% ＆ Space/Shiftガイド）
 * - フライトモード表示（[ASSIST] 直感アシスト ⇔ [ACRO] 本格アクロバット ＆ Rキーガイド）
 * - 方位コンパステープ（N/E/S/W ＆ 0〜359°）
 * - バッテリー残量・モーターTier・プロポ電波通信距離
 */
public class PlaneFlightHudOverlay {

    public static void register() {
        NeoForge.EVENT_BUS.register(new PlaneFlightHudOverlay());
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        if (!ClientDroneInputHandler.isPiloting()) return;
        if (!ClientDroneInputHandler.isRcPlanePiloting()) return;

        ClientDroneInputHandler.RcCameraMode mode = ClientDroneInputHandler.getCarCameraMode();
        // チェイス視点 または コックピット視点の時に航空計器HUDを描画
        if (mode != ClientDroneInputHandler.RcCameraMode.CHASE_CAR &&
            mode != ClientDroneInputHandler.RcCameraMode.GUNNER_AIM) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Entity entity = mc.level.getEntity(ClientDroneInputHandler.getActiveDroneId());
        if (!(entity instanceof RcPlaneEntity plane)) return;

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int cx = width / 2;
        int cy = height / 2;

        // 1. 中央: 姿勢指示器 (人工水平儀 / Artificial Horizon)
        renderArtificialHorizon(graphics, font, cx, cy, plane);

        // 2. 左側: 対気速度計 (AIRSPEED Tape & STALL Indicator)
        renderAirspeedIndicator(graphics, font, cx - 110, cy, plane);

        // 3. 右側: 高度計 (ALTITUDE & VSI)
        renderAltitudeIndicator(graphics, font, cx + 110, cy, plane);

        // 4. 下部: スロットルインジケーター (THROTTLE %)
        renderThrottleIndicator(graphics, font, cx, cy + 50, plane);

        // 5. 上部: フライトモード切替表示 ([ASSIST] ⇔ [ACRO]) ＆ 方位コンパス
        renderHeaderStatus(graphics, font, cx, 12, plane);

        // 6. 左下: バッテリー残量 ＆ モーターTier
        renderPlaneStatus(graphics, font, 14, height - 55, plane);

        // 7. 右下: 通信距離 ＆ 電波強度 ＆ カメラモード
        renderSignalStatus(graphics, font, width - 110, height - 55, plane, mode);

        // 8. 右上: 飛行制御・姿勢デバッグテレメトリ (原因探求用)
        renderDebugTelemetry(graphics, font, width - 130, 8, plane);
    }

    /**
     * 右上: リアルタイム姿勢・接地デバッグテレメトリ（がたつき・姿勢巻き戻し原因探求用）
     */
    private void renderDebugTelemetry(GuiGraphics graphics, Font font, int x, int y, RcPlaneEntity plane) {
        int w = 122;
        int h = 48;
        graphics.fill(x - 4, y - 4, x + w, y + h, 0x88000000);
        graphics.renderOutline(x - 4, y - 4, w + 4, h + 4, 0xAA00DDFF);

        boolean ground = plane.onGround();
        boolean taxi = ground && (plane.airspeed < 0.20f) && (Math.abs(plane.getRollAngle()) < 22.0f);

        int gndColor = ground ? 0xFFFF4444 : 0xFF00FF66;
        graphics.drawString(font, "[DBG TELEMETRY]", x, y, 0xFF00DDFF, false);
        graphics.drawString(font, String.format("Gnd:%b  Taxi:%b", ground, taxi), x, y + 10, gndColor, false);
        graphics.drawString(font, String.format("P: %+.1f°  R: %+.1f°", plane.getPitchAngle(), plane.getRollAngle()), x, y + 20, 0xFFFFFFFF, false);
        graphics.drawString(font, String.format("vVel:%+.2f  Spd:%.1f", plane.verticalVelocity, plane.airspeed * 72.0f), x, y + 30, 0xFFCCCCCC, false);
    }

    /**
     * 人工水平儀（機体固定シンボル ＆ ピッチ・ロール追従水平線）
     */
    private void renderArtificialHorizon(GuiGraphics graphics, Font font, int cx, int cy, RcPlaneEntity plane) {
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();

        float pitch = plane.getPitchAngle();
        float roll = plane.getRollAngle();

        // センターピッチオフセット (ピッチ1度あたり1.2ピクセル移動)
        float pitchOffset = pitch * 1.2f;

        // 中央回転 (ロール角: 機体モデルの傾きと完全に同位相で回転)
        poseStack.translate(cx, cy, 0);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-roll));

        // 水平線 (人工水平バー: ロールとピッチに合わせて傾斜・上下)
        int hWidth = 44;
        graphics.fill(-hWidth, (int) pitchOffset, hWidth, (int) pitchOffset + 1, 0xFF00FF66);

        // ピッチラダー (+10°, +20°, -10°, -20°)
        for (int p = -20; p <= 20; p += 10) {
            if (p == 0) continue;
            int ladderY = (int) (pitchOffset - p * 1.2f);
            int lw = (Math.abs(p) == 10) ? 14 : 22;
            graphics.fill(-lw, ladderY, lw, ladderY + 1, 0x8800FF66);
            if (Math.abs(p) == 20) {
                String pText = String.valueOf(p);
                graphics.drawString(font, pText, lw + 2, ladderY - 3, 0xAA00FF66, false);
            }
        }

        poseStack.popPose();

        // 機体固定シンボル（画面中央固定の飛行機マーク: ── · ──）
        graphics.fill(cx - 24, cy, cx - 8, cy + 1, 0xFFFFFF00);
        graphics.fill(cx - 8, cy, cx - 8, cy + 4, 0xFFFFFF00);
        graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFF00); // 中心点
        graphics.fill(cx + 8, cy, cx + 24, cy + 1, 0xFFFFFF00);
        graphics.fill(cx + 8, cy, cx + 8, cy + 4, 0xFFFFFF00);
    }

    /**
     * 左側: 対気速度計
     */
    private void renderAirspeedIndicator(GuiGraphics graphics, Font font, int x, int cy, RcPlaneEntity plane) {
        float speedBlocksPerTick = plane.getAirspeed();
        float speedKmh = speedBlocksPerTick * 20.0f * 3.6f; // km/h 換算

        // 背景
        graphics.fill(x - 25, cy - 35, x + 25, cy + 35, 0x77000000);
        graphics.renderOutline(x - 25, cy - 35, 50, 70, 0xAA00FF66);

        // タイトル
        graphics.drawCenteredString(font, "SPD", x, cy - 31, 0xFFAAAAAA);

        // 速度数値
        String spdStr = String.format("%.0f", speedKmh);
        graphics.drawCenteredString(font, spdStr, x, cy - 10, 0xFFFFFFFF);
        graphics.drawCenteredString(font, "km/h", x, cy + 2, 0xFF888888);

        // スピードバー
        float maxSpeedKmh = 70.0f;
        float ratio = Mth.clamp(speedKmh / maxSpeedKmh, 0.0f, 1.0f);
        int barHeight = (int) (ratio * 50);
        graphics.fill(x + 20, cy + 28 - barHeight, x + 23, cy + 28, 0xFF00FF66);

        // 失速（STALL）警告表示: 空中飛行中で失速判定(isStalling)のとき赤色点滅
        if (!plane.onGround() && plane.isStalling) {
            boolean blink = (plane.tickCount / 5) % 2 == 0;
            if (blink) {
                graphics.fill(x - 24, cy + 18, x + 18, cy + 32, 0xEECC0000);
                graphics.drawCenteredString(font, "! STALL !", x - 3, cy + 21, 0xFFFFFFFF);
            }
        }
    }

    /**
     * 右側: 高度計 ＆ 昇降速度
     */
    private void renderAltitudeIndicator(GuiGraphics graphics, Font font, int x, int cy, RcPlaneEntity plane) {
        double currentY = plane.getY();
        double deltaY = plane.getDeltaMovement().y * 20.0; // m/s 昇降率

        // 背景
        graphics.fill(x - 25, cy - 35, x + 25, cy + 35, 0x77000000);
        graphics.renderOutline(x - 25, cy - 35, 50, 70, 0xAA00FF66);

        // タイトル
        graphics.drawCenteredString(font, "ALT", x, cy - 31, 0xFFAAAAAA);

        // 高度数値
        String altStr = String.format("%.1f", currentY);
        graphics.drawCenteredString(font, altStr, x, cy - 10, 0xFFFFFFFF);
        graphics.drawCenteredString(font, "m", x, cy + 2, 0xFF888888);

        // 昇降率 (VSI)
        int vsiColor = deltaY >= 0 ? 0xFF00FF66 : 0xFFFF5555;
        String vsiStr = String.format("%s%.1f m/s", deltaY >= 0 ? "+" : "", deltaY);
        graphics.drawCenteredString(font, vsiStr, x, cy + 18, vsiColor);
    }

    /**
     * 下部: スロットル開度インジケーター
     */
    private void renderThrottleIndicator(GuiGraphics graphics, Font font, int cx, int y, RcPlaneEntity plane) {
        float throttle = plane.getThrottle();
        int percent = (int) (throttle * 100);

        int barW = 100;
        int barH = 6;
        int bx = cx - barW / 2;

        graphics.fill(bx - 12, y - 4, bx + barW + 12, y + 20, 0x77000000);
        graphics.renderOutline(bx - 1, y - 1, barW + 2, barH + 2, 0xAA00FF66);

        // スロットルバー
        int fillW = (int) (barW * throttle);
        int barColor = throttle > 0.8f ? 0xFFFF6600 : 0xFF00FF66;
        graphics.fill(bx, y, bx + fillW, y + barH, barColor);

        // テキスト表示
        String thrText = String.format("THR: %d%%", percent);
        graphics.drawCenteredString(font, thrText, cx, y + 8, 0xFFFFFFFF);

        // 操作ヒント
        graphics.drawString(font, "[Space] 加速", bx - 45, y - 1, 0x99AAAAAA, false);
        graphics.drawString(font, "[Shift] 減速", bx + barW + 6, y - 1, 0x99AAAAAA, false);
    }

    /**
     * 上部: フライトモード切替表示 ＆ 方位コンパス
     */
    private void renderHeaderStatus(GuiGraphics graphics, Font font, int cx, int y, RcPlaneEntity plane) {
        int mode = plane.getFlightMode();

        // 1. フライトモード表示バッジ
        int badgeW = 170;
        int bx = cx - badgeW / 2;
        graphics.fill(bx, y, bx + badgeW, y + 14, 0x88000000);

        if (mode == 0) {
            // ASSIST (直感アシスト)
            graphics.renderOutline(bx, y, badgeW, 14, 0xFF00FF66);
            graphics.drawCenteredString(font, "MODE: 直感アシスト [ASSIST]", cx, y + 3, 0xFF00FF66);
        } else {
            // ACRO (本格アクロバット)
            graphics.renderOutline(bx, y, badgeW, 14, 0xFFFF3333);
            graphics.drawCenteredString(font, "MODE: 本格アクロバット [ACRO]", cx, y + 3, 0xFFFF5555);
        }

        // 切替キーガイド
        graphics.drawCenteredString(font, "[R] キーで操縦モード切替", cx, y + 18, 0xCCDDDDDD);

        // 2. 上部方位コンパス (Minecraft Yaw -> 航空方位角 Heading への変換)
        // Minecraft: 0=南, 90=西, 180=北, 270=東
        // 航空方位: 0=北, 90=東, 180=南, 270=西
        float heading = Mth.wrapDegrees(plane.getYRot() + 180.0f);
        if (heading < 0) heading += 360.0f;
        int compassDeg = (int) heading;
        String dir = getHeadingName(compassDeg);
        String compStr = String.format("HDG %03d° %s", compassDeg, dir);
        graphics.drawCenteredString(font, compStr, cx, y + 29, 0xFF88DDFF);
    }

    private String getHeadingName(int deg) {
        if (deg >= 338 || deg < 23) return "N (北)";
        if (deg < 68) return "NE (北東)";
        if (deg < 113) return "E (東)";
        if (deg < 158) return "SE (南東)";
        if (deg < 203) return "S (南)";
        if (deg < 248) return "SW (南西)";
        if (deg < 293) return "W (西)";
        return "NW (北西)";
    }

    /**
     * 左下: バッテリー残量 ＆ モーターTier
     */
    private void renderPlaneStatus(GuiGraphics graphics, Font font, int x, int y, RcPlaneEntity plane) {
        int w = 90;
        int h = 38;
        graphics.fill(x - 4, y - 4, x + w, y + h, 0x77000000);
        graphics.renderOutline(x - 4, y - 4, w + 4, h + 4, 0xAA00FF66);

        // バッテリー
        int rem = plane.getBatteryRemaining();
        int max = Math.max(1, plane.getBatteryMax());
        float batRatio = Mth.clamp((float) rem / max, 0.0f, 1.0f);
        int batColor = batRatio < 0.2f ? 0xFFFF3333 : (batRatio < 0.5f ? 0xFFFFAA00 : 0xFF00FF66);

        graphics.drawString(font, String.format("BAT: %d%%", (int) (batRatio * 100)), x, y, batColor, false);
        // バッテリーバー
        graphics.fill(x, y + 10, x + 65, y + 13, 0xFF333333);
        graphics.fill(x, y + 10, x + (int) (65 * batRatio), y + 13, batColor);

        // モーターTier
        int tier = plane.getMotorTier();
        String tierName = switch (tier) {
            case 2 -> "Sport (T2)";
            case 3 -> "Hi-Speed (T3)";
            case 4 -> "Racing (T4)";
            default -> "Standard (T1)";
        };
        graphics.drawString(font, "ENG: " + tierName, x, y + 18, 0xFFDDDDDD, false);
    }

    /**
     * 右下: 通信距離 ＆ 電波強度 ＆ カメラモード
     */
    private void renderSignalStatus(GuiGraphics graphics, Font font, int x, int y, RcPlaneEntity plane, ClientDroneInputHandler.RcCameraMode mode) {
        int w = 95;
        int h = 38;
        graphics.fill(x - 4, y - 4, x + w, y + h, 0x77000000);
        graphics.renderOutline(x - 4, y - 4, w + 4, h + 4, 0xAA00FF66);

        double sx = ClientDroneInputHandler.getStartX();
        double sy = ClientDroneInputHandler.getStartY();
        double sz = ClientDroneInputHandler.getStartZ();
        double dist = Math.sqrt(plane.distanceToSqr(sx, sy, sz));

        // 距離
        graphics.drawString(font, String.format("DIST: %.1fm", dist), x, y, 0xFFFFFFFF, false);

        // 電波強度
        float signalRatio = (float) Math.max(0.0, 1.0 - (dist / 250.0));
        String sigBars = signalRatio > 0.75f ? "llll" : signalRatio > 0.5f ? "lll." : signalRatio > 0.25f ? "ll.." : "l...";
        int sigColor = signalRatio > 0.5f ? 0xFF00FF66 : signalRatio > 0.25f ? 0xFFFFAA00 : 0xFFFF3333;
        graphics.drawString(font, "SIG: [" + sigBars + "]", x, y + 10, sigColor, false);

        // カメラモード
        String camName = (mode == ClientDroneInputHandler.RcCameraMode.GUNNER_AIM) ? "COCKPIT (FPV)" : "CHASE (追従)";
        graphics.drawString(font, "CAM: " + camName, x, y + 20, 0xFFAAAAAA, false);
    }
}

