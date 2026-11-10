package com.microdrone.mod.client.renderer;

import com.microdrone.mod.block.RaceDisplayBlock;
import com.microdrone.mod.block.entity.RaceDisplayBlockEntity;
import com.microdrone.mod.client.cctv.CctvRenderPipeline;
import com.microdrone.mod.entity.RcCarEntity;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

/**
 * レース電光掲示板 ＆ 256x144 (16:9) / 30fps CCTV ライブモニター レンダラー
 * 
 * - プレイヤーが見ている時だけ 30fps で FBO オフスクリーン描画を実行 (カリング対応)
 * - モニター表面に 16:9 比率でライブ中継映像テクスチャを投影
 * - 画面枠に LIVE 赤ランプ、カメラ名、リアルタイム速度などのTVテロップをオーバーレイ
 * - BufferUploader.drawWithShader を一切呼ばず、Minecraft標準の MultiBufferSource で完全安全描画
 */
public class RaceDisplayBlockEntityRenderer implements BlockEntityRenderer<RaceDisplayBlockEntity> {
    private final Font font;

    public RaceDisplayBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(RaceDisplayBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        // マスターブロックでのみスクリーン全体を描画（重複防止）
        if (!be.isMaster()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Direction facing = be.getBlockState().getValue(RaceDisplayBlock.FACING);
        int width = be.getMultiblockWidth();
        int height = be.getMultiblockHeight();

        // 1. 【カリング ＆ 30fps FBOレンダリング実行】
        // プレイヤーがディスプレイを見ている時だけ 30fps で仮想カメラシーンを描画
        if (be.getDisplayMode() == RaceDisplayBlockEntity.DisplayMode.CCTV_LIVE) {
            CctvRenderPipeline.renderFrameIfVisible(mc, be, partialTick);
        }

        poseStack.pushPose();

        // 向き（FACING）に応じて、正面左下表面 (0,0) をローカル原点に設定
        switch (facing) {
            case SOUTH -> poseStack.translate(0.0, 0.0, 1.002);
            case NORTH -> {
                poseStack.translate(1.0, 0.0, -0.002);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
            }
            case EAST -> {
                poseStack.translate(1.002, 0.0, 1.0);
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
            }
            case WEST -> {
                poseStack.translate(-0.002, 0.0, 0.0);
                poseStack.mulPose(Axis.YP.rotationDegrees(-90.0f));
            }
        }

        if (be.getDisplayMode() == RaceDisplayBlockEntity.DisplayMode.CCTV_LIVE) {
            // === 2. CCTV リアルタイム中継画面 (256x144 / 16:9) ===
            renderCctvScreen(be, poseStack, buffer, width, height, mc);
        } else {
            // === 3. リーダーボード（タイム順位表）===
            renderScreenBackground(poseStack, buffer, width, height, be.isFlashing());

            poseStack.pushPose();
            poseStack.translate(0.0, height, 0.001);
            poseStack.scale(1.0f, -1.0f, 1.0f);
            renderLeaderboardText(be, poseStack, buffer, width, height);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    /**
     * CCTVライブ中継画面の描画 (FBOテクスチャ投影 + 16:9レターボックス + TVテロップ)
     */
    private void renderCctvScreen(RaceDisplayBlockEntity be, PoseStack poseStack, MultiBufferSource buffer, int w, int h, Minecraft mc) {
        // 1. 背景のダークLED枠
        renderScreenBackground(poseStack, buffer, w, h, false);

        int targetCarId = CctvRenderPipeline.getTargetCarId();
        Entity targetEntity = (mc.level != null && targetCarId >= 0) ? mc.level.getEntity(targetCarId) : null;
        RcCarEntity car = (targetEntity instanceof RcCarEntity c && c.isAlive()) ? c : null;

        // 2. 16:9 アスペクト比での画面領域計算
        float screenAspect = (float) w / (float) h;
        float targetAspect = 16.0f / 9.0f;
        float drawW = w - 0.04f;
        float drawH = h - 0.04f;
        float offsetX = 0.02f;
        float offsetY = 0.02f;

        if (screenAspect > targetAspect) {
            drawW = drawH * targetAspect;
            offsetX = (w - drawW) / 2.0f;
        } else {
            drawH = drawW / targetAspect;
            offsetY = (h - drawH) / 2.0f;
        }

        // 3. FBO ライブカメラ映像テクスチャの描画
        if (car != null) {
            renderFboTextureQuad(poseStack, buffer, offsetX, offsetY, drawW, drawH);
        } else {
            // 車両がいない時は待機用 SMPTEカラーバー
            renderStandbyColorBars(poseStack, buffer, offsetX, offsetY, drawW, drawH);
        }

        // 4. TV中継テロップ (OSD) オーバーレイ描画
        renderCctvOsdOverlay(poseStack, buffer, w, h, offsetX, offsetY, drawW, drawH, car, mc);
    }

    /**
     * FBO のカラーテクスチャを 16:9 クワッドへ Minecraft 標準 MultiBufferSource で安全にマッピング
     */
    private void renderFboTextureQuad(PoseStack poseStack, MultiBufferSource buffer, float x, float y, float w, float h) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(CctvRenderPipeline.CCTV_TEXTURE_LOCATION));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();

        // FBOテクスチャは上下反転 (V座標: 0=上, 1=下)
        float u0 = 0.0f, v0 = 1.0f;
        float u1 = 1.0f, v1 = 0.0f;

        consumer.addVertex(mat, x, y, 0.003f).setColor(255, 255, 255, 255).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f);
        consumer.addVertex(mat, x + w, y, 0.003f).setColor(255, 255, 255, 255).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f);
        consumer.addVertex(mat, x + w, y + h, 0.003f).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f);
        consumer.addVertex(mat, x, y + h, 0.003f).setColor(255, 255, 255, 255).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f);
    }

    /**
     * レース中継 TVテロップ (OSD) の描画
     */
    private void renderCctvOsdOverlay(PoseStack poseStack, MultiBufferSource buffer, int fullW, int fullH, float x, float y, float w, float h, RcCarEntity car, Minecraft mc) {
        poseStack.pushPose();
        poseStack.translate(0.0, fullH, 0.005);
        poseStack.scale(1.0f, -1.0f, 1.0f);

        float textScale = 0.0075f * Math.min(fullW, fullH);
        poseStack.scale(textScale, textScale, textScale);

        float screenWUnits = (fullW / textScale);
        float screenHUnits = (fullH / textScale);

        if (car == null) {
            String msg = "§e§l[ STANDBY - SEARCHING RC VEHICLES ]";
            String sub = "§7256x144 (16:9) / 30fps CCTV ACTIVE";
            int tw1 = font.width(msg);
            int tw2 = font.width(sub);
            font.drawInBatch(msg, (screenWUnits - tw1) / 2.0f, screenHUnits * 0.45f, 0xFFFBBF24, true, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            font.drawInBatch(sub, (screenWUnits - tw2) / 2.0f, screenHUnits * 0.45f + 13, 0xFFCBD5E1, true, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        } else {
            // 左上: LIVE 🔴 + カメラ名
            String camLabel = CctvRenderPipeline.getActiveCameraLabel();
            String liveText = "§c§l🔴 LIVE §f" + camLabel;
            font.drawInBatch(liveText, 6, 6, 0xFFFFFFFF, true, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);

            // 右上: REC + タイムコード
            if (mc.level != null) {
                long time = mc.level.getGameTime();
                String recText = String.format("§eREC §f%02d:%02d:%02d", (time / 1200) % 60, (time / 20) % 60, (time % 20) * 5);
                int rw = font.width(recText);
                font.drawInBatch(recText, screenWUnits - rw - 6, 6, 0xFFFFFFFF, true, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            }

            // 左下: 車両名
            String variant = car.getVariantId();
            String carName = "bicycle".equalsIgnoreCase(variant) ? "§6VILLAGER BICYCLE" : "86".equalsIgnoreCase(variant) ? "§bAE86 SPRINTER TRUENO" : "§cRC SPORTS CAR";
            font.drawInBatch("§7CAR: " + carName, 6, screenHUnits - 16, 0xFFFFFFFF, true, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);

            // 右下: リアルタイム速度
            float spdKmh = Math.abs(car.getCurrentSpeed()) * 72.0f;
            String spdText = String.format("§7SPD: §a%03d §7km/h", (int) Math.round(spdKmh));
            if (car.isHeadlightOn()) {
                spdText += " §a[LIGHT]";
            }
            int sw = font.width(spdText);
            font.drawInBatch(spdText, screenWUnits - sw - 6, screenHUnits - 16, 0xFFFFFFFF, true, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }

        poseStack.popPose();
    }

    /**
     * 暗いLEDスクリーン背景クワッドの描画
     */
    private void renderScreenBackground(PoseStack poseStack, MultiBufferSource buffer, float w, float h, boolean flash) {
        VertexConsumer builder = buffer.getBuffer(RenderType.gui());
        Matrix4f matrix = poseStack.last().pose();

        int r = flash ? 35 : 12;
        int g = flash ? 45 : 15;
        int b = flash ? 55 : 20;
        int a = 250;

        builder.addVertex(matrix, 0.02f, 0.02f, 0.0f).setColor(r, g, b, a);
        builder.addVertex(matrix, w - 0.02f, 0.02f, 0.0f).setColor(r, g, b, a);
        builder.addVertex(matrix, w - 0.02f, h - 0.02f, 0.0f).setColor(r, g, b, a);
        builder.addVertex(matrix, 0.02f, h - 0.02f, 0.0f).setColor(r, g, b, a);
    }

    /**
     * 待機用 SMPTEカラーバー (16:9 領域内)
     */
    private void renderStandbyColorBars(PoseStack poseStack, MultiBufferSource buffer, float x, float y, float w, float h) {
        VertexConsumer builder = buffer.getBuffer(RenderType.gui());
        Matrix4f matrix = poseStack.last().pose();

        int[][] colors = {
                {220, 220, 220}, {220, 220, 0}, {0, 220, 220}, {0, 200, 0},
                {200, 0, 200}, {220, 0, 0}, {0, 0, 200}
        };

        float barW = w / 7.0f;
        for (int i = 0; i < 7; i++) {
            float x1 = x + i * barW;
            float x2 = x + (i + 1) * barW;
            int cr = colors[i][0];
            int cg = colors[i][1];
            int cb = colors[i][2];

            builder.addVertex(matrix, x1, y, 0.002f).setColor(cr, cg, cb, 240);
            builder.addVertex(matrix, x2, y, 0.002f).setColor(cr, cg, cb, 240);
            builder.addVertex(matrix, x2, y + h, 0.002f).setColor(cr, cg, cb, 240);
            builder.addVertex(matrix, x1, y + h, 0.002f).setColor(cr, cg, cb, 240);
        }
    }

    /**
     * 電光掲示板のテキスト描画 (リーダーボード)
     */
    private void renderLeaderboardText(RaceDisplayBlockEntity be, PoseStack poseStack, MultiBufferSource buffer, int w, int h) {
        float baseScale = 0.0085f * Math.min(w, h);
        if (w == 1 && h == 1) {
            baseScale = 0.0075f;
        }

        poseStack.pushPose();
        poseStack.translate(0.05f * w, 0.05f * h, 0.0f);
        poseStack.scale(baseScale, baseScale, baseScale);

        String title = "§6§l🏁 RACE LEADERBOARD";
        font.drawInBatch(title, 0, 0, 0xFFFFAA00, false, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);

        String pilot = "§fPILOT: §e" + be.getPilotName();
        font.drawInBatch(pilot, 0, 13, 0xFFFFFFFF, false, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);

        String lapText = be.getCurrentLap() > 0 ? "§bLAP: §f" + be.getCurrentLap() : "§7LAP: --";
        font.drawInBatch(lapText, 0, 26, 0xFF55FFFF, false, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);

        String lastTimeText = be.getLastLapTimeMs() > 0 ? "§aTIME: §l" + formatTime(be.getLastLapTimeMs()) : "§7TIME: --'--\"---";
        font.drawInBatch(lastTimeText, 0, 40, 0xFF55FF55, false, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);

        String bestTimeText = be.getBestLapTimeMs() > 0 ? "§dBEST: §l" + formatTime(be.getBestLapTimeMs()) : "§7BEST: --'--\"---";
        font.drawInBatch(bestTimeText, 0, 54, 0xFFFF55FF, false, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);

        if (h >= 2 && be.getSplitTimeMs() > 0) {
            String splitText = "§3[CP " + be.getSplitCheckpoint() + "] §b" + formatTime(be.getSplitTimeMs());
            font.drawInBatch(splitText, 0, 68, 0xFF00AAAA, false, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }

        poseStack.popPose();
    }

    private String formatTime(long ms) {
        long minutes = (ms / 60000);
        long seconds = (ms % 60000) / 1000;
        long millis = ms % 1000;
        return String.format("%02d'%02d\"%03d", minutes, seconds, millis);
    }
}
