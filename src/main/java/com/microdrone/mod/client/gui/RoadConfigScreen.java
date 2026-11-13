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
    private RoadSplineData.RoadMode roadMode = RoadSplineData.RoadMode.ROAD;
    private float roadWidth;
    private float maxBankAngle;
    private float controlPointDist;
    private int segments = 64;
    private boolean arcFitMode = true;
    private float loopHeight = RoadSplineData.DEFAULT_LOOP_HEIGHT;
    private float loopBoostMultiplier = RoadSplineData.DEFAULT_LOOP_BOOST;
    private int turnDirection;
    private boolean isPreview;
    private boolean isGenerated;

    private Button roadModeButton;
    private Button arcFitButton;
    private Button turnDirButton;
    private Button previewButton;
    private AbstractSliderButton controlPointSlider;
    private AbstractSliderButton loopHeightSlider;
    private AbstractSliderButton loopBoostSlider;

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
                this.roadMode = data.getRoadMode();
                this.roadWidth = data.getRoadWidth();
                this.maxBankAngle = data.getMaxBankAngle();
                this.controlPointDist = data.getControlPointDistance();
                this.segments = Math.max(16, Math.min(128, data.getSegments()));
                this.arcFitMode = data.isArcFitMode();
                this.loopHeight = data.getLoopHeight();
                this.loopBoostMultiplier = data.getLoopBoostMultiplier();
                this.turnDirection = data.getTurnDirection();
                this.isPreview = data.isPreview();
                this.isGenerated = data.isGenerated();
            }
        }

        int centerX = this.width / 2;
        int startY = 29;
        int buttonWidth = 220;
        int halfWidth = 108;
        int rowHeight = 19;

        // Row 0: 生成モードトグル (通常道路 / ジャンプ台 / 360°ループ)
        roadModeButton = Button.builder(getRoadModeText(), btn -> {
            roadMode = roadMode.next();
            btn.setMessage(getRoadModeText());
            updateLoopHeightSliderLabel();
            sendUpdate(0);
        }).bounds(centerX - 110, startY, buttonWidth, 18).build();
        this.addRenderableWidget(roadModeButton);

        // Row 1: 道幅スライダー (4.0m 〜 9.0m)
        this.addRenderableWidget(new AbstractSliderButton(centerX - 110, startY + rowHeight, buttonWidth, 18,
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

        // Row 2: 最大バンク角スライダー (0° 〜 45°)
        this.addRenderableWidget(new AbstractSliderButton(centerX - 110, startY + rowHeight * 2, buttonWidth, 18,
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

        // Row 3: 曲線形状モードトグル (真円フィット ⇔ 手動ベジェ)
        arcFitButton = Button.builder(getArcFitText(), btn -> {
            arcFitMode = !arcFitMode;
            btn.setMessage(getArcFitText());
            updateControlPointSliderLabel();
            sendUpdate(0);
        }).bounds(centerX - 110, startY + rowHeight * 3, buttonWidth, 18).build();
        this.addRenderableWidget(arcFitButton);

        // Row 4: 曲線分割数スライダー (16 〜 128, 滑らかさ向上)
        this.addRenderableWidget(new AbstractSliderButton(centerX - 110, startY + rowHeight * 4, buttonWidth, 18,
                Component.literal("曲線分割数 (滑らかさ): " + segments),
                (segments - 16.0) / 112.0) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal("曲線分割数 (滑らかさ): " + segments));
            }

            @Override
            protected void applyValue() {
                int raw = (int) Math.round(16.0 + this.value * 112.0);
                // 8刻みで綺麗にスナップ
                segments = Math.max(16, Math.min(128, ((raw + 4) / 8) * 8));
                sendUpdate(0);
            }
        });

        // Row 5: 曲線半径・制御点距離スライダー (4.0m 〜 40.0m, 手動ベジェ用)
        controlPointSlider = new AbstractSliderButton(centerX - 110, startY + rowHeight * 5, buttonWidth, 18,
                getControlPointLabel(),
                (controlPointDist - 4.0f) / 36.0f) {
            @Override
            protected void updateMessage() {
                setMessage(getControlPointLabel());
            }

            @Override
            protected void applyValue() {
                controlPointDist = 4.0f + (float) this.value * 36.0f;
                controlPointDist = Math.round(controlPointDist * 10.0f) / 10.0f;
                sendUpdate(0);
            }
        };
        this.addRenderableWidget(controlPointSlider);

        // Row 6: ループ高さスライダー (左) ＆ ループ加速倍率スライダー (右)
        loopHeightSlider = new AbstractSliderButton(centerX - 110, startY + rowHeight * 6, halfWidth, 18,
                getLoopHeightLabel(),
                (loopHeight - RoadSplineData.MIN_LOOP_HEIGHT) / (RoadSplineData.MAX_LOOP_HEIGHT - RoadSplineData.MIN_LOOP_HEIGHT)) {
            @Override
            protected void updateMessage() {
                setMessage(getLoopHeightLabel());
            }

            @Override
            protected void applyValue() {
                float raw = RoadSplineData.MIN_LOOP_HEIGHT + (float) this.value * (RoadSplineData.MAX_LOOP_HEIGHT - RoadSplineData.MIN_LOOP_HEIGHT);
                // 0.5m刻みでスナップ
                loopHeight = Math.round(raw * 2.0f) / 2.0f;
                sendUpdate(0);
            }
        };
        this.addRenderableWidget(loopHeightSlider);

        loopBoostSlider = new AbstractSliderButton(centerX + 2, startY + rowHeight * 6, halfWidth, 18,
                getLoopBoostLabel(),
                (loopBoostMultiplier - RoadSplineData.MIN_LOOP_BOOST) / (RoadSplineData.MAX_LOOP_BOOST - RoadSplineData.MIN_LOOP_BOOST)) {
            @Override
            protected void updateMessage() {
                setMessage(getLoopBoostLabel());
            }

            @Override
            protected void applyValue() {
                float raw = RoadSplineData.MIN_LOOP_BOOST + (float) this.value * (RoadSplineData.MAX_LOOP_BOOST - RoadSplineData.MIN_LOOP_BOOST);
                // 0.1x刻みでスナップ
                loopBoostMultiplier = Math.round(raw * 10.0f) / 10.0f;
                sendUpdate(0);
            }
        };
        this.addRenderableWidget(loopBoostSlider);

        // Row 7: 曲がる方向切り替え (左) ＆ プレビュートグル (右)
        turnDirButton = Button.builder(getTurnDirText(), btn -> {
            turnDirection = (turnDirection + 1) % 3;
            btn.setMessage(getTurnDirText());
            sendUpdate(0);
        }).bounds(centerX - 110, startY + rowHeight * 7, halfWidth, 18).build();
        this.addRenderableWidget(turnDirButton);

        previewButton = Button.builder(getPreviewText(), btn -> {
            isPreview = !isPreview;
            btn.setMessage(getPreviewText());
            sendUpdate(0);
        }).bounds(centerX + 2, startY + rowHeight * 7, halfWidth, 18).build();
        this.addRenderableWidget(previewButton);

        // Row 8: 終点ブロック再探索ボタン
        this.addRenderableWidget(Button.builder(Component.literal("終点ブロックを再探索 (半径32m)"), btn -> {
            sendUpdate(1);
        }).bounds(centerX - 110, startY + rowHeight * 8, buttonWidth, 18).build());

        // Row 9: アクションボタン: 確定生成 (左) & 削除 (右)
        this.addRenderableWidget(Button.builder(Component.literal("✔ 確定生成"), btn -> {
            isGenerated = true;
            isPreview = false;
            sendUpdate(2);
            this.onClose();
        }).bounds(centerX - 110, startY + rowHeight * 9 + 1, halfWidth, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("✖ 削除"), btn -> {
            isGenerated = false;
            isPreview = true;
            sendUpdate(3);
            this.onClose();
        }).bounds(centerX + 2, startY + rowHeight * 9 + 1, halfWidth, 18).build());

        // Row 10: 閉じるボタン
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> {
            this.onClose();
        }).bounds(centerX - 110, startY + rowHeight * 10 + 2, buttonWidth, 18).build());
    }

    private Component getRoadModeText() {
        return switch (roadMode) {
            case JUMP_RAMP -> Component.literal("生成モード: §6" + roadMode.getDisplayName() + " (JUMP)");
            case LOOP_360 -> Component.literal("生成モード: §d" + roadMode.getDisplayName() + " (LOOP)");
            default -> Component.literal("生成モード: §a" + roadMode.getDisplayName() + " (ROAD)");
        };
    }

    private Component getArcFitText() {
        return arcFitMode
                ? Component.literal("曲線形状: §b真円フィット (ARC)")
                : Component.literal("曲線形状: §e手動ベジェ (BEZIER)");
    }

    private Component getControlPointLabel() {
        String suffix = arcFitMode ? " §7(手動時有効)" : "";
        return Component.literal("曲線半径 (制御点): " + String.format("%.1f", controlPointDist) + "m" + suffix);
    }

    private void updateControlPointSliderLabel() {
        if (controlPointSlider != null) {
            controlPointSlider.setMessage(getControlPointLabel());
        }
    }

    private Component getLoopHeightLabel() {
        String prefix = (roadMode == RoadSplineData.RoadMode.LOOP_360) ? "" : "§7";
        return Component.literal(prefix + "高さ: " + String.format("%.1f", loopHeight) + "m");
    }

    private Component getLoopBoostLabel() {
        String prefix = (roadMode == RoadSplineData.RoadMode.LOOP_360) ? "" : "§7";
        return Component.literal(prefix + "加速倍率: " + String.format("%.1f", loopBoostMultiplier) + "x");
    }

    private void updateLoopHeightSliderLabel() {
        if (loopHeightSlider != null) {
            loopHeightSlider.setMessage(getLoopHeightLabel());
        }
        if (loopBoostSlider != null) {
            loopBoostSlider.setMessage(getLoopBoostLabel());
        }
    }

    private Component getTurnDirText() {
        return switch (turnDirection) {
            case 1 -> Component.literal("方向: 左 (LEFT)");
            case 2 -> Component.literal("方向: 右 (RIGHT)");
            default -> Component.literal("方向: 自動 (AUTO)");
        };
    }

    private Component getPreviewText() {
        return isPreview ? Component.literal("プレビュー: §aON") : Component.literal("プレビュー: §7OFF");
    }

    private void sendUpdate(int action) {
        if (startBe != null) {
            var data = startBe.getSplineData();
            data.setRoadMode(roadMode);
            data.setRoadWidth(roadWidth);
            data.setMaxBankAngle(maxBankAngle);
            data.setTurnDirection(turnDirection);
            data.setControlPointDistance(controlPointDist);
            data.setSegments(segments);
            data.setArcFitMode(arcFitMode);
            data.setLoopHeight(loopHeight);
            data.setLoopBoostMultiplier(loopBoostMultiplier);
            data.setPreview(isPreview);
            data.setGenerated(isGenerated);
        }
        RoadConfigPayload payload = new RoadConfigPayload(
                startPos,
                roadMode.getId(),
                roadWidth,
                maxBankAngle,
                turnDirection,
                controlPointDist,
                segments,
                arcFitMode,
                loopHeight,
                loopBoostMultiplier,
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
        graphics.drawCenteredString(this.font, this.title, centerX, 6, 0xFFFFA0);

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
        graphics.drawCenteredString(this.font, Component.literal(linkStatus), centerX, 17, statusColor);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
