package com.microdrone.mod.client.gui;

import com.microdrone.mod.entity.RcCarEntity;
import com.microdrone.mod.network.RcCarTuningPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * RCカー車重・走行物理モード・カウンターステア角・滑り始め角チューニングGUI画面
 * プロポを持った状態でスニーク右クリック、またはRCカーをスニーク右クリックで起動
 */
public class RcCarTuningScreen extends Screen {

    private final RcCarEntity car;
    private WeightSlider weightSlider;
    private CounterAngleSlider counterAngleSlider;
    private DriftStartAngleSlider driftStartAngleSlider;
    private Button modeButton;
    private Button debugButton;
    private boolean realMode;

    public RcCarTuningScreen(RcCarEntity car) {
        super(Component.literal("RCカー セッティング"));
        this.car = car;
        this.realMode = car.isRealMode();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 0. 走行モード切替ボタン (RACE ⇔ REAL)
        this.modeButton = Button.builder(getModeButtonText(), btn -> toggleDrivingMode())
                .bounds(centerX - 120, centerY - 98, 240, 20).build();
        this.addRenderableWidget(this.modeButton);

        // 1. 最大カウンターステア角スライダー (35° 〜 65°)
        float currentCounter = this.car.getCounterMaxAngle();
        double counterProgress = Math.max(0.0, Math.min(1.0, (currentCounter - 35.0) / 30.0));
        this.counterAngleSlider = new CounterAngleSlider(centerX - 120, centerY - 62, 240, 20, counterProgress);
        this.addRenderableWidget(this.counterAngleSlider);

        // 2. 滑り始め角度スライダー (10° 〜 35°)
        float currentDriftStart = this.car.getDriftStartAngle();
        double driftStartProgress = Math.max(0.0, Math.min(1.0, (currentDriftStart - 10.0) / 25.0));
        this.driftStartAngleSlider = new DriftStartAngleSlider(centerX - 120, centerY - 38, 240, 20, driftStartProgress);
        this.addRenderableWidget(this.driftStartAngleSlider);

        // 3. 車重スライダー (0.50kg 〜 2.50kg)
        float currentWeight = this.car.getVehicleMass();
        double initialProgress = Math.max(0.0, Math.min(1.0, (currentWeight - 0.50) / 2.00));
        this.weightSlider = new WeightSlider(centerX - 120, centerY - 14, 240, 20, initialProgress);
        this.addRenderableWidget(this.weightSlider);

        // 4. プリセットボタン (1行目: 0.80kg / 1.10kg)
        this.addRenderableWidget(Button.builder(Component.literal("軽量 (0.80kg)"), btn -> setPreset(0.80f))
                .bounds(centerX - 120, centerY + 24, 115, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("標準 (1.10kg)"), btn -> setPreset(1.10f))
                .bounds(centerX + 5, centerY + 24, 115, 20).build());

        // 5. プリセットボタン (2行目: 1.60kg / 2.20kg)
        this.addRenderableWidget(Button.builder(Component.literal("重量級 (1.60kg)"), btn -> setPreset(1.60f))
                .bounds(centerX - 120, centerY + 48, 115, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("超重量 (2.20kg)"), btn -> setPreset(2.20f))
                .bounds(centerX + 5, centerY + 48, 115, 20).build());

        // 6. 完了ボタン & デバッグHUD切替
        this.addRenderableWidget(Button.builder(Component.literal("設定完了"), btn -> onClose())
                .bounds(centerX - 120, centerY + 76, 115, 20).build());

        this.debugButton = Button.builder(getDebugButtonText(), btn -> toggleDebugHud())
                .bounds(centerX + 5, centerY + 76, 115, 20).build();
        this.addRenderableWidget(this.debugButton);
    }

    private Component getDebugButtonText() {
        return RcCarHudOverlay.showPhysicsDebug
                ? Component.literal("数値HUD: §bON")
                : Component.literal("数値HUD: §7OFF");
    }

    private void toggleDebugHud() {
        RcCarHudOverlay.showPhysicsDebug = !RcCarHudOverlay.showPhysicsDebug;
        this.debugButton.setMessage(getDebugButtonText());
    }

    private Component getModeButtonText() {
        return this.realMode
                ? Component.literal("走行モード: §b§lREAL (可変グリップ物理)")
                : Component.literal("走行モード: §a§lRACE (標準ドリフト補助)");
    }

    private void toggleDrivingMode() {
        this.realMode = !this.realMode;
        this.modeButton.setMessage(getModeButtonText());
        this.car.setDrivingMode(this.realMode);
        sendSyncPacket();
    }

    private void setPreset(float weight) {
        double progress = Math.max(0.0, Math.min(1.0, (weight - 0.50) / 2.00));
        this.weightSlider.setValueFromPreset(progress);
    }

    private void sendSyncPacket() {
        float w = this.weightSlider != null ? this.weightSlider.getWeightKg() : this.car.getVehicleMass();
        float cAngle = this.counterAngleSlider != null ? this.counterAngleSlider.getAngleDeg() : this.car.getCounterMaxAngle();
        float dAngle = this.driftStartAngleSlider != null ? this.driftStartAngleSlider.getAngleDeg() : this.car.getDriftStartAngle();
        PacketDistributor.sendToServer(new RcCarTuningPayload(this.car.getId(), w, this.realMode, cAngle, dAngle));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // タイトル
        graphics.drawCenteredString(this.font, "§6§l=== RCカー 走行モード＆物理セッティング ===", centerX, centerY - 118, 0xFFFFFF);

        // モード説明
        String modeDesc = this.realMode
                ? "§b[REAL] §7前後/横速度分離・可変グリップ・荷重移動・カウンター制御"
                : "§a[RACE] §7Spaceホップドリフト・扱いやすい標準アシスト";
        graphics.drawCenteredString(this.font, modeDesc, centerX, centerY - 74, 0xDDDDDD);

        // 現在車重設定の解説
        float w = this.weightSlider.getWeightKg();
        String desc;
        if (w < 0.90f) {
            desc = "§b【軽量・トリック仕様】 §7ジャンプが高くフワッと遠くまで飛びます";
        } else if (w <= 1.35f) {
            desc = "§a【標準・実車1/10バギー】 §7自然な放物線と手応えのある推奨バランス";
        } else if (w <= 1.85f) {
            desc = "§6【重量級・高速レーン】 §7低空を鋭く突き抜け、ドスッと素早く着地";
        } else {
            desc = "§c【超重量級・地面吸着】 §7超重厚。地面に張り付き段差やジャンプを極小化";
        }
        graphics.drawCenteredString(this.font, desc, centerX, centerY + 10, 0xEEEEEE);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * カウンターステア最大切れ角スライダー (35° 〜 65°)
     */
    private class CounterAngleSlider extends AbstractSliderButton {

        public CounterAngleSlider(int x, int y, int width, int height, double value) {
            super(x, y, width, height, Component.empty(), value);
            updateMessage();
        }

        public float getAngleDeg() {
            return (float) Math.round(35.0 + this.value * 30.0);
        }

        @Override
        protected void updateMessage() {
            float deg = getAngleDeg();
            setMessage(Component.literal(String.format("カウンター最大角 (REAL): §b%.0f°", deg)));
        }

        @Override
        protected void applyValue() {
            float deg = getAngleDeg();
            car.setCounterMaxAngle(deg);
            sendSyncPacket();
        }
    }

    /**
     * 横滑り開始（滑り始め）ステア角スライダー (10° 〜 35°)
     */
    private class DriftStartAngleSlider extends AbstractSliderButton {

        public DriftStartAngleSlider(int x, int y, int width, int height, double value) {
            super(x, y, width, height, Component.empty(), value);
            updateMessage();
        }

        public float getAngleDeg() {
            return (float) Math.round(10.0 + this.value * 25.0);
        }

        @Override
        protected void updateMessage() {
            float deg = getAngleDeg();
            setMessage(Component.literal(String.format("滑り始め角度 (REAL): §e%.0f°", deg)));
        }

        @Override
        protected void applyValue() {
            float deg = getAngleDeg();
            car.setDriftStartAngle(deg);
            sendSyncPacket();
        }
    }

    /**
     * 車重スライダーウィジェット
     */
    private class WeightSlider extends AbstractSliderButton {

        public WeightSlider(int x, int y, int width, int height, double value) {
            super(x, y, width, height, Component.empty(), value);
            updateMessage();
        }

        public float getWeightKg() {
            return (float) Math.round((0.50 + this.value * 2.00) * 100.0) / 100.0f;
        }

        public void setValueFromPreset(double progress) {
            this.value = progress;
            updateMessage();
            applyValue();
        }

        @Override
        protected void updateMessage() {
            float w = getWeightKg();
            setMessage(Component.literal(String.format("車重設定: §e%.2f kg", w)));
        }

        @Override
        protected void applyValue() {
            float w = getWeightKg();
            car.setCustomWeight(w);
            sendSyncPacket();
        }
    }
}
