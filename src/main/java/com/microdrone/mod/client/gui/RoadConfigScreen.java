package com.microdrone.mod.client.gui;

import com.microdrone.mod.block.road.RoadSplineData;
import com.microdrone.mod.block.road.RoadStartBlockEntity;
import com.microdrone.mod.network.RoadConfigPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 道路・ヘアピンカーブ生成設定画面 (GUI)
 */
public class RoadConfigScreen extends Screen {

    private final BlockPos startPos;
    private RoadStartBlockEntity startBe;

    // UIパラメータ
    private float roadWidth;
    private float maxBankAngle;
    private float controlPointDist;
    private int turnDirection;
    private boolean isPreview;
    private boolean isGenerated;

    private Button turnDirButton;
    private Button previewButton;

    public RoadConfigScreen(BlockPos startPos) {
        super(Component.translatable("gui.microdrone.road_config.title"));
        this.startPos = startPos;
    }

    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new RoadConfigScreen(pos));
    }

    @Override
    protected void init() {
        super.init();

        if (this.minecraft != null && this.minecraft.level != null) {
            if (this.minecraft.level.getBlockEntity(startPos) instanceof RoadStartBlockEntity be) {
                this.startBe = be;
                RoadSplineData data = be.getSplineData();
                this.roadWidth = data.getRoadWidth();
                this.maxBankAngle = data.getMaxBankAngle();
                this.controlPointDist = data.getControlPointDistance();
                this.turnDirection = data.getTurnDirection();
                this.isPreview = data.isPreview();
                this.isGenerated = data.isGenerated();
            }
        }

        int centerX = this.width / 2;
        int startY = 45;
        int buttonWidth = 200;
        int rowHeight = 24;

        // 1. 道幅スライダー (4.0m 〜 7.0m)
        this.addRenderableWidget(new AbstractSliderButton(centerX - 100, startY, buttonWidth, 20,
                Component.literal("道幅: " + String.format("%.1f", roadWidth) + "m"),
                (roadWidth - RoadSplineData.MIN_ROAD_WIDTH) / (RoadSplineData.MAX_ROAD_WIDTH - RoadSplineData.MIN_ROAD_WIDTH)) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal("道幅: " + String.format("%.1f", roadWidth) + "m"));
            }

            @Override
            protected void applyValue() {
                roadWidth = RoadSplineData.MIN_ROAD_WIDTH + (float) this.value * (RoadSplineData.MAX_ROAD_WIDTH - RoadSplineData.MIN_ROAD_WIDTH);
                roadWidth = Math.round(roadWidth * 10.0f) / 10.0f;
                sendUpdate(0);
            }
        });

        // 2. 最大バンク角スライダー (0° 〜 45°)
        this.addRenderableWidget(new AbstractSliderButton(centerX - 100, startY + rowHeight, buttonWidth, 20,
                Component.literal("最大バンク角: " + String.format("%.0f", maxBankAngle) + "°"),
                maxBankAngle / RoadSplineData.MAX_MAX_BANK) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal("最大バンク角: " + String.format("%.0f", maxBankAngle) + "°"));
            }

            @Override
            protected void applyValue() {
                maxBankAngle = (float) this.value * RoadSplineData.MAX_MAX_BANK;
                maxBankAngle = Math.round(maxBankAngle);
                sendUpdate(0);
            }
        });

        // 3. 曲線半径・制御点距離スライダー (4.0m 〜 40.0m)
        this.addRenderableWidget(new AbstractSliderButton(centerX - 100, startY + rowHeight * 2, buttonWidth, 20,
                Component.literal("曲線半径 (制御点): " + String.format("%.1f", controlPointDist) + "m"),
                (controlPointDist - 4.0f) / 36.0f) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal("曲線半径 (制御点): " + String.format("%.1f", controlPointDist) + "m"));
            }

            @Override
            protected void applyValue() {
                controlPointDist = 4.0f + (float) this.value * 36.0f;
                controlPointDist = Math.round(controlPointDist * 10.0f) / 10.0f;
                sendUpdate(0);
            }
        });

        // 4. 曲がる方向切り替えボタン
        turnDirButton = Button.builder(getTurnDirText(), btn -> {
            turnDirection = (turnDirection + 1) % 3;
            btn.setMessage(getTurnDirText());
            sendUpdate(0);
        }).bounds(centerX - 100, startY + rowHeight * 3, buttonWidth, 20).build();
        this.addRenderableWidget(turnDirButton);

        // 5. 終点ブロック再探索ボタン
        this.addRenderableWidget(Button.builder(Component.literal("終点ブロックを再探索 (半径32m)"), btn -> {
            sendUpdate(1);
        }).bounds(centerX - 100, startY + rowHeight * 4, buttonWidth, 20).build());

        // 6. プレビュートグル
        previewButton = Button.builder(getPreviewText(), btn -> {
            isPreview = !isPreview;
            btn.setMessage(getPreviewText());
            sendUpdate(0);
        }).bounds(centerX - 100, startY + rowHeight * 5, buttonWidth, 20).build();
        this.addRenderableWidget(previewButton);

        // 7. アクションボタン: 確定生成 (左) & 削除 (右)
        this.addRenderableWidget(Button.builder(Component.literal("✔ 道路を確定生成"), btn -> {
            isGenerated = true;
            isPreview = false;
            sendUpdate(2);
            this.onClose();
        }).bounds(centerX - 100, startY + rowHeight * 6 + 4, 98, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("✖ 道路を削除"), btn -> {
            isGenerated = false;
            isPreview = true;
            sendUpdate(3);
            this.onClose();
        }).bounds(centerX + 2, startY + rowHeight * 6 + 4, 98, 20).build());

        // 8. 閉じるボタン
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> {
            this.onClose();
        }).bounds(centerX - 100, startY + rowHeight * 7 + 10, buttonWidth, 20).build());
    }

    private Component getTurnDirText() {
        return switch (turnDirection) {
            case 1 -> Component.literal("曲がる方向: 左カーブ (LEFT)");
            case 2 -> Component.literal("曲がる方向: 右カーブ (RIGHT)");
            default -> Component.literal("曲がる方向: 自動判定 (AUTO)");
        };
    }

    private Component getPreviewText() {
        return isPreview ? Component.literal("プレビュー表示: ON (半透明)") : Component.literal("プレビュー表示: OFF");
    }

    private void sendUpdate(int action) {
        if (startBe != null) {
            var data = startBe.getSplineData();
            data.setRoadWidth(roadWidth);
            data.setMaxBankAngle(maxBankAngle);
            data.setTurnDirection(turnDirection);
            data.setControlPointDistance(controlPointDist);
            data.setPreview(isPreview);
            data.setGenerated(isGenerated);
        }
        RoadConfigPayload payload = new RoadConfigPayload(
                startPos,
                roadWidth,
                maxBankAngle,
                turnDirection,
                controlPointDist,
                32,
                isPreview,
                isGenerated,
                action
        );
        PacketDistributor.sendToServer(payload);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, centerX, 12, 0xFFFFA0);

        // リンク状態の表示
        String linkStatus;
        int statusColor;
        if (startBe != null && startBe.getLinkedEndPos() != null) {
            BlockPos endPos = startBe.getLinkedEndPos();
            linkStatus = "接続先 終点: X=" + endPos.getX() + ", Y=" + endPos.getY() + ", Z=" + endPos.getZ();
            statusColor = 0x55FF55; // 緑
        } else {
            linkStatus = "終点ブロックが未接続です (周囲に設置して再探索を押してください)";
            statusColor = 0xFF5555; // 赤
        }
        graphics.drawCenteredString(this.font, Component.literal(linkStatus), centerX, 28, statusColor);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

