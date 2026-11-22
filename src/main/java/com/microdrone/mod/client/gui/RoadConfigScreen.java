package com.microdrone.mod.client.gui;

import com.microdrone.mod.block.road.RoadSplineData;
import com.microdrone.mod.block.road.RoadStartBlockEntity;
import com.microdrone.mod.network.RoadConfigPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 道路・ヘアピンカーブ生成設定画面 (GUI)
 * タブ機能により「形状・コース設定」と「路面タイプ設定」を切り替え可能
 */
public class RoadConfigScreen extends Screen {

    private final BlockPos startPos;
    private RoadStartBlockEntity startBe;

    // タブ状態: 0 = 形状・コース設定, 1 = 路面タイプ設定
    private int activeTab = 0;
    private final List<AbstractWidget> shapeWidgets = new ArrayList<>();
    private final List<AbstractWidget> surfaceWidgets = new ArrayList<>();

    // UIパラメータ
    private RoadSplineData.RoadMode roadMode = RoadSplineData.RoadMode.ROAD;
    private RoadSplineData.RoadSurfaceType surfaceType = RoadSplineData.RoadSurfaceType.ASPHALT;
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

    // ウィジェット参照
    private Button tabShapeButton;
    private Button tabSurfaceButton;

    private Button roadModeButton;
    private Button arcFitButton;
    private Button turnDirButton;
    private Button previewButton;
    private AbstractSliderButton controlPointSlider;
    private AbstractSliderButton loopHeightSlider;
    private AbstractSliderButton loopBoostSlider;

    private Button btnAsphalt;
    private Button btnDirt;
    private Button btnIce;
    private Button btnPuddle;

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

        shapeWidgets.clear();
        surfaceWidgets.clear();

        if (this.minecraft != null && this.minecraft.level != null) {
            if (this.minecraft.level.getBlockEntity(startPos) instanceof RoadStartBlockEntity be) {
                this.startBe = be;
                RoadSplineData data = be.getSplineData();
                this.roadMode = data.getRoadMode();
                this.surfaceType = data.getSurfaceType();
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
        int tabY = 24;
        int buttonWidth = 220;
        int halfWidth = 108;
        int rowHeight = 18;

        // =========================================================================
        // タブ切り替えボタン (上部常時表示)
        // =========================================================================
        tabShapeButton = Button.builder(Component.translatable("gui.microdrone.road.tab.shape"), btn -> {
            switchTab(0);
        }).bounds(centerX - 110, tabY, halfWidth, 16).build();
        this.addRenderableWidget(tabShapeButton);

        tabSurfaceButton = Button.builder(Component.translatable("gui.microdrone.road.tab.surface"), btn -> {
            switchTab(1);
        }).bounds(centerX + 2, tabY, halfWidth, 16).build();
        this.addRenderableWidget(tabSurfaceButton);

        // =========================================================================
        // TAB 0: 形状・コース設定ウィジェット群
        // =========================================================================
        int shapeStartY = 43;

        // Row 0: 生成モードトグル (通常道路 / ジャンプ台 / 360°ループ)
        roadModeButton = Button.builder(getRoadModeText(), btn -> {
            roadMode = roadMode.next();
            btn.setMessage(getRoadModeText());
            updateLoopHeightSliderLabel();
            sendUpdate(0);
        }).bounds(centerX - 110, shapeStartY, buttonWidth, 17).build();
        addShapeWidget(roadModeButton);

        // Row 1: 道幅スライダー (4.0m 〜 9.0m)
        addShapeWidget(new AbstractSliderButton(centerX - 110, shapeStartY + rowHeight, buttonWidth, 17,
                Component.translatable("gui.microdrone.road.width", String.format("%.1f", roadWidth)),
                (roadWidth - RoadSplineData.MIN_ROAD_WIDTH) / (RoadSplineData.MAX_ROAD_WIDTH - RoadSplineData.MIN_ROAD_WIDTH)) {
            @Override
            protected void updateMessage() {
                setMessage(Component.translatable("gui.microdrone.road.width", String.format("%.1f", roadWidth)));
            }

            @Override
            protected void applyValue() {
                roadWidth = RoadSplineData.MIN_ROAD_WIDTH + (float) this.value * (RoadSplineData.MAX_ROAD_WIDTH - RoadSplineData.MIN_ROAD_WIDTH);
                roadWidth = Math.round(roadWidth * 10.0f) / 10.0f;
                sendUpdate(0);
            }
        });

        // Row 2: 最大バンク角スライダー (0° 〜 45°)
        addShapeWidget(new AbstractSliderButton(centerX - 110, shapeStartY + rowHeight * 2, buttonWidth, 17,
                Component.translatable("gui.microdrone.road.max_bank", String.format("%.0f", maxBankAngle)),
                maxBankAngle / RoadSplineData.MAX_MAX_BANK) {
            @Override
            protected void updateMessage() {
                setMessage(Component.translatable("gui.microdrone.road.max_bank", String.format("%.0f", maxBankAngle)));
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
        }).bounds(centerX - 110, shapeStartY + rowHeight * 3, buttonWidth, 17).build();
        addShapeWidget(arcFitButton);

        // Row 4: 曲線分割数スライダー (16 〜 128)
        addShapeWidget(new AbstractSliderButton(centerX - 110, shapeStartY + rowHeight * 4, buttonWidth, 17,
                Component.translatable("gui.microdrone.road.segments", segments),
                (segments - 16.0) / 112.0) {
            @Override
            protected void updateMessage() {
                setMessage(Component.translatable("gui.microdrone.road.segments", segments));
            }

            @Override
            protected void applyValue() {
                int raw = (int) Math.round(16.0 + this.value * 112.0);
                segments = Math.max(16, Math.min(128, ((raw + 4) / 8) * 8));
                sendUpdate(0);
            }
        });

        // Row 5: 曲線半径・制御点距離スライダー (4.0m 〜 40.0m)
        controlPointSlider = new AbstractSliderButton(centerX - 110, shapeStartY + rowHeight * 5, buttonWidth, 17,
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
        addShapeWidget(controlPointSlider);

        // Row 6: ループ高さ ＆ 加速倍率スライダー
        loopHeightSlider = new AbstractSliderButton(centerX - 110, shapeStartY + rowHeight * 6, halfWidth, 17,
                getLoopHeightLabel(),
                (loopHeight - RoadSplineData.MIN_LOOP_HEIGHT) / (RoadSplineData.MAX_LOOP_HEIGHT - RoadSplineData.MIN_LOOP_HEIGHT)) {
            @Override
            protected void updateMessage() {
                setMessage(getLoopHeightLabel());
            }

            @Override
            protected void applyValue() {
                float raw = RoadSplineData.MIN_LOOP_HEIGHT + (float) this.value * (RoadSplineData.MAX_LOOP_HEIGHT - RoadSplineData.MIN_LOOP_HEIGHT);
                loopHeight = Math.round(raw * 2.0f) / 2.0f;
                sendUpdate(0);
            }
        };
        addShapeWidget(loopHeightSlider);

        loopBoostSlider = new AbstractSliderButton(centerX + 2, shapeStartY + rowHeight * 6, halfWidth, 17,
                getLoopBoostLabel(),
                (loopBoostMultiplier - RoadSplineData.MIN_LOOP_BOOST) / (RoadSplineData.MAX_LOOP_BOOST - RoadSplineData.MIN_LOOP_BOOST)) {
            @Override
            protected void updateMessage() {
                setMessage(getLoopBoostLabel());
            }

            @Override
            protected void applyValue() {
                float raw = RoadSplineData.MIN_LOOP_BOOST + (float) this.value * (RoadSplineData.MAX_LOOP_BOOST - RoadSplineData.MIN_LOOP_BOOST);
                loopBoostMultiplier = Math.round(raw * 10.0f) / 10.0f;
                sendUpdate(0);
            }
        };
        addShapeWidget(loopBoostSlider);

        // Row 7: 曲がる方向切り替え ＆ プレビュートグル
        turnDirButton = Button.builder(getTurnDirText(), btn -> {
            turnDirection = (turnDirection + 1) % 3;
            btn.setMessage(getTurnDirText());
            sendUpdate(0);
        }).bounds(centerX - 110, shapeStartY + rowHeight * 7, halfWidth, 17).build();
        addShapeWidget(turnDirButton);

        previewButton = Button.builder(getPreviewText(), btn -> {
            isPreview = !isPreview;
            btn.setMessage(getPreviewText());
            sendUpdate(0);
        }).bounds(centerX + 2, shapeStartY + rowHeight * 7, halfWidth, 17).build();
        addShapeWidget(previewButton);

        // Row 8: 終点ブロック再探索ボタン
        addShapeWidget(Button.builder(Component.translatable("gui.microdrone.road.rescan"), btn -> {
            sendUpdate(1);
        }).bounds(centerX - 110, shapeStartY + rowHeight * 8, buttonWidth, 17).build());

        // =========================================================================
        // TAB 1: 路面タイプ設定ウィジェット群
        // =========================================================================
        int surfStartY = 45;
        int surfBtnHeight = 20;
        int surfSpacing = 24;

        btnAsphalt = Button.builder(getSurfaceButtonText(RoadSplineData.RoadSurfaceType.ASPHALT), btn -> {
            surfaceType = RoadSplineData.RoadSurfaceType.ASPHALT;
            updateSurfaceButtons();
            sendUpdate(0);
        }).bounds(centerX - 110, surfStartY, buttonWidth, surfBtnHeight).build();
        addSurfaceWidget(btnAsphalt);

        btnDirt = Button.builder(getSurfaceButtonText(RoadSplineData.RoadSurfaceType.DIRT), btn -> {
            surfaceType = RoadSplineData.RoadSurfaceType.DIRT;
            updateSurfaceButtons();
            sendUpdate(0);
        }).bounds(centerX - 110, surfStartY + surfSpacing, buttonWidth, surfBtnHeight).build();
        addSurfaceWidget(btnDirt);

        btnIce = Button.builder(getSurfaceButtonText(RoadSplineData.RoadSurfaceType.ICE), btn -> {
            surfaceType = RoadSplineData.RoadSurfaceType.ICE;
            updateSurfaceButtons();
            sendUpdate(0);
        }).bounds(centerX - 110, surfStartY + surfSpacing * 2, buttonWidth, surfBtnHeight).build();
        addSurfaceWidget(btnIce);

        btnPuddle = Button.builder(getSurfaceButtonText(RoadSplineData.RoadSurfaceType.PUDDLE), btn -> {
            surfaceType = RoadSplineData.RoadSurfaceType.PUDDLE;
            updateSurfaceButtons();
            sendUpdate(0);
        }).bounds(centerX - 110, surfStartY + surfSpacing * 3, buttonWidth, surfBtnHeight).build();
        addSurfaceWidget(btnPuddle);

        // =========================================================================
        // 共通アクションボタン (下部常時表示)
        // =========================================================================
        int bottomY = shapeStartY + rowHeight * 9 + 2;

        // Row 9: 確定生成 (左) & 削除 (右)
        this.addRenderableWidget(Button.builder(Component.translatable("gui.microdrone.road.confirm_generate"), btn -> {
            isGenerated = true;
            isPreview = false;
            sendUpdate(2);
            this.onClose();
        }).bounds(centerX - 110, bottomY, halfWidth, 17).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.microdrone.road.delete"), btn -> {
            isGenerated = false;
            isPreview = true;
            sendUpdate(3);
            this.onClose();
        }).bounds(centerX + 2, bottomY, halfWidth, 17).build());

        // Row 10: 閉じるボタン
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> {
            this.onClose();
        }).bounds(centerX - 110, bottomY + rowHeight + 1, buttonWidth, 17).build());

        // 初期タブの適用
        switchTab(activeTab);
    }

    private void addShapeWidget(AbstractWidget widget) {
        this.addRenderableWidget(widget);
        this.shapeWidgets.add(widget);
    }

    private void addSurfaceWidget(AbstractWidget widget) {
        this.addRenderableWidget(widget);
        this.surfaceWidgets.add(widget);
    }

    private void switchTab(int tab) {
        this.activeTab = tab;
        for (AbstractWidget w : shapeWidgets) {
            w.visible = (activeTab == 0);
            w.active = (activeTab == 0);
        }
        for (AbstractWidget w : surfaceWidgets) {
            w.visible = (activeTab == 1);
            w.active = (activeTab == 1);
        }
        updateTabButtons();
        if (activeTab == 1) {
            updateSurfaceButtons();
        }
    }

    private void updateTabButtons() {
        if (tabShapeButton != null) {
            tabShapeButton.setMessage(activeTab == 0
                    ? Component.literal("§a▶ §l").append(Component.translatable("gui.microdrone.road.tab.shape"))
                    : Component.translatable("gui.microdrone.road.tab.shape"));
        }
        if (tabSurfaceButton != null) {
            tabSurfaceButton.setMessage(activeTab == 1
                    ? Component.literal("§a▶ §l").append(Component.translatable("gui.microdrone.road.tab.surface"))
                    : Component.translatable("gui.microdrone.road.tab.surface"));
        }
    }

    private Component getSurfaceButtonText(RoadSplineData.RoadSurfaceType type) {
        String key = "gui.microdrone.road.surface." + type.name().toLowerCase();
        boolean isSelected = (this.surfaceType == type);
        String prefix = isSelected ? "§e✔ §6§l" : "§f";
        return Component.literal(prefix).append(Component.translatable(key));
    }

    private void updateSurfaceButtons() {
        if (btnAsphalt != null) btnAsphalt.setMessage(getSurfaceButtonText(RoadSplineData.RoadSurfaceType.ASPHALT));
        if (btnDirt != null) btnDirt.setMessage(getSurfaceButtonText(RoadSplineData.RoadSurfaceType.DIRT));
        if (btnIce != null) btnIce.setMessage(getSurfaceButtonText(RoadSplineData.RoadSurfaceType.ICE));
        if (btnPuddle != null) btnPuddle.setMessage(getSurfaceButtonText(RoadSplineData.RoadSurfaceType.PUDDLE));
    }

    private Component getRoadModeText() {
        return switch (roadMode) {
            case JUMP_RAMP -> Component.translatable("gui.microdrone.road.mode.jump_ramp");
            case LOOP_360 -> Component.translatable("gui.microdrone.road.mode.loop_360");
            default -> Component.translatable("gui.microdrone.road.mode.road");
        };
    }

    private Component getArcFitText() {
        return arcFitMode
                ? Component.translatable("gui.microdrone.road.arc_fit")
                : Component.translatable("gui.microdrone.road.bezier");
    }

    private Component getControlPointLabel() {
        String suffix = arcFitMode ? Component.translatable("gui.microdrone.road.manual_suffix").getString() : "";
        return Component.translatable("gui.microdrone.road.control_point", String.format("%.1f", controlPointDist), suffix);
    }

    private void updateControlPointSliderLabel() {
        if (controlPointSlider != null) {
            controlPointSlider.setMessage(getControlPointLabel());
        }
    }

    private Component getLoopHeightLabel() {
        String prefix = (roadMode == RoadSplineData.RoadMode.LOOP_360) ? "" : "§7";
        return Component.translatable("gui.microdrone.road.loop_height", prefix, String.format("%.1f", loopHeight));
    }

    private Component getLoopBoostLabel() {
        String prefix = (roadMode == RoadSplineData.RoadMode.LOOP_360) ? "" : "§7";
        return Component.translatable("gui.microdrone.road.loop_boost", prefix, String.format("%.1f", loopBoostMultiplier));
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
            case 1 -> Component.translatable("gui.microdrone.road.turn_dir.left");
            case 2 -> Component.translatable("gui.microdrone.road.turn_dir.right");
            default -> Component.translatable("gui.microdrone.road.turn_dir.auto");
        };
    }

    private Component getPreviewText() {
        return isPreview ? Component.translatable("gui.microdrone.road.preview_on") : Component.translatable("gui.microdrone.road.preview_off");
    }

    private void sendUpdate(int action) {
        if (startBe != null) {
            var data = startBe.getSplineData();
            data.setRoadMode(roadMode);
            data.setSurfaceType(surfaceType);
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
                surfaceType.getId(),
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
        graphics.drawCenteredString(this.font, this.title, centerX, 4, 0xFFFFA0);

        // リンク状態の表示
        Component linkStatus;
        int statusColor;
        if (startBe != null && startBe.getLinkedEndPos() != null) {
            BlockPos endPos = startBe.getLinkedEndPos();
            linkStatus = Component.translatable("gui.microdrone.road.status_linked", endPos.getX(), endPos.getY(), endPos.getZ());
            statusColor = 0x55FF55; // 緑
        } else {
            linkStatus = Component.translatable("gui.microdrone.road.status_unlinked");
            statusColor = 0xFF5555; // 赤
        }
        graphics.drawCenteredString(this.font, linkStatus, centerX, 14, statusColor);

        // 路面設定タブの場合、選択中の路面タイプの説明カードを描画
        if (activeTab == 1) {
            renderSurfaceDescription(graphics, centerX);
        }
    }

    private void renderSurfaceDescription(GuiGraphics graphics, int centerX) {
        int descX = centerX - 110;
        int descY = 148;
        int descWidth = 220;
        int descHeight = 54;

        // 背景ボックス (半透明ダークグレー + 枠線)
        graphics.fill(descX, descY, descX + descWidth, descY + descHeight, 0x88000000);
        graphics.renderOutline(descX, descY, descWidth, descHeight, 0xFF666666);

        String titleKey = "gui.microdrone.road.surface." + surfaceType.name().toLowerCase() + ".title";
        String line1Key = "gui.microdrone.road.surface." + surfaceType.name().toLowerCase() + ".desc1";
        String line2Key = "gui.microdrone.road.surface." + surfaceType.name().toLowerCase() + ".desc2";
        String tireKey = "gui.microdrone.road.surface." + surfaceType.name().toLowerCase() + ".tire";

        graphics.drawString(this.font, Component.translatable(titleKey), descX + 6, descY + 5, 0xFFFF55);
        graphics.drawString(this.font, Component.translatable(line1Key), descX + 6, descY + 18, 0xDDDDDD);
        graphics.drawString(this.font, Component.translatable(line2Key), descX + 6, descY + 29, 0xAAAAAA);
        graphics.drawString(this.font, Component.translatable(tireKey), descX + 6, descY + 41, 0x55FFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
