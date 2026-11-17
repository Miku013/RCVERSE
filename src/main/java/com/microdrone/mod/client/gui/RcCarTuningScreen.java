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
        super(Component.translatable("gui.microdrone.tuning.title"));
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
        this.addRenderableWidget(Button.builder(Component.translatable("gui.microdrone.tuning.preset_light"), btn -> setPreset(0.80f))
                .bounds(centerX - 120, centerY + 24, 115, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.microdrone.tuning.preset_std"), btn -> setPreset(1.10f))
                .bounds(centerX + 5, centerY + 24, 115, 20).build());

        // 5. プリセットボタン (2行目: 1.60kg / 2.20kg)
        this.addRenderableWidget(Button.builder(Component.translatable("gui.microdrone.tuning.preset_heavy"), btn -> setPreset(1.60f))
                .bounds(centerX - 120, centerY + 48, 115, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.microdrone.tuning.preset_ultra"), btn -> setPreset(2.20f))
                .bounds(centerX + 5, centerY + 48, 115, 20).build());

        // 6. 完了ボタン & デバッグHUD切替
        this.addRenderableWidget(Button.builder(Component.translatable("gui.microdrone.tuning.done"), btn -> onClose())
                .bounds(centerX - 120, centerY + 76, 115, 20).build());

        this.debugButton = Button.builder(getDebugButtonText(), btn -> toggleDebugHud())
                .bounds(centerX + 5, centerY + 76, 115, 20).build();
        this.addRenderableWidget(this.debugButton);
    }

    private Component getDebugButtonText() {
        return RcCarHudOverlay.showPhysicsDebug
                ? Component.translatable("gui.microdrone.tuning.debug_hud_on")
                : Component.translatable("gui.microdrone.tuning.debug_hud_off");
    }

    private void toggleDebugHud() {
        RcCarHudOverlay.showPhysicsDebug = !RcCarHudOverlay.showPhysicsDebug;
        this.debugButton.setMessage(getDebugButtonText());
    }

    private Component getModeButtonText() {
        return this.realMode
                ? Component.translatable("gui.microdrone.tuning.mode_real")
                : Component.translatable("gui.microdrone.tuning.mode_race");
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
        graphics.drawCenteredString(this.font, Component.translatable("gui.microdrone.tuning.header").getString(), centerX, centerY - 118, 0xFFFFFF);

        // モード説明
        String modeDesc = this.realMode
                ? Component.translatable("gui.microdrone.tuning.mode_desc_real").getString()
                : Component.translatable("gui.microdrone.tuning.mode_desc_race").getString();
        graphics.drawCenteredString(this.font, modeDesc, centerX, centerY - 74, 0xDDDDDD);

        // 現在車重設定の解説
        float w = this.weightSlider.getWeightKg();
        String desc;
        if (w < 0.90f) {
            desc = Component.translatable("gui.microdrone.tuning.desc_light").getString();
        } else if (w <= 1.35f) {
            desc = Component.translatable("gui.microdrone.tuning.desc_std").getString();
        } else if (w <= 1.85f) {
            desc = Component.translatable("gui.microdrone.tuning.desc_heavy").getString();
        } else {
            desc = Component.translatable("gui.microdrone.tuning.desc_ultra").getString();
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
            setMessage(Component.translatable("gui.microdrone.tuning.counter_angle", String.format("%.0f", deg)));
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
            setMessage(Component.translatable("gui.microdrone.tuning.drift_start_angle", String.format("%.0f", deg)));
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
            setMessage(Component.translatable("gui.microdrone.tuning.weight", String.format("%.2f", w)));
        }

        @Override
        protected void applyValue() {
            float w = getWeightKg();
            car.setCustomWeight(w);
            sendSyncPacket();
        }
    }
}
