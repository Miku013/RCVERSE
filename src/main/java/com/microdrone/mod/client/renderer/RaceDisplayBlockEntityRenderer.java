package com.microdrone.mod.client.renderer;

import com.microdrone.mod.block.RaceDisplayBlock;
import com.microdrone.mod.block.entity.RaceDisplayBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/**
 * レース電光掲示板 レンダラー
 * 
 * - マルチブロック結合されたLEDモニター表面にリアルタイムレース計時（リーダーボード）を描画
 * - パイロット名、現在ラップ、直前ラップタイム、ベストラップ、セクター区間タイムを表示
 * - ラップ更新時のピカッと光るフラッシュ演出対応
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

        Direction facing = be.getBlockState().getValue(RaceDisplayBlock.FACING);
        int width = be.getMultiblockWidth();
        int height = be.getMultiblockHeight();

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

        // === リーダーボード（タイム順位表）描画 ===
        renderScreenBackground(poseStack, buffer, width, height, be.isFlashing());

        poseStack.pushPose();
        poseStack.translate(0.0, height, 0.001);
        poseStack.scale(1.0f, -1.0f, 1.0f);
        renderLeaderboardText(be, poseStack, buffer, width, height);
        poseStack.popPose();

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
