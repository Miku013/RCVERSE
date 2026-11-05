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
 * RCカー車重＆物理チューニングGUI画面 (アプローチ3)
 * プロポを持った状態でスニーク右クリック、またはRCカーをスニーク右クリックで起動
 */
public class RcCarTuningScreen extends Screen {

    private final RcCarEntity car;
    private WeightSlider slider;

    public RcCarTuningScreen(RcCarEntity car) {
        super(Component.literal("RCカー セッティング"));
        this.car = car;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        float currentWeight = this.car.getVehicleMass();
        // 0.50kg ~ 2.50kg -> 0.0 ~ 1.0
        double initialProgress = Math.max(0.0, Math.min(1.0, (currentWeight - 0.50) / 2.00));

        // 1. 車重スライダー
        this.slider = new WeightSlider(centerX - 120, centerY - 25, 240, 20, initialProgress);
        this.addRenderableWidget(this.slider);

        // 2. プリセットボタン (1行目: 0.80kg / 1.10kg)
        this.addRenderableWidget(Button.builder(Component.literal("軽量 (0.80kg)"), btn -> setPreset(0.80f))
                .bounds(centerX - 120, centerY + 8, 115, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("標準 (1.10kg)"), btn -> setPreset(1.10f))
                .bounds(centerX + 5, centerY + 8, 115, 20).build());

        // 3. プリセットボタン (2行目: 1.60kg / 2.20kg)
        this.addRenderableWidget(Button.builder(Component.literal("重量級 (1.60kg)"), btn -> setPreset(1.60f))
                .bounds(centerX - 120, centerY + 32, 115, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("超重量 (2.20kg)"), btn -> setPreset(2.20f))
                .bounds(centerX + 5, centerY + 32, 115, 20).build());

        // 4. 完了ボタン
        this.addRenderableWidget(Button.builder(Component.literal("設定完了"), btn -> onClose())
                .bounds(centerX - 60, centerY + 65, 120, 20).build());
    }

    private void setPreset(float weight) {
        double progress = Math.max(0.0, Math.min(1.0, (weight - 0.50) / 2.00));
        this.slider.setValueFromPreset(progress);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // タイトル
        graphics.drawCenteredString(this.font, "§6§l=== RCカー 車重＆ジャンプ物理セッティング ===", centerX, centerY - 75, 0xFFFFFF);

        // 現在設定の解説
        float w = this.slider.getWeightKg();
        String desc;
        String airTime;
        if (w < 0.90f) {
            desc = "§b【軽量・トリック仕様】 §7ジャンプが高くフワッと遠くまで飛びます";
            airTime = "滞空時間目安: §e約 0.70秒 §7(軽快・ロングジャンプ)";
        } else if (w <= 1.35f) {
            desc = "§a【標準・実車1/10バギー】 §7自然な放物線と手応えのある推奨バランス";
            airTime = "滞空時間目安: §e約 0.55秒 §7(推奨・リアルRC)";
        } else if (w <= 1.85f) {
            desc = "§6【重量級・高速レーン】 §7低空を鋭く突き抜け、ドスッと素早く着地";
            airTime = "滞空時間目安: §e約 0.42秒 §7(低空高速・レーザー弾道)";
        } else {
            desc = "§c【超重量級・地面吸着】 §7超重厚。地面に張り付き段差やジャンプを極小化";
            airTime = "滞空時間目安: §e約 0.32秒 §7(超重厚・即座着地)";
        }

        graphics.drawCenteredString(this.font, desc, centerX, centerY - 54, 0xEEEEEE);
        graphics.drawCenteredString(this.font, airTime, centerX, centerY - 42, 0xCCCCCC);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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
            // クライアント側に即時反映
            car.setCustomWeight(w);
            // サーバーへ同期パケット送信
            PacketDistributor.sendToServer(new RcCarTuningPayload(car.getId(), w));
        }
    }
}
